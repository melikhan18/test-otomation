package com.qaplatform.android.session.service;

import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.common.jwt.JwtTokenService;
import com.qaplatform.android.session.api.dto.SessionDtos;
import com.qaplatform.android.session.domain.Session;
import com.qaplatform.android.session.domain.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SessionService {

    private final SessionRepository sessions;
    private final SessionLockService locks;
    private final JwtTokenService jwt;
    private final DeviceLookup devices;

    public SessionService(SessionRepository sessions, SessionLockService locks, JwtTokenService jwt,
                          DeviceLookup devices) {
        this.sessions = sessions;
        this.locks = locks;
        this.jwt = jwt;
        this.devices = devices;
    }

    /**
     * Reserve a device. Tenancy guard verifies the caller belongs to the device's
     * company; if an active project is supplied and the device is restricted, the
     * project must be on its access whitelist. Anyone who slips past those checks
     * would be bypassing the android-device-service visibility filter — so we re-check here.
     */
    @Transactional
    public SessionDtos.SessionView create(JwtPrincipal caller, long deviceId, Long companyId, Long projectId) {
        if (caller == null || !caller.isUser()) throw ApiException.forbidden("user required");

        // First pass — short-circuit obvious violations before paying for a DB insert
        // and a Redis SETNX. The same checks run again post-lock to close the
        // TOCTOU window (admin reassignment between check and acquire).
        checkAccess(caller, deviceId, companyId, projectId);

        // Reserve a placeholder Session row first so we have an id for the lock value.
        Session s = sessions.save(new Session(deviceId, caller.userId(), caller.productId()));

        if (!locks.tryAcquire(deviceId, s.getId())) {
            // someone else holds it — roll back
            sessions.delete(s);
            throw ApiException.conflict("device is currently in use");
        }

        // Re-check tenancy AFTER acquiring the lock. Between the first checkAccess()
        // call and now an admin could have moved the device to another company, or
        // toggled its restricted flag, or revoked our project's access grant. With
        // the lock held we are the only writer; if anything looks wrong, release the
        // lock + delete the placeholder row before throwing so we don't leave a
        // dangling reservation on a device the caller can no longer drive.
        try {
            checkAccess(caller, deviceId, companyId, projectId);
        } catch (RuntimeException recheck) {
            locks.release(deviceId, s.getId());
            sessions.delete(s);
            throw recheck;
        }

        return toView(s, true);
    }

    /**
     * Pure-validation helper — throws {@link ApiException} on any policy violation.
     * Has no side effects; safe to call multiple times in the same transaction.
     */
    private void checkAccess(JwtPrincipal caller, long deviceId, Long companyId, Long projectId) {
        DeviceLookup.Info info = devices.find(deviceId)
                .orElseThrow(() -> ApiException.notFound("device"));
        if (caller.platformAdmin()) return;
        Long deviceCompanyId = info.companyId();
        if (deviceCompanyId == null) {
            throw ApiException.forbidden("device has no company assignment");
        }
        if (!caller.isMemberOf(deviceCompanyId)) {
            throw ApiException.forbidden("not a member of this device's company");
        }
        if (companyId != null && !companyId.equals(deviceCompanyId)) {
            throw ApiException.badRequest("device not in active company");
        }
        if (info.restricted() && projectId != null
                && !devices.projectAccess(deviceId).contains(projectId)) {
            throw ApiException.forbidden("device not granted to active project");
        }
    }

    @Transactional
    public void end(JwtPrincipal caller, long sessionId) {
        Session s = ensureOwned(caller, sessionId);
        if (s.isActive()) {
            s.end();
            locks.release(s.getDeviceId(), s.getId());
        }
    }

    @Transactional
    public SessionDtos.SessionView touch(JwtPrincipal caller, long sessionId) {
        Session s = ensureOwned(caller, sessionId);
        if (!s.isActive()) throw ApiException.conflict("session ended");
        // Atomic compare-and-refresh. Returns false when the lock has been reaped
        // (TTL expired) or taken over by another session — either way the client
        // should bail out instead of pretending the session is still healthy.
        boolean refreshed = locks.touch(s.getDeviceId(), s.getId());
        if (!refreshed) {
            // Persist the session row as ENDED so the frontend's next poll sees a
            // terminal state, then surface a 409 so the active request errors out.
            s.end();
            sessions.save(s);
            throw ApiException.conflict("session lock expired or taken over");
        }
        return toView(s, true);
    }

    @Transactional(readOnly = true)
    public SessionDtos.SessionView get(JwtPrincipal caller, long sessionId) {
        Session s = ensureOwned(caller, sessionId);
        return toView(s, s.isActive());
    }

    @Transactional(readOnly = true)
    public List<SessionDtos.SessionView> activeForCaller(JwtPrincipal caller) {
        if (caller == null) throw ApiException.unauthorized("unauthenticated");
        return sessions.findAllByUserIdAndStatus(caller.userId(), "ACTIVE")
                .stream().map(s -> toView(s, false)).toList();
    }

    private Session ensureOwned(JwtPrincipal caller, long sessionId) {
        if (caller == null) throw ApiException.unauthorized("unauthenticated");
        Session s = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("session"));
        if (!s.getProductId().equals(caller.productId())) throw ApiException.forbidden("cross-product");
        if (!caller.isAdmin() && !s.getUserId().equals(caller.userId())) throw ApiException.forbidden("not owner");
        return s;
    }

    private SessionDtos.SessionView toView(Session s, boolean includeToken) {
        String token = includeToken
                ? jwt.issueSessionToken(s.getId(), s.getDeviceId(), s.getUserId(), s.getProductId())
                : null;
        return new SessionDtos.SessionView(
                s.getId(), s.getDeviceId(), s.getUserId(), s.getProductId(),
                s.getStatus(), s.getCreatedAt(), s.getEndedAt(),
                token, includeToken ? locks.lockExpiresAt() : null);
    }
}
