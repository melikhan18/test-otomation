package com.qaplatform.android.automation.api;

import com.qaplatform.android.automation.api.dto.TestDataDtos;
import com.qaplatform.android.automation.service.TestDataService;
import com.qaplatform.android.automation.tenancy.TenancyGuard;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.common.tenancy.TenancyHeaders;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/automation/test-data")
public class TestDataController {

    private final TestDataService service;
    private final TenancyGuard guard;

    public TestDataController(TestDataService service, TenancyGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public List<TestDataDtos.View> list(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @RequestParam(value = "environment", required = false) String environment,
            @RequestParam(value = "reveal", defaultValue = "false") boolean reveal) {
        return service.list(caller, guard.requireProject(caller, projectId), environment, reveal);
    }

    @GetMapping("/environments")
    public List<String> environments(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId) {
        return service.environments(caller, guard.requireProject(caller, projectId));
    }

    @GetMapping("/{id}")
    public TestDataDtos.View get(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id,
            @RequestParam(value = "reveal", defaultValue = "false") boolean reveal) {
        return service.get(caller, guard.requireProject(caller, projectId), id, reveal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TestDataDtos.View create(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @RequestBody @Valid TestDataDtos.CreateRequest req) {
        return service.create(caller, guard.requireProject(caller, projectId), req);
    }

    @PutMapping("/{id}")
    public TestDataDtos.View update(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestHeader(name = TenancyHeaders.PROJECT_ID) long projectId,
            @PathVariable long id,
            @RequestBody @Valid TestDataDtos.UpdateRequest req) {
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
