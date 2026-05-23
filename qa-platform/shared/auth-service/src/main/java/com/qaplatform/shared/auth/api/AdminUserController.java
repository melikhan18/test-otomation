package com.qaplatform.shared.auth.api;

import com.qaplatform.shared.auth.api.dto.AdminUserDtos;
import com.qaplatform.shared.auth.service.AdminUserService;
import com.qaplatform.common.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService service;
    public AdminUserController(AdminUserService service) { this.service = service; }

    @GetMapping
    public List<AdminUserDtos.View> list(@AuthenticationPrincipal JwtPrincipal caller) {
        return service.list(caller);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserDtos.View create(@AuthenticationPrincipal JwtPrincipal caller,
                                     @RequestBody @Valid AdminUserDtos.Create req) {
        return service.create(caller, req);
    }

    @PatchMapping("/{id}")
    public AdminUserDtos.View update(@AuthenticationPrincipal JwtPrincipal caller,
                                     @PathVariable long id,
                                     @RequestBody @Valid AdminUserDtos.Update req) {
        return service.update(caller, id, req);
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@AuthenticationPrincipal JwtPrincipal caller,
                              @PathVariable long id,
                              @RequestBody @Valid AdminUserDtos.PasswordReset req) {
        service.resetPassword(caller, id, req);
    }
}
