package com.devicefarm.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public class AdminUserDtos {

    public record View(
            long id,
            String username,
            String email,
            String role,
            boolean platformAdmin,
            boolean enabled,
            Instant createdAt,
            int companyCount
    ) {}

    public record Create(
            @NotBlank @Size(min = 3, max = 64) String username,
            @Email String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            boolean platformAdmin
    ) {}

    public record Update(
            @Email String email,
            Boolean enabled,
            Boolean platformAdmin
    ) {}

    public record PasswordReset(
            @NotBlank @Size(min = 8, max = 128) String newPassword
    ) {}
}
