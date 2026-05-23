package com.qaplatform.android.automation.api;

import com.qaplatform.android.automation.api.dto.RunDtos;
import com.qaplatform.android.automation.service.RunService;
import com.qaplatform.android.automation.tenancy.TenancyGuard;
import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.common.tenancy.TenancyHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/automation/runs")
public class RunController {

    private final RunService service;
    private final TenancyGuard guard;

    public RunController(RunService service, TenancyGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public List<RunDtos.Summary> list(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @RequestParam(value = "scenarioId", required = false) Long scenarioId) {
        return service.list(caller, guard.requireProject(caller, projectId), scenarioId);
    }

    @GetMapping("/{id}")
    public RunDtos.View get(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id) {
        return service.get(caller, guard.requireProject(caller, projectId), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunDtos.View create(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @RequestBody @Valid RunDtos.CreateRequest req,
            HttpServletRequest http) {
        String header = http.getHeader("Authorization");
        String jwt = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        if (jwt == null) throw ApiException.unauthorized("missing Authorization header");
        return service.create(caller, guard.requireProject(caller, projectId), req, jwt);
    }

    @PatchMapping("/{id}/tags")
    public RunDtos.View updateTags(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id,
            @RequestBody RunDtos.TagsRequest req) {
        return service.updateTags(caller, guard.requireProject(caller, projectId), id, req.tags());
    }

    /** Stop a running/queued run. Partial recording is preserved. */
    @PostMapping("/{id}/cancel")
    public RunDtos.View cancel(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id) {
        return service.cancel(caller, guard.requireProject(caller, projectId), id);
    }
}
