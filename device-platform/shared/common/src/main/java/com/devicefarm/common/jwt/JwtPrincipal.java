package com.devicefarm.common.jwt;

import java.util.List;

/**
 * Authenticated principal extracted from a verified JWT.
 *
 * Tenancy model (post project-roles refactor)
 * ────────────────────────────────────────────
 * A user can belong to multiple {@link CompanyMembership companies}. Each
 * membership has an {@code owner} flag (company-wide administration) and a
 * list of {@link ProjectGrant project grants} — explicit per-project roles
 * (QA_MANAGER / TESTER). A company OWNER has implicit access to every project
 * in their company and does not need explicit grants. The grants list is empty
 * for plain MEMBERs until an OWNER assigns them somewhere.
 *
 * The {@link #platformAdmin} flag, when true, grants cross-company access
 * (vendor / platform staff). It is a separate concept from any company role
 * and is effectively "OWNER of everything".
 *
 * Legacy
 * ──────
 * {@link #productId} is the pre-tenancy single-tenant ID. Still emitted during
 * migration so downstream services that haven't moved to project-scoped
 * filtering keep working. Drop once every service has migrated.
 */
public record JwtPrincipal(
        String subject,
        Long userId,
        Long deviceId,
        Long sessionId,
        String role,
        Long productId,
        boolean platformAdmin,
        List<CompanyMembership> companies
) {
    public boolean isAdmin()  { return platformAdmin || "ADMIN".equals(role); }
    public boolean isAgent()  { return "AGENT".equals(role); }
    public boolean isUser()   { return userId != null; }

    /** OWNER of this company? Platform admins always count as OWNER. */
    public boolean isOwnerOf(long companyId) {
        if (platformAdmin) return true;
        CompanyMembership m = findCompany(companyId);
        return m != null && m.owner();
    }

    /** Any membership in this company — OWNER or someone with a project grant. */
    public boolean isMemberOf(long companyId) {
        if (platformAdmin) return true;
        return findCompany(companyId) != null;
    }

    /**
     * Role in a specific project.
     *   - {@code "OWNER"} if the user is OWNER of the project's company (or
     *     platform admin).
     *   - {@code "QA_MANAGER"} / {@code "TESTER"} if explicitly granted on the
     *     project.
     *   - {@code null} when the user has no access.
     */
    public String roleInProject(long companyId, long projectId) {
        if (platformAdmin) return "OWNER";
        CompanyMembership m = findCompany(companyId);
        if (m == null) return null;
        if (m.owner()) return "OWNER";
        if (m.projects() != null) {
            for (ProjectGrant g : m.projects()) {
                if (g.id() == projectId) return g.role();
            }
        }
        return null;
    }

    /** Convenience: can the user see/use this project at all? */
    public boolean hasProjectAccess(long companyId, long projectId) {
        return roleInProject(companyId, projectId) != null;
    }

    /** OWNER or QA_MANAGER in a project — write-level capabilities. */
    public boolean canManageProject(long companyId, long projectId) {
        String r = roleInProject(companyId, projectId);
        return "OWNER".equals(r) || "QA_MANAGER".equals(r);
    }

    private CompanyMembership findCompany(long companyId) {
        if (companies == null) return null;
        for (CompanyMembership m : companies) {
            if (m.id() == companyId) return m;
        }
        return null;
    }

    /**
     * One company a user belongs to.
     *
     * @param owner    true → company OWNER; false → plain MEMBER with whatever
     *                 explicit project grants are in {@code projects}.
     * @param projects per-project role grants (QA_MANAGER / TESTER). Always
     *                 empty for an OWNER (their access is implicit).
     */
    public record CompanyMembership(
            long id,
            String slug,
            boolean owner,
            List<ProjectGrant> projects
    ) {}

    /** A user's role on one project: id + ("QA_MANAGER" | "TESTER"). */
    public record ProjectGrant(long id, String role) {}
}
