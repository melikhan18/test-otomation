package com.qaplatform.android.session.service;

import com.qaplatform.android.session.domain.Session;
import com.qaplatform.android.session.domain.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Sweeps ACTIVE sessions in two ways:
 *
 * <ol>
 *   <li><b>Device offline</b>: agent presence is tracked by the android-device-service
 *       via the Redis key {@code device:online:{deviceId}} (TTL ≈ 20 s, refreshed by
 *       HTTP heartbeat). If that key disappears while we still hold an ACTIVE session
 *       the lock + DB row are stale — release them so the device returns to OFFLINE /
 *       ONLINE on the next refresh. ~50 s worst-case detection window.</li>
 *   <li><b>Max age</b>: any session ACTIVE longer than {@link #MAX_SESSION_AGE} is
 *       almost certainly orphaned — either the orchestrator container restarted
 *       mid-run, or the {@code finally} block that calls {@code sessions.release(...)}
 *       threw before it got there. We end it unconditionally so the device frees up
 *       without operator intervention. No real test execution should run an hour,
 *       so the threshold is conservative.</li>
 * </ol>
 */
@Service
public class OrphanedSessionReaper {

    private static final Logger log = LoggerFactory.getLogger(OrphanedSessionReaper.class);
    private static final String ONLINE_KEY = "device:online:%d";
    private static final Duration MAX_SESSION_AGE = Duration.ofHours(1);

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
        Instant maxAgeCutoff = Instant.now().minus(MAX_SESSION_AGE);
        for (Session s : sessions.findAllByStatus("ACTIVE")) {
            boolean online = Boolean.TRUE.equals(redis.hasKey(ONLINE_KEY.formatted(s.getDeviceId())));
            boolean tooOld = s.getCreatedAt() != null && s.getCreatedAt().isBefore(maxAgeCutoff);

            if (!online) {
                s.end();
                locks.release(s.getDeviceId(), s.getId());
                log.info("auto-ended orphan session {} (device {} offline)", s.getId(), s.getDeviceId());
            } else if (tooOld) {
                s.end();
                locks.release(s.getDeviceId(), s.getId());
                log.warn("auto-ended stale session {} on device {} — exceeded max age {} (orchestrator likely leaked it)",
                        s.getId(), s.getDeviceId(), MAX_SESSION_AGE);
            }
        }

        // Reverse sweep: walk the Redis lock keys directly and free any whose
        // session row is NOT in ACTIVE state (or no longer exists). Catches:
        //   • DB rows manually tampered with (operator ran UPDATE ... ENDED)
        //   • Race where the release Lua script returned 0 but somehow the
        //     keys weren't deleted
        //   • Any other path that ended the DB row without going through
        //     SessionLockService.release(...)
        // Without this, the device-service sees device:lock:N + device:session:N
        // and keeps reporting IN_USE indefinitely even though the session is gone.
        Set<String> lockKeys = redis.keys("device:lock:*");
        if (lockKeys != null) {
            for (String key : lockKeys) {
                long deviceId;
                try { deviceId = Long.parseLong(key.substring("device:lock:".length())); }
                catch (NumberFormatException nfe) { continue; }

                String sessionIdStr = redis.opsForValue().get(key);
                if (sessionIdStr == null) continue;  // expired between keys() and get()
                long sessionId;
                try { sessionId = Long.parseLong(sessionIdStr); }
                catch (NumberFormatException nfe) { continue; }

                Optional<Session> sOpt = sessions.findById(sessionId);
                boolean stillActive = sOpt.isPresent() && "ACTIVE".equals(sOpt.get().getStatus());
                if (!stillActive) {
                    locks.release(deviceId, sessionId);
                    log.warn("auto-released stale Redis lock on device {} (session {} no longer ACTIVE in DB)",
                            deviceId, sessionId);
                }
            }
        }
    }
}
