package com.qaplatform.android.session.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Cross-schema read-only view of {@code android_device.devices}. Used by SessionService
 * to answer "which company owns this device, and is it restricted?" without pulling
 * android-device-service's JPA entities into the android-session-service module.
 *
 * Cached per-device since the company assignment never changes after enrollment.
 */
@Component
public class DeviceLookup {

    public record Info(long deviceId, Long companyId, boolean restricted) {}

    private final JdbcTemplate jdbc;
    private final ConcurrentMap<Long, Info> cache = new ConcurrentHashMap<>();

    public DeviceLookup(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Info> find(long deviceId) {
        Info cached = cache.get(deviceId);
        if (cached != null) return Optional.of(cached);
        var rows = jdbc.queryForList(
                "SELECT id, company_id, restricted FROM android_device.devices WHERE id = ?", deviceId);
        if (rows.isEmpty()) return Optional.empty();
        var row = rows.get(0);
        Long companyId = row.get("company_id") == null ? null : ((Number) row.get("company_id")).longValue();
        boolean restricted = Boolean.TRUE.equals(row.get("restricted"));
        Info info = new Info(deviceId, companyId, restricted);
        cache.put(deviceId, info);
        return Optional.of(info);
    }

    /** Project ids whitelisted for a restricted device. Empty for unrestricted devices. */
    public java.util.List<Long> projectAccess(long deviceId) {
        return jdbc.query(
                "SELECT project_id FROM android_device.device_project_access WHERE device_id = ?",
                (rs, i) -> rs.getLong(1),
                deviceId);
    }
}
