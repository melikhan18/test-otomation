package com.devicefarm.session.service;

import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
import com.devicefarm.common.jwt.JwtTokenService;
import com.devicefarm.session.api.dto.SessionDtos;
import com.devicefarm.session.domain.Session;
import com.devicefarm.session.domain.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SessionService {

    private final SessionRepository sessions;
    private final SessionLockService locks;
    private final JwtTokenService jwt;

    public SessionService(SessionRepository sessions, SessionLockService locks, JwtTokenService jwt) {
        this.sessions = sessions;
        this.locks = locks;
        this.jwt = jwt;
    }

    @Transactional
    public SessionDtos.SessionView create(JwtPrincipal caller, long deviceId) {
        if (caller == null || !caller.isUser()) throw ApiException.forbidden("user required");

        // Reserve a placeholder Session row first so we have an id for the lock value.
        Session s = sessions.save(new Session(deviceId, caller.userId(), caller.productId()));

        if (!locks.tryAcquire(deviceId, s.getId())) {
            // someone else holds it — roll back
            sessions.delete(s);
            throw ApiException.conflict("device is currently in use");
        }

        return toView(s, true);
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
        locks.touch(s.getDeviceId(), s.getId());
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
