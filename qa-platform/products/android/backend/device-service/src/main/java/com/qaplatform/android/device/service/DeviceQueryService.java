package com.qaplatform.android.device.service;

import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.android.device.api.dto.DeviceDtos;
import com.qaplatform.android.device.domain.Device;
import com.qaplatform.android.device.domain.DeviceProjectAccessRepository;
import com.qaplatform.android.device.domain.DeviceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DeviceQueryService {

    private final DeviceRepository repo;
    private final DeviceProjectAccessRepository access;
    private final HeartbeatService heartbeat;

    public DeviceQueryService(DeviceRepository repo, DeviceProjectAccessRepository access, HeartbeatService heartbeat) {
        this.repo = repo;
        this.access = access;
        this.heartbeat = heartbeat;
    }

    /**
     * List devices visible to the caller within the active project context.
     *
     * <p>Visibility rules:
     * <ul>
     *   <li>Device must belong to the {@code companyId} the caller is a member of.</li>
     *   <li>If {@code projectId} is given AND the device is {@code restricted=true},
     *       the device must appear in {@code device_project_access} for that project.</li>
     *   <li>If {@code projectId} is null, only company-membership matters — used by
     *       the company-wide Devices admin page.</li>
     * </ul>
     */
    public List<DeviceDtos.DeviceView> list(JwtPrincipal caller, Long companyId, Long projectId) {
        if (caller == null || caller.userId() == null) throw ApiException.unauthorized("missing identity");
        if (companyId == null) throw ApiException.badRequest("missing X-Company-Id header");
        if (!caller.isMemberOf(companyId)) {
            throw ApiException.forbidden("not a member of this company");
        }

        List<Device> devices = repo.findAllByCompanyId(companyId);
        if (projectId != null) {
            Set<Long> visibleRestricted = access.findAllByProjectId(projectId).stream()
                    .map(a -> a.getDeviceId()).collect(Collectors.toSet());
            devices = devices.stream()
                    .filter(d -> !d.isRestricted() || visibleRestricted.contains(d.getId()))
                    .toList();
        }
        return devices.stream().map(this::toView).toList();
    }

    public DeviceDtos.DeviceView getForCaller(JwtPrincipal caller, Long companyId, long deviceId) {
        if (caller == null || caller.userId() == null) throw ApiException.unauthorized("missing identity");
        Device d = repo.findById(deviceId).orElseThrow(() -> ApiException.notFound("device"));
        if (companyId != null && !d.getCompanyId().equals(companyId)) {
            throw ApiException.forbidden("device not in active company");
        }
        if (!caller.isMemberOf(d.getCompanyId())) {
            throw ApiException.forbidden("not a member of this device's company");
        }
        return toView(d);
    }

    /**
     * Cross-tenant device dump for the platform-admin devices page. Includes
     * archived-company devices too — the admin page needs them to spot orphans;
     * regular tenants never see this endpoint so there's no scoping issue.
     */
    public List<DeviceDtos.AdminDeviceView> listAllForAdmin(JwtPrincipal caller) {
        if (caller == null || !caller.platformAdmin()) {
            throw ApiException.forbidden("platform admin only");
        }
        List<Device> all = repo.findAll();
        // One bulk lookup of project-access rows so each row's count is O(1).
        // Devices without any rows contribute zero — Collectors.counting() handles that.
        return all.stream().map(d -> {
            long count = access.countByDeviceId(d.getId());
            return toAdminView(d, (int) count);
        }).toList();
    }

    private DeviceDtos.AdminDeviceView toAdminView(Device d, int accessProjectCount) {
        Long sessionId = heartbeat.currentSessionId(d.getId());
        DeviceDtos.DeviceStatus status;
        if (sessionId != null) status = DeviceDtos.DeviceStatus.IN_USE;
        else if (heartbeat.isOnline(d.getId())) status = DeviceDtos.DeviceStatus.ONLINE;
        else status = DeviceDtos.DeviceStatus.OFFLINE;

        return new DeviceDtos.AdminDeviceView(
                d.getId(), d.getCompanyId(), d.getSerial(),
                d.getManufacturer(), d.getModel(),
                d.getAndroidVersion(), d.getScreenWidth(), d.getScreenHeight(), d.getAgentVersion(),
                d.getEnrolledAt(), d.getLastSeenAt(), status, sessionId,
                d.isRestricted(), accessProjectCount);
    }

    private DeviceDtos.DeviceView toView(Device d) {
        Long sessionId = heartbeat.currentSessionId(d.getId());
        DeviceDtos.DeviceStatus status;
        if (sessionId != null) status = DeviceDtos.DeviceStatus.IN_USE;
        else if (heartbeat.isOnline(d.getId())) status = DeviceDtos.DeviceStatus.ONLINE;
        else status = DeviceDtos.DeviceStatus.OFFLINE;

        return new DeviceDtos.DeviceView(
                d.getId(), d.getSerial(), d.getManufacturer(), d.getModel(),
                d.getAndroidVersion(), d.getScreenWidth(), d.getScreenHeight(), d.getAgentVersion(),
                d.getEnrolledAt(), d.getLastSeenAt(), status, sessionId);
    }
}
