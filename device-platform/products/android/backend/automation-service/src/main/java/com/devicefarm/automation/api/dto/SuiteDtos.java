package com.devicefarm.automation.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public class SuiteDtos {

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

    /** Lightweight row for the suites list. */
    public record Summary(
            long id,
            long productId,
            String name,
            String description,
            List<String> tags,
            int scenarioCount,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /** Full suite view including ordered scenario references. */
    public record View(
            long id,
            long productId,
            String name,
            String description,
            List<String> tags,
            Instant createdAt,
            Instant updatedAt,
            List<ScenarioRef> scenarios
    ) {}

    /**
     * Denormalised scenario reference for the suite detail view — lets the UI render the
     * scenario name + step count without a join.
     */
    public record ScenarioRef(
            long scenarioId,
            String name,
            String description,
            List<String> tags,
            int stepCount,
            int orderIndex
    ) {}

    /* ── Membership ops ────────────────────────────────────────────── */

    public record AddScenarioRequest(@NotNull Long scenarioId) {}

    public record ReorderRequest(@NotNull @Valid List<@NotNull Long> scenarioIds) {}
}
