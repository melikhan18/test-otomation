package com.devicefarm.session.service;

import com.devicefarm.session.domain.Session;
import com.devicefarm.session.domain.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sweeps ACTIVE sessions whose underlying agent is no longer online.
 *
 * Agent presence is tracked by the device-service via the Redis key
 * {@code device:online:{deviceId}} (TTL ≈ 20 s, refreshed by HTTP heartbeat). If that key
 * disappears while we still hold an ACTIVE session, the lock and DB row are stale —
 * release them so the device-service stops reporting IN_USE and the device returns to
 * OFFLINE / ONLINE on the next refresh.
 *
 * A small grace period (one sweep cycle) absorbs transient network blips: the agent has
 * 20 s of TTL plus our 30 s scheduling delay before the reaper acts, ~50 s worst-case.
 */
@Service
public class OrphanedSessionReaper {

    private static final Logger log = LoggerFactory.getLogger(OrphanedSessionReaper.class);
    private static final String ONLINE_KEY = "device:online:%d";

    private final SessionRepository sessions;
    private final SessionLockService locks;
    private final StringRedisTemplate redis;

    public OrphanedSessionReaper(SessionRepository sessions, SessionLockService locks, StringRedisTemplate redis) {
        this.sessions = sessions;
        this.locks = locks;
        this.redis = redis;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    @Transactional
    public void sweep() {
        for (Session s : sessions.findAllByStatus("ACTIVE")) {
            boolean online = Boolean.TRUE.equals(redis.hasKey(ONLINE_KEY.formatted(s.getDeviceId())));
            if (!online) {
                s.end();
                locks.release(s.getDeviceId(), s.getId());
                log.info("auto-ended orphan session {} (device {} offline)", s.getId(), s.getDeviceId());
            }
        }
    }
}
