package com.devicefarm.automation.api;

import com.devicefarm.automation.api.dto.ScenarioDtos;
import com.devicefarm.automation.service.ScenarioService;
import com.devicefarm.common.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/automation/scenarios")
public class ScenarioController {

    private final ScenarioService service;

    public ScenarioController(ScenarioService service) { this.service = service; }

    /* ── Scenario CRUD ────────────────────────────────────────────────── */

    @GetMapping
    public List<ScenarioDtos.Summary> list(@AuthenticationPrincipal JwtPrincipal caller) {
        return service.list(caller);
    }

    @GetMapping("/{id}")
    public ScenarioDtos.View get(@AuthenticationPrincipal JwtPrincipal caller, @PathVariable long id) {
        return service.get(caller, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScenarioDtos.View create(@AuthenticationPrincipal JwtPrincipal caller,
                                    @RequestBody @Valid ScenarioDtos.CreateRequest req) {
        return service.create(caller, req);
    }

    @PutMapping("/{id}")
    public ScenarioDtos.View update(@AuthenticationPrincipal JwtPrincipal caller,
                                    @PathVariable long id,
                                    @RequestBody @Valid ScenarioDtos.UpdateRequest req) {
        return service.update(caller, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal JwtPrincipal caller, @PathVariable long id) {
        service.delete(caller, id);
    }

    /* ── Steps under a scenario ───────────────────────────────────────── */

    @PostMapping("/{id}/steps")
    @ResponseStatus(HttpStatus.CREATED)
    public ScenarioDtos.View addStep(@AuthenticationPrincipal JwtPrincipal caller,
                                     @PathVariable long id,
                                     @RequestBody @Valid ScenarioDtos.StepCreateRequest req) {
        return service.addStep(caller, id, req);
    }

    @PutMapping("/{id}/steps/{stepId}")
    public ScenarioDtos.View updateStep(@AuthenticationPrincipal JwtPrincipal caller,
                                        @PathVariable long id,
                                        @PathVariable long stepId,
                                        @RequestBody @Valid ScenarioDtos.StepUpdateRequest req) {
        return service.updateStep(caller, id, stepId, req);
    }

    @DeleteMapping("/{id}/steps/{stepId}")
    public ScenarioDtos.View deleteStep(@AuthenticationPrincipal JwtPrincipal caller,
                                        @PathVariable long id,
                                        @PathVariable long stepId) {
        return service.deleteStep(caller, id, stepId);
    }

    @PutMapping("/{id}/steps/reorder")
    public ScenarioDtos.View reorderSteps(@AuthenticationPrincipal JwtPrincipal caller,
                                          @PathVariable long id,
                                          @RequestBody @Valid ScenarioDtos.ReorderRequest req) {
        return service.reorderSteps(caller, id, req);
    }
}
