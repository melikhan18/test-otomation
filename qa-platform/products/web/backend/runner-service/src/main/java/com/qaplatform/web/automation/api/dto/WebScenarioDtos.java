package com.qaplatform.web.automation.api.dto;

import com.qaplatform.web.automation.domain.WebStepAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public class WebScenarioDtos {

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
            int version,
            int stepCount,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record View(
            long id,
            String name,
            String description,
            List<String> tags,
            int version,
            Instant createdAt,
            Instant updatedAt,
            List<StepView> steps
    ) {}

    /* ── Steps ─────────────────────────────────────────────────────── */

    public record StepCreateRequest(
            @NotNull WebStepAction action,
            String selector,
            String value,
            /** Catalog ref — when set, takes precedence over `selector`. */
            Long targetElementId,
            /** Catalog ref — when set, takes precedence over `value`. */
            Long dataId,
            @Min(0) Integer timeoutMs,
            Boolean screenshotAfter,
            /** Insertion index (0-based). When null → append at end. */
            Integer position
    ) {}

    public record StepUpdateRequest(
            @NotNull WebStepAction action,
            String selector,
            String value,
            Long targetElementId,
            Long dataId,
            @Min(0) Integer timeoutMs,
            Boolean screenshotAfter
    ) {}

    public record ReorderRequest(@NotNull @Valid List<@NotNull Long> stepIds) {}

    public record StepView(
            long id,
            long scenarioId,
            int orderIndex,
            WebStepAction action,
            String selector,
            String value,
            Long targetElementId,
            Long dataId,
            int timeoutMs,
            boolean screenshotAfter,
            Instant createdAt
    ) {}
}
