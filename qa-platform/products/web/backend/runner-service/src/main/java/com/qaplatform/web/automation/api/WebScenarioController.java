package com.qaplatform.web.automation.api;

import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.web.automation.api.dto.WebScenarioDtos;
import com.qaplatform.web.automation.service.WebScenarioService;
import com.qaplatform.web.automation.tenancy.ProjectContext;
import com.qaplatform.web.automation.tenancy.TenancyGuard;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/web/scenarios")
public class WebScenarioController {

    private final TenancyGuard guard;
    private final WebScenarioService service;

    public WebScenarioController(TenancyGuard guard, WebScenarioService service) {
        this.guard = guard;
        this.service = service;
    }

    @GetMapping
    public List<WebScenarioDtos.Summary> list(@AuthenticationPrincipal JwtPrincipal caller,
                                              @RequestHeader("X-Project-Id") Long projectId) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.list(caller, ctx);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebScenarioDtos.View create(@AuthenticationPrincipal JwtPrincipal caller,
                                       @RequestHeader("X-Project-Id") Long projectId,
                                       @RequestBody @Valid WebScenarioDtos.CreateRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.create(caller, ctx, req);
    }

    @GetMapping("/{id}")
    public WebScenarioDtos.View get(@AuthenticationPrincipal JwtPrincipal caller,
                                    @RequestHeader("X-Project-Id") Long projectId,
                                    @PathVariable long id) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.get(caller, ctx, id);
    }

    @PutMapping("/{id}")
    public WebScenarioDtos.View update(@AuthenticationPrincipal JwtPrincipal caller,
                                       @RequestHeader("X-Project-Id") Long projectId,
                                       @PathVariable long id,
                                       @RequestBody @Valid WebScenarioDtos.UpdateRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.update(caller, ctx, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal JwtPrincipal caller,
                       @RequestHeader("X-Project-Id") Long projectId,
                       @PathVariable long id) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        service.delete(caller, ctx, id);
    }

    /* ── Steps ─────────────────────────────────────────────────────────── */

    @PostMapping("/{scenarioId}/steps")
    @ResponseStatus(HttpStatus.CREATED)
    public WebScenarioDtos.StepView addStep(@AuthenticationPrincipal JwtPrincipal caller,
                                            @RequestHeader("X-Project-Id") Long projectId,
                                            @PathVariable long scenarioId,
                                            @RequestBody @Valid WebScenarioDtos.StepCreateRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.addStep(caller, ctx, scenarioId, req);
    }

    @PutMapping("/{scenarioId}/steps/{stepId}")
    public WebScenarioDtos.StepView updateStep(@AuthenticationPrincipal JwtPrincipal caller,
                                                @RequestHeader("X-Project-Id") Long projectId,
                                                @PathVariable long scenarioId,
                                                @PathVariable long stepId,
                                                @RequestBody @Valid WebScenarioDtos.StepUpdateRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.updateStep(caller, ctx, scenarioId, stepId, req);
    }

    @DeleteMapping("/{scenarioId}/steps/{stepId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStep(@AuthenticationPrincipal JwtPrincipal caller,
                           @RequestHeader("X-Project-Id") Long projectId,
                           @PathVariable long scenarioId,
                           @PathVariable long stepId) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        service.deleteStep(caller, ctx, scenarioId, stepId);
    }

    @PostMapping("/{scenarioId}/steps/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@AuthenticationPrincipal JwtPrincipal caller,
                        @RequestHeader("X-Project-Id") Long projectId,
                        @PathVariable long scenarioId,
                        @RequestBody @Valid WebScenarioDtos.ReorderRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        service.reorderSteps(caller, ctx, scenarioId, req);
    }
}
