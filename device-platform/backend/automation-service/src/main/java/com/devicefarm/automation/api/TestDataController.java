package com.devicefarm.automation.api;

import com.devicefarm.automation.api.dto.TestDataDtos;
import com.devicefarm.automation.service.TestDataService;
import com.devicefarm.common.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/automation/test-data")
public class TestDataController {

    private final TestDataService service;

    public TestDataController(TestDataService service) { this.service = service; }

    @GetMapping
    public List<TestDataDtos.View> list(@AuthenticationPrincipal JwtPrincipal caller,
                                        @RequestParam(value = "environment", required = false) String environment,
                                        @RequestParam(value = "reveal", defaultValue = "false") boolean reveal) {
        return service.list(caller, environment, reveal);
    }

    @GetMapping("/environments")
    public List<String> environments(@AuthenticationPrincipal JwtPrincipal caller) {
        return service.environments(caller);
    }

    @GetMapping("/{id}")
    public TestDataDtos.View get(@AuthenticationPrincipal JwtPrincipal caller,
                                 @PathVariable long id,
                                 @RequestParam(value = "reveal", defaultValue = "false") boolean reveal) {
        return service.get(caller, id, reveal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TestDataDtos.View create(@AuthenticationPrincipal JwtPrincipal caller,
                                    @RequestBody @Valid TestDataDtos.CreateRequest req) {
        return service.create(caller, req);
    }

    @PutMapping("/{id}")
    public TestDataDtos.View update(@AuthenticationPrincipal JwtPrincipal caller,
                                    @PathVariable long id,
                                    @RequestBody @Valid TestDataDtos.UpdateRequest req) {
        return service.update(caller, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal JwtPrincipal caller, @PathVariable long id) {
        service.delete(caller, id);
    }
}
