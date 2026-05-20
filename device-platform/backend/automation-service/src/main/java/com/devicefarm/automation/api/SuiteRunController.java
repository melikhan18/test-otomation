package com.devicefarm.automation.api;

import com.devicefarm.automation.api.dto.SuiteRunDtos;
import com.devicefarm.automation.service.SuiteRunService;
import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/automation/suite-runs")
public class SuiteRunController {

    private final SuiteRunService service;

    public SuiteRunController(SuiteRunService service) { this.service = service; }

    @GetMapping
    public List<SuiteRunDtos.Summary> list(@AuthenticationPrincipal JwtPrincipal caller,
                                           @RequestParam(value = "suiteId", required = false) Long suiteId) {
        return service.list(caller, suiteId);
    }

    @GetMapping("/{id}")
    public SuiteRunDtos.View get(@AuthenticationPrincipal JwtPrincipal caller, @PathVariable long id) {
        return service.get(caller, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SuiteRunDtos.View create(@AuthenticationPrincipal JwtPrincipal caller,
                                    @RequestBody @Valid SuiteRunDtos.CreateRequest req,
                                    HttpServletRequest http) {
        String header = http.getHeader("Authorization");
        String jwt = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        if (jwt == null) throw ApiException.unauthorized("missing Authorization header");
        return service.create(caller, req, jwt);
    }

    @PatchMapping("/{id}/tags")
    public SuiteRunDtos.View updateTags(@AuthenticationPrincipal JwtPrincipal caller,
                                        @PathVariable long id,
                                        @RequestBody SuiteRunDtos.TagsRequest req) {
        return service.updateTags(caller, id, req.tags());
    }
}
