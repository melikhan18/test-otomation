package com.devicefarm.auth.api;

import com.devicefarm.auth.api.dto.AuthDtos;
import com.devicefarm.auth.service.AuthService;
import com.devicefarm.common.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/login")
    public AuthDtos.LoginResponse login(@RequestBody @Valid AuthDtos.LoginRequest req) {
        return auth.login(req.username(), req.password());
    }

    @PostMapping("/refresh")
    public AuthDtos.LoginResponse refresh(@RequestBody @Valid AuthDtos.RefreshRequest req) {
        return auth.refresh(req.refreshToken());
    }

    @GetMapping("/me")
    public ResponseEntity<AuthDtos.CurrentUserResponse> me(@AuthenticationPrincipal JwtPrincipal principal) {
        if (principal == null || principal.userId() == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(new AuthDtos.CurrentUserResponse(
                principal.userId(), principal.subject(), principal.role(), principal.productId()));
    }
}
