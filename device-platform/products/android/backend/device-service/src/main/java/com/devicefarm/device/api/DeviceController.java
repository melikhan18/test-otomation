package com.devicefarm.device.api;

import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
import com.devicefarm.common.tenancy.TenancyHeaders;
import com.devicefarm.device.api.dto.DeviceDtos;
import com.devicefarm.device.domain.Device;
import com.devicefarm.device.domain.DeviceRepository;
import com.devicefarm.device.service.AdminDeviceService;
import com.devicefarm.device.service.DeviceAccessService;
import com.devicefarm.device.service.DeviceQueryService;
import com.devicefarm.device.service.EnrollmentService;
import com.devicefarm.device.service.HeartbeatService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceQueryService query;
    private final DeviceAccessService access;
    private final AdminDeviceService admin;
    private final EnrollmentService enrollment;
    private final HeartbeatService heartbeat;
    private final DeviceRepository devices;

    public DeviceController(DeviceQueryService query, DeviceAccessService access,
                            AdminDeviceService admin,
                            EnrollmentService enrollment,
                            HeartbeatService heartbeat, DeviceRepository devices) {
        this.query = query;
        this.access = access;
        this.admin = admin;
        this.enrollment = enrollment;
        this.heartbeat = heartbeat;
        this.devices = devices;
    }

    /**
     * Devices visible to the caller. Header {@code X-Company-Id} is required; if
     * {@code X-Project-Id} is also present we additionally filter out restricted
     * devices that aren't whitelisted to that project.
     */
    @GetMapping
    public List<DeviceDtos.DeviceView> list(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.COMPANY_ID, required = false) Long companyId,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID, required = false) Long projectId) {
        return query.list(caller, companyId, projectId);
    }

    @GetMapping("/{id}")
    public DeviceDtos.DeviceView get(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.COMPANY_ID, required = false) Long companyId,
            @PathVariable long id) {
        return query.getForCaller(caller, companyId, id);
    }

    /** Cross-tenant device listing. Platform-admin only. */
    @GetMapping("/admin/all")
    public List<DeviceDtos.AdminDeviceView> adminListAll(@AuthenticationPrincipal JwtPrincipal caller) {
        return query.listAllForAdmin(caller);
    }

    /** Move a device to another company. Platform-admin only. */
    @PatchMapping("/admin/{id}/company")
    public DeviceDtos.DeviceView adminReassign(@AuthenticationPrincipal JwtPrincipal caller,
                                                @PathVariable long id,
                                                @RequestBody @jakarta.validation.Valid DeviceDtos.AdminReassignRequest req) {
        admin.reassignCompany(caller, id, req.companyId());
        return query.getForCaller(caller, null, id);
    }

    @PostMapping("/enrollment-tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceDtos.EnrollmentTokenView issueEnrollment(
            @AuthenticationPrincipal JwtPrincipal admin,
            @RequestHeader(name = TenancyHeaders.COMPANY_ID, required = false) Long companyId) {
        return enrollment.issueEnrollmentToken(admin, companyId);
    }

    /* ── Per-device project access (admin) ── */

    @GetMapping("/{id}/access")
    public DeviceAccessService.AccessView getAccess(@AuthenticationPrincipal JwtPrincipal caller,
                                                     @PathVariable long id) {
        return access.get(caller, id);
    }

    @PutMapping("/{id}/access")
    public DeviceAccessService.AccessView updateAccess(@AuthenticationPrincipal JwtPrincipal caller,
                                                        @PathVariable long id,
                                                        @RequestBody DeviceAccessService.UpdateRequest req) {
        return access.update(caller, id, req);
    }

    @PostMapping("/{id}/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat(@AuthenticationPrincipal JwtPrincipal agent,
                          @PathVariable long id,
                          @RequestBody(required = false) DeviceDtos.HeartbeatRequest body) {
        if (agent == null || !agent.isAgent() || agent.deviceId() == null || agent.deviceId() != id) {
            throw ApiException.forbidden("only the device's own agent can heartbeat");
        }
        Device d = devices.findById(id).orElseThrow(() -> ApiException.notFound("device"));
        d.touchLastSeen();
        heartbeat.touch(id, body == null ? null : body.agentVersion());
    }
}
