package com.devicefarm.automation.api;

import com.devicefarm.automation.api.dto.SuiteDtos;
import com.devicefarm.automation.service.SuiteService;
import com.devicefarm.common.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/automation/suites")
public class SuiteController {

    private final SuiteService service;

    public SuiteController(SuiteService service) { this.service = service; }

    /* ── Suite CRUD ───────────────────────────────────────────────────── */

    @GetMapping
    public List<SuiteDtos.Summary> list(@AuthenticationPrincipal JwtPrincipal caller) {
        return service.list(caller);
    }

    @GetMapping("/{id}")
    public SuiteDtos.View get(@AuthenticationPrincipal JwtPrincipal caller, @PathVariable long id) {
        return service.get(caller, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuiteDtos.View create(@AuthenticationPrincipal JwtPrincipal caller,
                                 @RequestBody @Valid SuiteDtos.CreateRequest req) {
        return service.create(caller, req);
    }

    @PutMapping("/{id}")
    public SuiteDtos.View update(@AuthenticationPrincipal JwtPrincipal caller,
                                 @PathVariable long id,
                                 @RequestBody @Valid SuiteDtos.UpdateRequest req) {
        return service.update(caller, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal JwtPrincipal caller, @PathVariable long id) {
        service.delete(caller, id);
    }

    /* ── Scenario membership ─────────────────────────────────────────── */

    @PostMapping("/{id}/scenarios")
    public SuiteDtos.View addScenario(@AuthenticationPrincipal JwtPrincipal caller,
                                      @PathVariable long id,
                                      @RequestBody @Valid SuiteDtos.AddScenarioRequest req) {
        return service.addScenario(caller, id, req);
    }

    @DeleteMapping("/{id}/scenarios/{scenarioId}")
    public SuiteDtos.View removeScenario(@AuthenticationPrincipal JwtPrincipal caller,
                                         @PathVariable long id,
                                         @PathVariable long scenarioId) {
        return service.removeScenario(caller, id, scenarioId);
    }

    @PutMapping("/{id}/scenarios/reorder")
    public SuiteDtos.View reorderScenarios(@AuthenticationPrincipal JwtPrincipal caller,
                                           @PathVariable long id,
                                           @RequestBody @Valid SuiteDtos.ReorderRequest req) {
        return service.reorderScenarios(caller, id, req);
    }
}
