package com.qaplatform.android.session.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Owns the two Redis keys used to coordinate device reservation:
 * <ul>
 *   <li>{@code device:lock:{deviceId}} — atomic acquire (SETNX), value = sessionId, TTL = lock-ttl-minutes</li>
 *   <li>{@code device:session:{deviceId}} — convenience pointer the android-device-service reads to expose IN_USE</li>
 * </ul>
 * Both keys are deleted on release, both refreshed on touch.
 *
 * <h2>Why Lua scripts for release/touch</h2>
 * <p>{@link #release} and {@link #touch} must run as <em>compare-and-act</em> against the
 * lock's current value: only the owner of the lock may delete or refresh it. If we split
 * the GET and the DEL/EXPIRE into two round-trips, the TTL can expire between them, a
 * concurrent {@link #tryAcquire} can hand the lock to a different session, and our DEL
 * silently wipes the new owner's lock — letting two sessions drive the same device.</p>
 *
 * <p>A single {@code EVAL} on Redis runs atomically (single-threaded server, no other
 * command can interleave), which is the canonical Redlock-style fix.</p>
 */
@Service
public class SessionLockService {

    private static final String LOCK_KEY    = "device:lock:%d";
    private static final String SESSION_KEY = "device:session:%d";

    /**
     * KEYS[1] = lock key, KEYS[2] = session pointer key, ARGV[1] = expected sessionId.
     * Delete both keys only when the lock still holds our sessionId.
     */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "  redis.call('del', KEYS[1]); " +
                    "  redis.call('del', KEYS[2]); " +
                    "  return 1 " +
                    "else return 0 end",
                    Long.class);

    /**
     * KEYS[1] = lock key, KEYS[2] = session pointer key, ARGV[1] = expected sessionId,
     * ARGV[2] = ttl seconds. Refresh both TTLs only when the lock still holds our sessionId.
     * Without the value check, a frontend that keeps polling {@code touch} would silently
     * extend whoever happens to own the lock right now — even if our session was reaped.
     */
    private static final DefaultRedisScript<Long> TOUCH_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "  redis.call('expire', KEYS[1], ARGV[2]); " +
                    "  redis.call('expire', KEYS[2], ARGV[2]); " +
                    "  return 1 " +
                    "else return 0 end",
                    Long.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public SessionLockService(StringRedisTemplate redis,
                              @Value("${app.session.lock-ttl-minutes:30}") long ttlMinutes) {
        this.redis = redis;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    /** True if the lock was acquired (device was free). */
    public boolean tryAcquire(long deviceId, long sessionId) {
        Boolean ok = redis.opsForValue().setIfAbsent(LOCK_KEY.formatted(deviceId), String.valueOf(sessionId), ttl);
        if (Boolean.TRUE.equals(ok)) {
            redis.opsForValue().set(SESSION_KEY.formatted(deviceId), String.valueOf(sessionId), ttl);
            return true;
        }
        return false;
    }

    /**
     * Release the lock <em>only if we still own it</em>. Atomic — see class javadoc.
     * Returns true when the script actually deleted the keys (we were the owner),
     * false when the lock had already expired or been taken over by another session.
     * Callers may ignore the return when they don't need confirmation (the typical
     * "End session" path treats the operation as best-effort cleanup).
     */
    public boolean release(long deviceId, long sessionId) {
        Long result = redis.execute(
                RELEASE_SCRIPT,
                List.of(LOCK_KEY.formatted(deviceId), SESSION_KEY.formatted(deviceId)),
                String.valueOf(sessionId));
        return result != null && result == 1L;
    }

    /**
     * Refresh the TTL on both keys, but only if our session still holds the lock.
     * Atomic — see class javadoc. Returns true if the refresh succeeded; false means
     * the lock no longer points at us (someone else has it, or it's been reaped).
     * The frontend's periodic touch should surface a false return as "session lost".
     */
    public boolean touch(long deviceId, long sessionId) {
        Long result = redis.execute(
                TOUCH_SCRIPT,
                List.of(LOCK_KEY.formatted(deviceId), SESSION_KEY.formatted(deviceId)),
                String.valueOf(sessionId),
                String.valueOf(ttl.toSeconds()));
        return result != null && result == 1L;
    }

    public Instant lockExpiresAt() { return Instant.now().plus(ttl); }
}
