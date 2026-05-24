package com.qaplatform.web.automation.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public class WebSuiteDtos {

    public record CreateRequest(
            @NotBlank @Size(max = 255) String name,
            String description,
            List<@NotBlank String> tags
    ) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 255) String name,
            String description,
            List<@NotBlank String> tags
    ) {}

    public record Summary(
            long id,
            String name,
            String description,
            List<String> tags,
            int scenarioCount,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record View(
            long id,
            String name,
            String description,
            List<String> tags,
            Instant createdAt,
            Instant updatedAt,
            List<ScenarioRef> scenarios
    ) {}

    public record ScenarioRef(
            long scenarioId,
            String name,
            String description,
            List<String> tags,
            int stepCount,
            int orderIndex
    ) {}

    /* ── Membership ops ─────────────────────────────────────────────── */

    public record AddScenarioRequest(@NotNull Long scenarioId) {}

    public record ReorderRequest(@NotNull @Valid List<@NotNull Long> scenarioIds) {}
}
