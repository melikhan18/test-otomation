package com.qaplatform.android.automation.tenancy;

import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import org.springframework.stereotype.Component;

/**
 * Resolves the per-request {@link ProjectContext} from the JWT + the
 * {@code X-Project-Id} header. Every project-scoped controller calls
 * {@link #requireProject} as its first line — it validates the project exists,
 * confirms the caller has access, and packages the resolution into a context
 * object the service layer can carry around without redoing lookups.
 *
 * Permission logic (project-scoped roles)
 * ───────────────────────────────────────
 * <ul>
 *   <li>Platform admin → always allowed.</li>
 *   <li>Company OWNER → implicit access to every project in the company.</li>
 *   <li>Project QA_MANAGER / TESTER → access to that specific project only.</li>
 *   <li>Otherwise → 403.</li>
 * </ul>
 */
@Component
public class TenancyGuard {

    private final ProjectLookup lookup;

    public TenancyGuard(ProjectLookup lookup) { this.lookup = lookup; }

    public ProjectContext requireProject(JwtPrincipal caller, Long projectId) {
        if (caller == null || caller.userId() == null) throw ApiException.unauthorized("missing identity");
        if (projectId == null) throw ApiException.badRequest("missing X-Project-Id header");

        ProjectLookup.Info info = lookup.find(projectId)
                .orElseThrow(() -> ApiException.notFound("project"));

        if (caller.platformAdmin()) {
            return new ProjectContext(info.projectId(), info.companyId(), info.legacyProductId(), "PLATFORM_ADMIN");
        }
        if (!caller.isMemberOf(info.companyId())) {
            throw ApiException.forbidden("not a member of this company");
        }
        String role = caller.roleInProject(info.companyId(), info.projectId());
        if (role == null) throw ApiException.forbidden("not a member of this project");
        return new ProjectContext(info.projectId(), info.companyId(), info.legacyProductId(), role);
    }
}
