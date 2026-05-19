package com.devicefarm.automation.api;

import com.devicefarm.automation.api.dto.WorkspaceDtos;
import com.devicefarm.automation.service.WorkspaceService;
import com.devicefarm.common.jwt.JwtPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/automation/workspace")
public class WorkspaceController {

    private final WorkspaceService service;
    public WorkspaceController(WorkspaceService service) { this.service = service; }

    /**
     * Single-call sidebar payload — used by the unified Automation workspace UI.
     */
    @GetMapping("/tree")
    public WorkspaceDtos.Tree tree(@AuthenticationPrincipal JwtPrincipal caller) {
        return service.tree(caller);
    }
}
