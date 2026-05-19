package com.devicefarm.device.api;

import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
import com.devicefarm.device.api.dto.DeviceDtos;
import com.devicefarm.device.domain.Device;
import com.devicefarm.device.domain.DeviceRepository;
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
    private final EnrollmentService enrollment;
    private final HeartbeatService heartbeat;
    private final DeviceRepository devices;

    public DeviceController(DeviceQueryService query, EnrollmentService enrollment,
                            HeartbeatService heartbeat, DeviceRepository devices) {
        this.query = query;
        this.enrollment = enrollment;
        this.heartbeat = heartbeat;
        this.devices = devices;
    }

    @GetMapping
    public List<DeviceDtos.DeviceView> list(@AuthenticationPrincipal JwtPrincipal caller) {
        return query.listForCaller(caller);
    }

    @GetMapping("/{id}")
    public DeviceDtos.DeviceView get(@AuthenticationPrincipal JwtPrincipal caller, @PathVariable long id) {
        return query.getForCaller(caller, id);
    }

    @PostMapping("/enrollment-tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceDtos.EnrollmentTokenView issueEnrollment(@AuthenticationPrincipal JwtPrincipal admin) {
        return enrollment.issueEnrollmentToken(admin);
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
