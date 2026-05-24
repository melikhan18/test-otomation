package com.qaplatform.web.automation.api;

import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.web.automation.api.dto.WebSuiteDtos;
import com.qaplatform.web.automation.service.WebSuiteService;
import com.qaplatform.web.automation.tenancy.ProjectContext;
import com.qaplatform.web.automation.tenancy.TenancyGuard;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/web/suites")
public class WebSuiteController {

    private final TenancyGuard guard;
    private final WebSuiteService service;

    public WebSuiteController(TenancyGuard guard, WebSuiteService service) {
        this.guard = guard;
        this.service = service;
    }

    @GetMapping
    public List<WebSuiteDtos.Summary> list(@AuthenticationPrincipal JwtPrincipal caller,
                                           @RequestHeader("X-Project-Id") Long projectId) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.list(caller, ctx);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebSuiteDtos.View create(@AuthenticationPrincipal JwtPrincipal caller,
                                    @RequestHeader("X-Project-Id") Long projectId,
                                    @RequestBody @Valid WebSuiteDtos.CreateRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.create(caller, ctx, req);
    }

    @GetMapping("/{id}")
    public WebSuiteDtos.View get(@AuthenticationPrincipal JwtPrincipal caller,
                                 @RequestHeader("X-Project-Id") Long projectId,
                                 @PathVariable long id) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.get(caller, ctx, id);
    }

    @PutMapping("/{id}")
    public WebSuiteDtos.View update(@AuthenticationPrincipal JwtPrincipal caller,
                                    @RequestHeader("X-Project-Id") Long projectId,
                                    @PathVariable long id,
                                    @RequestBody @Valid WebSuiteDtos.UpdateRequest req) {
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

    /* ── Membership ─────────────────────────────────────────────────── */

    @PostMapping("/{suiteId}/scenarios")
    public WebSuiteDtos.View addScenario(@AuthenticationPrincipal JwtPrincipal caller,
                                         @RequestHeader("X-Project-Id") Long projectId,
                                         @PathVariable long suiteId,
                                         @RequestBody @Valid WebSuiteDtos.AddScenarioRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.addScenario(caller, ctx, suiteId, req.scenarioId());
    }

    @DeleteMapping("/{suiteId}/scenarios/{scenarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeScenario(@AuthenticationPrincipal JwtPrincipal caller,
                               @RequestHeader("X-Project-Id") Long projectId,
                               @PathVariable long suiteId,
                               @PathVariable long scenarioId) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        service.removeScenario(caller, ctx, suiteId, scenarioId);
    }

    @PostMapping("/{suiteId}/scenarios/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@AuthenticationPrincipal JwtPrincipal caller,
                        @RequestHeader("X-Project-Id") Long projectId,
                        @PathVariable long suiteId,
                        @RequestBody @Valid WebSuiteDtos.ReorderRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        service.reorderScenarios(caller, ctx, suiteId, req.scenarioIds());
    }
}
