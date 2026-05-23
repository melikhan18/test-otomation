package com.devicefarm.device.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Online-ness is tracked by a short-TTL Redis key. Agent refreshes it via {@code POST /heartbeat}
 * every ~10s. Key expires after {@code app.device.heartbeat-ttl-seconds}, marking the device offline.
 */
@Service
public class HeartbeatService {

    private static final String ONLINE_KEY = "device:online:%d";
    private static final String SESSION_KEY = "device:session:%d";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public HeartbeatService(StringRedisTemplate redis,
                            @Value("${app.device.heartbeat-ttl-seconds:20}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public void touch(long deviceId, String agentVersion) {
        redis.opsForValue().set(ONLINE_KEY.formatted(deviceId), agentVersion == null ? "1" : agentVersion, ttl);
    }

    public boolean isOnline(long deviceId) {
        return Boolean.TRUE.equals(redis.hasKey(ONLINE_KEY.formatted(deviceId)));
    }

    public Long currentSessionId(long deviceId) {
        String v = redis.opsForValue().get(SESSION_KEY.formatted(deviceId));
        if (v == null || v.isBlank()) return null;
        try { return Long.parseLong(v); } catch (NumberFormatException e) { return null; }
    }
}
