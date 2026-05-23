package com.qaplatform.android.device.service;

import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.android.device.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages per-device project visibility. The default state is
 * {@code restricted = false} — every project in the device's company can see and
 * reserve it. When the admin flips a device to restricted, only projects listed
 * in {@code device_project_access} retain visibility.
 */
@Service
public class DeviceAccessService {

    public record AccessView(long deviceId, boolean restricted, List<Long> projectIds) {}
    public record UpdateRequest(boolean restricted, List<Long> projectIds) {}

    private final DeviceRepository devices;
    private final DeviceProjectAccessRepository access;

    public DeviceAccessService(DeviceRepository devices, DeviceProjectAccessRepository access) {
        this.devices = devices;
        this.access = access;
    }

    /** Read current access policy for a device. Company members may view. */
    @Transactional(readOnly = true)
    public AccessView get(JwtPrincipal caller, long deviceId) {
        Device d = ensureViewable(caller, deviceId);
        List<Long> projectIds = access.findAllByDeviceId(d.getId()).stream()
                .map(DeviceProjectAccess::getProjectId).toList();
        return new AccessView(d.getId(), d.isRestricted(), projectIds);
    }

    /**
     * Replace the access policy. OWNER/QA_MANAGER required. When
     * {@code restricted=false}, the whitelist is irrelevant and gets cleared so
     * a future "restricted=true" toggle doesn't accidentally inherit stale grants.
     */
    @Transactional
    public AccessView update(JwtPrincipal caller, long deviceId, UpdateRequest req) {
        Device d = ensureManageable(caller, deviceId);
        d.setRestricted(req.restricted());
        devices.save(d);

        if (!req.restricted()) {
            access.deleteAllByDeviceId(d.getId());
            return new AccessView(d.getId(), false, List.of());
        }

        Set<Long> desired = new HashSet<>(req.projectIds() == null ? List.of() : req.projectIds());
        Set<Long> current = access.findAllByDeviceId(d.getId()).stream()
                .map(DeviceProjectAccess::getProjectId).collect(Collectors.toSet());

        // Add new grants
        for (Long pid : desired) {
            if (!current.contains(pid)) {
                access.save(new DeviceProjectAccess(d.getId(), pid, caller.userId()));
            }
        }
        // Remove revoked grants
        for (Long pid : current) {
            if (!desired.contains(pid)) {
                access.deleteByDeviceIdAndProjectId(d.getId(), pid);
            }
        }
        return new AccessView(d.getId(), true, desired.stream().sorted().toList());
    }

    /* ──────────────────────  helpers  ─────────────────────── */

    private Device ensureViewable(JwtPrincipal caller, long deviceId) {
        if (caller == null || caller.userId() == null) throw ApiException.unauthorized("missing identity");
        Device d = devices.findById(deviceId).orElseThrow(() -> ApiException.notFound("device"));
        if (!caller.isMemberOf(d.getCompanyId())) {
            throw ApiException.forbidden("not a member of this device's company");
        }
        return d;
    }

    /**
     * Mutating device-access requires company OWNER (or platform admin). Devices
     * are a company-wide resource; QA_MANAGERs operate inside one project and
     * shouldn't unilaterally re-share devices across the rest of the company.
     */
    private Device ensureManageable(JwtPrincipal caller, long deviceId) {
        Device d = ensureViewable(caller, deviceId);
        if (!caller.isOwnerOf(d.getCompanyId())) {
            throw ApiException.forbidden("OWNER role required");
        }
        return d;
    }
}
