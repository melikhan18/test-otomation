package com.qaplatform.android.automation.api;

import com.qaplatform.android.automation.api.dto.SuiteDtos;
import com.qaplatform.android.automation.service.SuiteService;
import com.qaplatform.android.automation.tenancy.ProjectContext;
import com.qaplatform.android.automation.tenancy.TenancyGuard;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.common.tenancy.TenancyHeaders;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/automation/suites")
public class SuiteController {

    private final SuiteService service;
    private final TenancyGuard guard;

    public SuiteController(SuiteService service, TenancyGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public List<SuiteDtos.Summary> list(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId) {
        return service.list(caller, guard.requireProject(caller, projectId));
    }

    @GetMapping("/{id}")
    public SuiteDtos.View get(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id) {
        return service.get(caller, guard.requireProject(caller, projectId), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuiteDtos.View create(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @RequestBody @Valid SuiteDtos.CreateRequest req) {
        return service.create(caller, guard.requireProject(caller, projectId), req);
    }

    @PutMapping("/{id}")
    public SuiteDtos.View update(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id,
            @RequestBody @Valid SuiteDtos.UpdateRequest req) {
        return service.update(caller, guard.requireProject(caller, projectId), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id) {
        service.delete(caller, guard.requireProject(caller, projectId), id);
    }

    @PostMapping("/{id}/scenarios")
    public SuiteDtos.View addScenario(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id,
            @RequestBody @Valid SuiteDtos.AddScenarioRequest req) {
        return service.addScenario(caller, guard.requireProject(caller, projectId), id, req);
    }

    @DeleteMapping("/{id}/scenarios/{scenarioId}")
    public SuiteDtos.View removeScenario(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id,
            @PathVariable long scenarioId) {
        return service.removeScenario(caller, guard.requireProject(caller, projectId), id, scenarioId);
    }

    @PutMapping("/{id}/scenarios/reorder")
    public SuiteDtos.View reorderScenarios(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id,
            @RequestBody @Valid SuiteDtos.ReorderRequest req) {
        return service.reorderScenarios(caller, guard.requireProject(caller, projectId), id, req);
    }
}
