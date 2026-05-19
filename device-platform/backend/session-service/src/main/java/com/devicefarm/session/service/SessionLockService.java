package com.devicefarm.session.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Owns the two Redis keys used to coordinate device reservation:
 * <ul>
 *   <li>{@code device:lock:{deviceId}} — atomic acquire (SETNX), value = sessionId, TTL = lock-ttl-minutes</li>
 *   <li>{@code device:session:{deviceId}} — convenience pointer the device-service reads to expose IN_USE</li>
 * </ul>
 * Both keys are deleted on release, both refreshed on touch.
 */
@Service
public class SessionLockService {

    private static final String LOCK_KEY    = "device:lock:%d";
    private static final String SESSION_KEY = "device:session:%d";

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

    public void release(long deviceId, long sessionId) {
        String v = redis.opsForValue().get(LOCK_KEY.formatted(deviceId));
        if (v != null && v.equals(String.valueOf(sessionId))) {
            redis.delete(LOCK_KEY.formatted(deviceId));
            redis.delete(SESSION_KEY.formatted(deviceId));
        }
    }

    public void touch(long deviceId, long sessionId) {
        redis.expire(LOCK_KEY.formatted(deviceId), ttl);
        redis.expire(SESSION_KEY.formatted(deviceId), ttl);
    }

    public Instant lockExpiresAt() { return Instant.now().plus(ttl); }
}
