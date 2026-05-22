package com.devicefarm.automation.api;

import com.devicefarm.automation.api.dto.AppDtos;
import com.devicefarm.automation.service.AppService;
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
@RequestMapping("/api/automation/apps")
public class AppController {

    private final AppService service;
    private final TenancyGuard guard;

    public AppController(AppService service, TenancyGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public List<AppDtos.Summary> list(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.list(ctx);
    }

    @GetMapping("/{id}")
    public AppDtos.View get(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.get(ctx, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppDtos.View create(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @RequestBody @Valid AppDtos.CreateRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.create(caller, ctx, req);
    }

    @PutMapping("/{id}")
    public AppDtos.View update(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id,
            @RequestBody @Valid AppDtos.UpdateRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.update(ctx, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        service.archive(ctx, id);
    }
}
