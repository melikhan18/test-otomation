package com.qaplatform.web.automation.api;

import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.web.automation.api.dto.WebRunDtos;
import com.qaplatform.web.automation.service.WebRunService;
import com.qaplatform.web.automation.tenancy.ProjectContext;
import com.qaplatform.web.automation.tenancy.TenancyGuard;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/web/runs")
public class WebRunController {

    private final TenancyGuard guard;
    private final WebRunService service;

    public WebRunController(TenancyGuard guard, WebRunService service) {
        this.guard = guard;
        this.service = service;
    }

    @GetMapping
    public List<WebRunDtos.Summary> list(@AuthenticationPrincipal JwtPrincipal caller,
                                         @RequestHeader("X-Project-Id") Long projectId,
                                         @RequestParam(required = false) Long scenarioId) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.list(caller, ctx, scenarioId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebRunDtos.View create(@AuthenticationPrincipal JwtPrincipal caller,
                                  @RequestHeader("X-Project-Id") Long projectId,
                                  @RequestBody @Valid WebRunDtos.CreateRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.create(caller, ctx, req);
    }

    @GetMapping("/{id}")
    public WebRunDtos.View get(@AuthenticationPrincipal JwtPrincipal caller,
                               @RequestHeader("X-Project-Id") Long projectId,
                               @PathVariable long id) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.get(caller, ctx, id);
    }
}
