package com.devicefarm.session.api;

import com.devicefarm.common.jwt.JwtPrincipal;
import com.devicefarm.session.api.dto.SessionDtos;
import com.devicefarm.session.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService service;

    public SessionController(SessionService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionDtos.SessionView create(@AuthenticationPrincipal JwtPrincipal caller,
                                          @RequestBody @Valid SessionDtos.CreateSessionRequest req) {
        return service.create(caller, req.deviceId());
    }

    @GetMapping("/{id}")
    public SessionDtos.SessionView get(@AuthenticationPrincipal JwtPrincipal caller, @PathVariable long id) {
        return service.get(caller, id);
    }

    @PostMapping("/{id}/touch")
    public SessionDtos.SessionView touch(@AuthenticationPrincipal JwtPrincipal caller, @PathVariable long id) {
        return service.touch(caller, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void end(@AuthenticationPrincipal JwtPrincipal caller, @PathVariable long id) {
        service.end(caller, id);
    }

    @GetMapping("/active")
    public List<SessionDtos.SessionView> active(@AuthenticationPrincipal JwtPrincipal caller) {
        return service.activeForCaller(caller);
    }
}
