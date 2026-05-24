package com.qaplatform.web.automation.tenancy;

import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import org.springframework.stereotype.Component;

/**
 * Resolves the per-request {@link ProjectContext} from the JWT + the
 * {@code X-Project-Id} header. Identical semantics to Android's guard.
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
            return new ProjectContext(info.projectId(), info.companyId(), "PLATFORM_ADMIN");
        }
        if (!caller.isMemberOf(info.companyId())) {
            throw ApiException.forbidden("not a member of this company");
        }
        String role = caller.roleInProject(info.companyId(), info.projectId());
        if (role == null) throw ApiException.forbidden("not a member of this project");
        return new ProjectContext(info.projectId(), info.companyId(), role);
    }
}
