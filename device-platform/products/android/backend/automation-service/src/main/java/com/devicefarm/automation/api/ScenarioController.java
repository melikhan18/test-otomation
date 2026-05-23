package com.devicefarm.automation.api;

import com.devicefarm.automation.api.dto.ScenarioDtos;
import com.devicefarm.automation.service.ScenarioService;
import com.devicefarm.automation.tenancy.ProjectContext;
import com.devicefarm.automation.tenancy.TenancyGuard;
import com.devicefarm.common.jwt.JwtPrincipal;
import com.devicefarm.common.tenancy.TenancyHeaders;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/automation/scenarios")
public class ScenarioController {

    private final ScenarioService service;
    private final TenancyGuard guard;

    public ScenarioController(ScenarioService service, TenancyGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    /* ── Scenario CRUD ────────────────────────────────────────────────── */

    @GetMapping
    public List<ScenarioDtos.Summary> list(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.list(caller, ctx);
    }

    @GetMapping("/{id}")
    public ScenarioDtos.View get(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.get(caller, ctx, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScenarioDtos.View create(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @RequestBody @Valid ScenarioDtos.CreateRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.create(caller, ctx, req);
    }

    @PutMapping("/{id}")
    public ScenarioDtos.View update(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id,
            @RequestBody @Valid ScenarioDtos.UpdateRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.update(caller, ctx, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        service.delete(caller, ctx, id);
    }

    /* ── Steps under a scenario ───────────────────────────────────────── */

    @PostMapping("/{id}/steps")
    @ResponseStatus(HttpStatus.CREATED)
    public ScenarioDtos.View addStep(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id,
            @RequestBody @Valid ScenarioDtos.StepCreateRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.addStep(caller, ctx, id, req);
    }

    @PutMapping("/{id}/steps/{stepId}")
    public ScenarioDtos.View updateStep(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id,
            @PathVariable long stepId,
            @RequestBody @Valid ScenarioDtos.StepUpdateRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.updateStep(caller, ctx, id, stepId, req);
    }

    @DeleteMapping("/{id}/steps/{stepId}")
    public ScenarioDtos.View deleteStep(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id,
            @PathVariable long stepId) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.deleteStep(caller, ctx, id, stepId);
    }

    @PutMapping("/{id}/steps/reorder")
    public ScenarioDtos.View reorderSteps(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id,
            @RequestBody @Valid ScenarioDtos.ReorderRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.reorderSteps(caller, ctx, id, req);
    }
}
