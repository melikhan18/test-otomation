package com.qaplatform.web.automation.tenancy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only view of {@code auth.projects} + {@code auth.companies} for
 * tenancy resolution. Same JDBC-cache pattern as Android's; kept service-
 * local instead of in :common because the columns each platform reads may
 * diverge later (e.g. web might cache `default_browser_profile` if we add
 * per-project defaults).
 */
@Component
public class ProjectLookup {

    public record Info(long projectId, long companyId, String slug, String companySlug) {}

    private final JdbcTemplate jdbc;
    private final Map<Long, Info> cache = new ConcurrentHashMap<>();

    public ProjectLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Info> find(long projectId) {
        Info cached = cache.get(projectId);
        if (cached != null) return Optional.of(cached);
        var rows = jdbc.queryForList("""
                SELECT p.id, p.company_id, p.slug AS project_slug,
                       c.slug AS company_slug
                  FROM auth.projects p
                  JOIN auth.companies c ON c.id = p.company_id
                 WHERE p.id = ? AND p.archived_at IS NULL
                """, projectId);
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> row = rows.get(0);
        Info info = new Info(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("company_id")).longValue(),
                (String) row.get("project_slug"),
                (String) row.get("company_slug")
        );
        cache.put(projectId, info);
        return Optional.of(info);
    }

    public void invalidate(long projectId) { cache.remove(projectId); }
}
