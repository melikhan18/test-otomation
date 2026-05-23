package com.qaplatform.shared.tenant.api;

import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.shared.tenant.api.dto.ProjectPlatformDtos;
import com.qaplatform.shared.tenant.service.ProjectPlatformService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenancy/projects/{projectId}/platforms")
public class ProjectPlatformController {

    private final ProjectPlatformService service;

    public ProjectPlatformController(ProjectPlatformService service) {
        this.service = service;
    }

    @GetMapping
    public ProjectPlatformDtos.ProjectPlatformsView list(
            @AuthenticationPrincipal JwtPrincipal caller,
            @PathVariable long projectId) {
        return service.list(caller, projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectPlatformDtos.PlatformView enable(
            @AuthenticationPrincipal JwtPrincipal caller,
            @PathVariable long projectId,
            @RequestBody @Valid ProjectPlatformDtos.EnableRequest req) {
        return service.enable(caller, projectId, req);
    }

    @DeleteMapping("/{platform}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(
            @AuthenticationPrincipal JwtPrincipal caller,
            @PathVariable long projectId,
            @PathVariable String platform) {
        service.disable(caller, projectId, platform);
    }
}
