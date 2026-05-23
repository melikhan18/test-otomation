package com.qaplatform.android.session.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class SessionDtos {
    public record CreateSessionRequest(@NotNull Long deviceId) {}
    public record SessionView(long id, long deviceId, long userId, long productId,
                              String status, Instant createdAt, Instant endedAt,
                              String sessionToken, Instant expiresAt) {}
}
