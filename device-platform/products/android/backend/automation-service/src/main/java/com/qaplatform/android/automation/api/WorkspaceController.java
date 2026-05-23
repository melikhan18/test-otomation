package com.qaplatform.android.automation.api;

import com.qaplatform.android.automation.api.dto.WorkspaceDtos;
import com.qaplatform.android.automation.service.WorkspaceService;
import com.qaplatform.android.automation.tenancy.TenancyGuard;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.common.tenancy.TenancyHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/automation/workspace")
public class WorkspaceController {

    private final WorkspaceService service;
    private final TenancyGuard guard;

    public WorkspaceController(WorkspaceService service, TenancyGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping("/tree")
    public WorkspaceDtos.Tree tree(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId) {
        return service.tree(caller, guard.requireProject(caller, projectId));
    }
}
