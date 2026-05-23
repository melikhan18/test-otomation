package com.qaplatform.android.automation.api;

import com.qaplatform.android.automation.api.dto.ElementDtos;
import com.qaplatform.android.automation.service.ElementService;
import com.qaplatform.android.automation.tenancy.TenancyGuard;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.common.tenancy.TenancyHeaders;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/automation/elements")
public class ElementController {

    private final ElementService service;
    private final TenancyGuard guard;

    public ElementController(ElementService service, TenancyGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public List<ElementDtos.View> list(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @RequestParam(value = "q", required = false) String q) {
        return service.list(caller, guard.requireProject(caller, projectId), q);
    }

    @GetMapping("/{id}")
    public ElementDtos.View get(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id) {
        return service.get(caller, guard.requireProject(caller, projectId), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ElementDtos.View create(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @RequestBody @Valid ElementDtos.CreateRequest req) {
        return service.create(caller, guard.requireProject(caller, projectId), req);
    }

    @PutMapping("/{id}")
    public ElementDtos.View update(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id,
            @RequestBody @Valid ElementDtos.UpdateRequest req) {
        return service.update(caller, guard.requireProject(caller, projectId), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id) {
        service.delete(caller, guard.requireProject(caller, projectId), id);
    }
}
