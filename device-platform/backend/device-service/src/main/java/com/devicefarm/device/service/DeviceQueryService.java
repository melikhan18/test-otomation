package com.devicefarm.device.service;

import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
import com.devicefarm.device.api.dto.DeviceDtos;
import com.devicefarm.device.domain.Device;
import com.devicefarm.device.domain.DeviceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeviceQueryService {

    private final DeviceRepository repo;
    private final HeartbeatService heartbeat;

    public DeviceQueryService(DeviceRepository repo, HeartbeatService heartbeat) {
        this.repo = repo;
        this.heartbeat = heartbeat;
    }

    public List<DeviceDtos.DeviceView> listForCaller(JwtPrincipal caller) {
        if (caller == null || caller.productId() == null) throw ApiException.unauthorized("missing identity");
        return repo.findAllByProductId(caller.productId()).stream().map(this::toView).toList();
    }

    public DeviceDtos.DeviceView getForCaller(JwtPrincipal caller, long deviceId) {
        if (caller == null || caller.productId() == null) throw ApiException.unauthorized("missing identity");
        Device d = repo.findById(deviceId).orElseThrow(() -> ApiException.notFound("device"));
        if (!d.getProductId().equals(caller.productId())) throw ApiException.forbidden("cross-product access");
        return toView(d);
    }

    private DeviceDtos.DeviceView toView(Device d) {
        Long sessionId = heartbeat.currentSessionId(d.getId());
        DeviceDtos.DeviceStatus status;
        if (sessionId != null) status = DeviceDtos.DeviceStatus.IN_USE;
        else if (heartbeat.isOnline(d.getId())) status = DeviceDtos.DeviceStatus.ONLINE;
        else status = DeviceDtos.DeviceStatus.OFFLINE;

        return new DeviceDtos.DeviceView(
                d.getId(), d.getProductId(), d.getSerial(), d.getManufacturer(), d.getModel(),
                d.getAndroidVersion(), d.getScreenWidth(), d.getScreenHeight(), d.getAgentVersion(),
                d.getEnrolledAt(), d.getLastSeenAt(), status, sessionId);
    }
}
