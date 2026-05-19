package com.devicefarm.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public class AuthDtos {
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(String accessToken, String refreshToken, long expiresIn,
                                long userId, String username, String role, long productId) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record CurrentUserResponse(long userId, String username, String role, long productId) {}
}
