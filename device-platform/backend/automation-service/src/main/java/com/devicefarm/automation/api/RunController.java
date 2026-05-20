package com.devicefarm.automation.api;

import com.devicefarm.automation.api.dto.RunDtos;
import com.devicefarm.automation.service.RunService;
import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/automation/runs")
public class RunController {

    private final RunService service;

    public RunController(RunService service) { this.service = service; }

    @GetMapping
    public List<RunDtos.Summary> list(@AuthenticationPrincipal JwtPrincipal caller,
                                      @RequestParam(value = "scenarioId", required = false) Long scenarioId) {
        return service.list(caller, scenarioId);
    }

    @GetMapping("/{id}")
    public RunDtos.View get(@AuthenticationPrincipal JwtPrincipal caller, @PathVariable long id) {
        return service.get(caller, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunDtos.View create(@AuthenticationPrincipal JwtPrincipal caller,
                               @RequestBody @Valid RunDtos.CreateRequest req,
                               HttpServletRequest http) {
        String header = http.getHeader("Authorization");
        String jwt = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        if (jwt == null) throw ApiException.unauthorized("missing Authorization header");
        return service.create(caller, req, jwt);
    }
}
