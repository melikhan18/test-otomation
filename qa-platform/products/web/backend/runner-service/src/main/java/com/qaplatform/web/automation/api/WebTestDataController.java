package com.qaplatform.web.automation.api;

import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.web.automation.api.dto.WebTestDataDtos;
import com.qaplatform.web.automation.service.WebTestDataService;
import com.qaplatform.web.automation.tenancy.ProjectContext;
import com.qaplatform.web.automation.tenancy.TenancyGuard;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/web/test-data")
public class WebTestDataController {

    private final TenancyGuard guard;
    private final WebTestDataService service;

    public WebTestDataController(TenancyGuard guard, WebTestDataService service) {
        this.guard = guard;
        this.service = service;
    }

    @GetMapping
    public List<WebTestDataDtos.View> list(@AuthenticationPrincipal JwtPrincipal caller,
                                           @RequestHeader("X-Project-Id") Long projectId) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.list(caller, ctx);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebTestDataDtos.View create(@AuthenticationPrincipal JwtPrincipal caller,
                                       @RequestHeader("X-Project-Id") Long projectId,
                                       @RequestBody @Valid WebTestDataDtos.CreateRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.create(caller, ctx, req);
    }

    @GetMapping("/{id}")
    public WebTestDataDtos.View get(@AuthenticationPrincipal JwtPrincipal caller,
                                    @RequestHeader("X-Project-Id") Long projectId,
                                    @PathVariable long id,
                                    @RequestParam(defaultValue = "false") boolean reveal) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.get(caller, ctx, id, reveal);
    }

    @PutMapping("/{id}")
    public WebTestDataDtos.View update(@AuthenticationPrincipal JwtPrincipal caller,
                                       @RequestHeader("X-Project-Id") Long projectId,
                                       @PathVariable long id,
                                       @RequestBody @Valid WebTestDataDtos.UpdateRequest req) {
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

    @GetMapping("/environments")
    public List<String> environments(@AuthenticationPrincipal JwtPrincipal caller,
                                     @RequestHeader("X-Project-Id") Long projectId) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.environments(caller, ctx);
    }
}
