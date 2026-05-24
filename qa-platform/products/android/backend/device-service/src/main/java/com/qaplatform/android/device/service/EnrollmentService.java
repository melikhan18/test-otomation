package com.qaplatform.android.device.service;

import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.common.jwt.JwtProperties;
import com.qaplatform.common.jwt.JwtTokenService;
import com.qaplatform.android.device.api.dto.DeviceDtos;
import com.qaplatform.android.device.domain.Device;
import com.qaplatform.android.device.domain.DeviceRepository;
import com.qaplatform.android.device.domain.EnrollmentToken;
import com.qaplatform.android.device.domain.EnrollmentTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * Mint an enrollment token against the caller's active company. Header
     * {@code X-Company-Id} carries the target company; the JWT must show the
     * caller is OWNER/QA_MANAGER (or platform admin) there.
     */
    @Transactional
    public DeviceDtos.EnrollmentTokenView issueEnrollmentToken(JwtPrincipal admin, Long companyId) {
        if (admin == null || admin.userId() == null) throw ApiException.unauthorized("missing identity");
        if (companyId == null) throw ApiException.badRequest("missing X-Company-Id header");
        // Enrollment provisions a device into the company as a whole, so it's an
        // OWNER-level operation. QA_MANAGERs are project-scoped now and don't get
        // to bring new devices into the org on their own.
        if (!admin.isOwnerOf(companyId)) {
            throw ApiException.forbidden("OWNER role required");
        }
        String token = generateOpaqueToken();
        Instant expiresAt = Instant.now().plus(jwtProps.getEnrollmentTokenTtl());
        EnrollmentToken et = tokens.save(new EnrollmentToken(
                token, companyId, admin.userId(), expiresAt));
        return new DeviceDtos.EnrollmentTokenView(et.getToken(), et.getExpiresAt());
    }

    @Transactional
    public DeviceDtos.EnrollResponse enroll(DeviceDtos.EnrollRequest req) {
        EnrollmentToken et = tokens.findByToken(req.enrollmentToken())
                .orElseThrow(() -> ApiException.unauthorized("invalid enrollment token"));
        if (et.getUsedAt() != null) throw ApiException.unauthorized("enrollment token already used");
        if (et.getExpiresAt().isBefore(Instant.now())) throw ApiException.unauthorized("enrollment token expired");

        Long companyId = et.getCompanyId();
        Device device = findOrCreateDevice(companyId, req);

        device.touchLastSeen();
        et.markUsed(device.getId());

        // Agent JWT still carries a productId positional claim on the shared
        // JwtTokenService API; pass 0 — no consumer reads it. Bridge validates
        // by (deviceId, signature) only.
        String agentToken = jwt.issueAgentToken(device.getId(), 0L);
        return new DeviceDtos.EnrollResponse(device.getId(), agentToken, wsUrl);
    }

    /**
     * Find existing device by (company, serial) or insert a new one. The lookup +
     * insert is a classic find-or-create race: two concurrent enrolls of the same
     * serial both pass the initial {@code findByCompanyIdAndSerial} check and try
     * to insert. The V3 partial unique index {@code uniq_devices_company_serial}
     * catches the second insert with a {@link DataIntegrityViolationException};
     * we recover by re-fetching the row the first caller created.
     */
    private Device findOrCreateDevice(Long companyId, DeviceDtos.EnrollRequest req) {
        var existing = devices.findByCompanyIdAndSerial(companyId, req.serial());
        if (existing.isPresent()) return existing.get();

        Device fresh = new Device(
                companyId, req.serial(), req.manufacturer(), req.model(),
                req.androidVersion(), req.screenWidth(), req.screenHeight(), req.agentVersion());
        try {
            return devices.save(fresh);
        } catch (DataIntegrityViolationException race) {
            // Another concurrent enroll won the insert. Re-fetch and continue with that row.
            return devices.findByCompanyIdAndSerial(companyId, req.serial())
                    .orElseThrow(() -> race);  // Should never happen — re-throw the original to aid debugging.
        }
    }

    private static String generateOpaqueToken() {
        byte[] buf = new byte[24];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
