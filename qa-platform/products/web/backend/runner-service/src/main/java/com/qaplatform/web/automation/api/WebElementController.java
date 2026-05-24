package com.qaplatform.web.automation.api;

import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.web.automation.api.dto.WebElementDtos;
import com.qaplatform.web.automation.service.WebElementService;
import com.qaplatform.web.automation.tenancy.ProjectContext;
import com.qaplatform.web.automation.tenancy.TenancyGuard;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/web/elements")
public class WebElementController {

    private final TenancyGuard guard;
    private final WebElementService service;

    public WebElementController(TenancyGuard guard, WebElementService service) {
        this.guard = guard;
        this.service = service;
    }

    @GetMapping
    public List<WebElementDtos.View> list(@AuthenticationPrincipal JwtPrincipal caller,
                                          @RequestHeader("X-Project-Id") Long projectId) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.list(caller, ctx);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebElementDtos.View create(@AuthenticationPrincipal JwtPrincipal caller,
                                      @RequestHeader("X-Project-Id") Long projectId,
                                      @RequestBody @Valid WebElementDtos.CreateRequest req) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.create(caller, ctx, req);
    }

    @GetMapping("/{id}")
    public WebElementDtos.View get(@AuthenticationPrincipal JwtPrincipal caller,
                                   @RequestHeader("X-Project-Id") Long projectId,
                                   @PathVariable long id) {
        ProjectContext ctx = guard.requireProject(caller, projectId);
        return service.get(caller, ctx, id);
    }

    @PutMapping("/{id}")
    public WebElementDtos.View update(@AuthenticationPrincipal JwtPrincipal caller,
                                      @RequestHeader("X-Project-Id") Long projectId,
                                      @PathVariable long id,
                                      @RequestBody @Valid WebElementDtos.UpdateRequest req) {
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
}
