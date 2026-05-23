package com.devicefarm.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class NotificationDtos {

    public record View(
            long id,
            String type,
            String status,
            Map<String, Object> payload,
            Long actorUserId,
            String actorUsername,
            Instant createdAt,
            Instant resolvedAt,
            Instant expiresAt
    ) {}

    public record UnreadCount(long count) {}

    /**
     * Body for invite-by-email. The recipient is identified by {@code email};
     * either we make them company OWNER ({@code owner=true}) or attach a list
     * of per-project grants ({@code grants}). One of the two must be set.
     */
    public record InviteByEmailRequest(
            @Email @NotBlank String email,
            boolean owner,
            List<TenancyDtos.ProjectGrantInput> grants
    ) {}
}
