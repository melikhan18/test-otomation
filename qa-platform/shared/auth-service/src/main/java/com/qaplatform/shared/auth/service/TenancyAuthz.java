package com.qaplatform.shared.auth.service;

import com.qaplatform.shared.auth.domain.CompanyMember;
import com.qaplatform.shared.auth.domain.CompanyMemberRepository;
import com.qaplatform.shared.auth.domain.Project;
import com.qaplatform.shared.auth.domain.ProjectMember;
import com.qaplatform.shared.auth.domain.ProjectMemberRepository;
import com.qaplatform.shared.auth.domain.ProjectRepository;
import com.qaplatform.shared.auth.domain.User;
import com.qaplatform.shared.auth.domain.UserRepository;
import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import org.springframework.stereotype.Component;

/**
 * Centralised "who can do what to whom" checks.
 *
 * Project-scoped roles model:
 *   - OWNER lives on {@code company_members} (or via platform_admin) and grants
 *     implicit access to every project in the company.
 *   - QA_MANAGER / TESTER live on {@code project_members}, one row per
 *     (user, project) pair. A user can be QA_MANAGER on one project and TESTER
 *     on another.
 *
 * Always reads the user's *current* DB state — JWT membership lists are a hint
 * for downstream services but the source of truth for these checks is the DB.
 */
@Component
public class TenancyAuthz {

    private final UserRepository users;
    private final CompanyMemberRepository companyMembers;
    private final ProjectMemberRepository projectMembers;
    private final ProjectRepository projects;

    public TenancyAuthz(UserRepository users,
                        CompanyMemberRepository companyMembers,
                        ProjectMemberRepository projectMembers,
                        ProjectRepository projects) {
        this.users = users;
        this.companyMembers = companyMembers;
        this.projectMembers = projectMembers;
        this.projects = projects;
    }

    /** Authenticated user, throws 401 if missing/unknown. */
    public User requireUser(JwtPrincipal caller) {
        if (caller == null || caller.userId() == null) throw ApiException.unauthorized("missing identity");
        return users.findById(caller.userId())
                .orElseThrow(() -> ApiException.unauthorized("user not found"));
    }

    /** True iff user is OWNER of this company (platform admins always count). */
    public boolean isOwner(User user, long companyId) {
        if (user.isPlatformAdmin()) return true;
        return companyMembers.findByUserIdAndCompanyId(user.getId(), companyId)
                .map(cm -> "OWNER".equals(cm.getRole()))
                .orElse(false);
    }

    /** Throws 403 unless OWNER. */
    public void requireOwner(User user, long companyId) {
        if (!isOwner(user, companyId)) throw ApiException.forbidden("OWNER role required");
    }

    /**
     * Throws 403 unless the user has *any* footing in the company — OWNER or any
     * project role. Used by listings that need to verify membership before
     * filtering further (e.g. "list members" only requires membership; the
     * response itself filters per role).
     */
    public CompanyMember requireMembership(User user, long companyId) {
        if (user.isPlatformAdmin()) {
            return companyMembers.findByUserIdAndCompanyId(user.getId(), companyId)
                    .orElseGet(() -> new CompanyMember(user.getId(), companyId, "OWNER"));
        }
        return companyMembers.findByUserIdAndCompanyId(user.getId(), companyId)
                .orElseThrow(() -> ApiException.forbidden("not a member of this company"));
    }

    /** Throws 403 unless the user has access to this project (OWNER or project grant). */
    public String requireProjectAccess(User user, long companyId, long projectId) {
        Project p = projects.findById(projectId).orElseThrow(() -> ApiException.notFound("project"));
        if (!p.getCompanyId().equals(companyId)) throw ApiException.notFound("project");

        if (isOwner(user, companyId)) return "OWNER";
        return projectMembers.findByUserIdAndProjectId(user.getId(), projectId)
                .map(ProjectMember::getRole)
                .orElseThrow(() -> ApiException.forbidden("no access to this project"));
    }

    /** Throws 403 unless OWNER or QA_MANAGER on the project. */
    public String requireProjectManager(User user, long companyId, long projectId) {
        String role = requireProjectAccess(user, companyId, projectId);
        if (!"OWNER".equals(role) && !"QA_MANAGER".equals(role)) {
            throw ApiException.forbidden("project manager role required");
        }
        return role;
    }
}
