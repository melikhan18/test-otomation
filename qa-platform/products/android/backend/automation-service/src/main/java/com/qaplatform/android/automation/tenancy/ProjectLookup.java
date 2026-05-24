package com.qaplatform.android.automation.tenancy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only cross-schema view of {@code auth.projects} + {@code auth.companies}.
 *
 * The automation service needs to resolve a project id into its parent
 * {@code companyId} for permission checks. Implemented with raw JDBC instead
 * of JPA to avoid pulling auth's entities into the automation persistence
 * unit. We cache aggressively since project metadata is effectively
 * immutable — created once, never relocated.
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

    /** Wipe the cache for a single project — call after project rename / archive. */
    public void invalidate(long projectId) { cache.remove(projectId); }
}
