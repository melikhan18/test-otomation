package com.qaplatform.web.automation.api;

import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.web.automation.api.dto.WebSuiteRunDtos;
import com.qaplatform.web.automation.service.WebSuiteRunService;
import com.qaplatform.web.automation.tenancy.ProjectContext;
import com.qaplatform.web.automation.tenancy.TenancyGuard;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/web/suite-runs")
public class WebSuiteRunController {

    private final TenancyGuard guard;
    private final WebSuiteRunService service;

    public WebSuiteRunController(TenancyGuard guard, WebSuiteRunService service) {
        this.guard = guard;
        this.service = service;
    }

    @GetMapping
    public List<WebSuiteRunDtos.Summary> list(@AuthenticationPrincipal JwtPrincipal caller,
                                              @RequestHeader("X-Project-Id") Long projectId,
                                              @RequestParam(required = false) Long suiteId) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.list(caller, ctx, suiteId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebSuiteRunDtos.View create(@AuthenticationPrincipal JwtPrincipal caller,
                                       @RequestHeader("X-Project-Id") Long projectId,
                                       @RequestBody @Valid WebSuiteRunDtos.CreateRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.create(caller, ctx, req);
    }

    @GetMapping("/{id}")
    public WebSuiteRunDtos.View get(@AuthenticationPrincipal JwtPrincipal caller,
                                    @RequestHeader("X-Project-Id") Long projectId,
                                    @PathVariable long id) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.get(caller, ctx, id);
    }
}
