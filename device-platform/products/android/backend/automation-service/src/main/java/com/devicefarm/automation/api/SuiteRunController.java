package com.devicefarm.automation.api;

import com.devicefarm.automation.api.dto.SuiteRunDtos;
import com.devicefarm.automation.service.SuiteRunService;
import com.devicefarm.automation.tenancy.TenancyGuard;
import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
import com.devicefarm.common.tenancy.TenancyHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/automation/suite-runs")
public class SuiteRunController {

    private final SuiteRunService service;
    private final TenancyGuard guard;

    public SuiteRunController(SuiteRunService service, TenancyGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public List<SuiteRunDtos.Summary> list(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @RequestParam(value = "suiteId", required = false) Long suiteId) {
        return service.list(caller, guard.requireProject(caller, projectId), suiteId);
    }

    @GetMapping("/{id}")
    public SuiteRunDtos.View get(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id) {
        return service.get(caller, guard.requireProject(caller, projectId), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SuiteRunDtos.View create(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @RequestBody @Valid SuiteRunDtos.CreateRequest req,
            HttpServletRequest http) {
        String header = http.getHeader("Authorization");
        String jwt = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        if (jwt == null) throw ApiException.unauthorized("missing Authorization header");
        return service.create(caller, guard.requireProject(caller, projectId), req, jwt);
    }

    @PatchMapping("/{id}/tags")
    public SuiteRunDtos.View updateTags(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id,
            @RequestBody SuiteRunDtos.TagsRequest req) {
        return service.updateTags(caller, guard.requireProject(caller, projectId), id, req.tags());
    }

    /** Stop a running/queued suite. Cascades a cancel to the current child run. */
    @PostMapping("/{id}/cancel")
    public SuiteRunDtos.View cancel(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id) {
        return service.cancel(caller, guard.requireProject(caller, projectId), id);
    }
}
