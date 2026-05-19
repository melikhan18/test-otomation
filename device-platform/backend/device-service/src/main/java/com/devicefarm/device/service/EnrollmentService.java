package com.devicefarm.device.service;

import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
import com.devicefarm.common.jwt.JwtProperties;
import com.devicefarm.common.jwt.JwtTokenService;
import com.devicefarm.device.api.dto.DeviceDtos;
import com.devicefarm.device.domain.Device;
import com.devicefarm.device.domain.DeviceRepository;
import com.devicefarm.device.domain.EnrollmentToken;
import com.devicefarm.device.domain.EnrollmentTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class EnrollmentService {

    private static final SecureRandom RNG = new SecureRandom();

    private final EnrollmentTokenRepository tokens;
    private final DeviceRepository devices;
    private final JwtTokenService jwt;
    private final JwtProperties jwtProps;
    private final String wsUrl;

    public EnrollmentService(EnrollmentTokenRepository tokens, DeviceRepository devices,
                             JwtTokenService jwt, JwtProperties jwtProps,
                             @Value("${app.bridge.public-ws-url:ws://localhost:8080/ws/agent}") String wsUrl) {
        this.tokens = tokens;
        this.devices = devices;
        this.jwt = jwt;
        this.jwtProps = jwtProps;
        this.wsUrl = wsUrl;
    }

    @Transactional
    public DeviceDtos.EnrollmentTokenView issueEnrollmentToken(JwtPrincipal admin) {
        if (admin == null || !admin.isAdmin()) throw ApiException.forbidden("admin only");
        String token = generateOpaqueToken();
        Instant expiresAt = Instant.now().plus(jwtProps.getEnrollmentTokenTtl());
        EnrollmentToken et = tokens.save(new EnrollmentToken(token, admin.productId(), admin.userId(), expiresAt));
        return new DeviceDtos.EnrollmentTokenView(et.getToken(), et.getExpiresAt());
    }

    @Transactional
    public DeviceDtos.EnrollResponse enroll(DeviceDtos.EnrollRequest req) {
        EnrollmentToken et = tokens.findByToken(req.enrollmentToken())
                .orElseThrow(() -> ApiException.unauthorized("invalid enrollment token"));
        if (et.getUsedAt() != null) throw ApiException.unauthorized("enrollment token already used");
        if (et.getExpiresAt().isBefore(Instant.now())) throw ApiException.unauthorized("enrollment token expired");

        Device device = devices.findByProductIdAndSerial(et.getProductId(), req.serial())
                .map(existing -> existing)  // re-enroll same serial → reuse
                .orElseGet(() -> devices.save(new Device(
                        et.getProductId(), req.serial(), req.manufacturer(), req.model(),
                        req.androidVersion(), req.screenWidth(), req.screenHeight(), req.agentVersion())));

        device.touchLastSeen();
        et.markUsed(device.getId());

        String agentToken = jwt.issueAgentToken(device.getId(), device.getProductId());
        return new DeviceDtos.EnrollResponse(device.getId(), device.getProductId(), agentToken, wsUrl);
    }

    private static String generateOpaqueToken() {
        byte[] buf = new byte[24];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
