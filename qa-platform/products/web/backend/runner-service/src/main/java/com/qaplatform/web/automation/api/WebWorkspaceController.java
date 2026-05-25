package com.qaplatform.web.automation.api;

import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.web.automation.api.dto.WebWorkspaceDtos;
import com.qaplatform.web.automation.service.WebWorkspaceService;
import com.qaplatform.web.automation.tenancy.ProjectContext;
import com.qaplatform.web.automation.tenancy.TenancyGuard;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/web/workspace")
public class WebWorkspaceController {

    private final TenancyGuard guard;
    private final WebWorkspaceService service;

    public WebWorkspaceController(TenancyGuard guard, WebWorkspaceService service) {
        this.guard = guard;
        this.service = service;
    }

    @GetMapping("/tree")
    public WebWorkspaceDtos.Tree tree(@AuthenticationPrincipal JwtPrincipal caller,
                                      @RequestHeader("X-Project-Id") Long projectId) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.tree(caller, ctx);
    }
}
