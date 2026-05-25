package com.qaplatform.web.automation.api.dto;

import com.qaplatform.web.automation.domain.WebStepAction;
import com.qaplatform.web.automation.domain.WebStepCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
            /**
             * Root-level steps of the scenario. Each step's {@link StepView#children}
             * holds nested children (only populated for {@code action = IF}); the
             * frontend renders the tree recursively. Empty for scenarios with no
             * conditional blocks.
             */
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
            /**
             * Insertion index (0-based) within the target branch / root. Null →
             * append at the end of that scope.
             */
            Integer position,
            /**
             * Tree-position: which IF step this new row should hang under, and
             * inside which branch. Both null = root-level (the original behaviour
             * for flat scenarios).
             */
            Long parentStepId,
            @Pattern(regexp = "then|else") String branchLabel,
            /**
             * Predicate JSON; only valid (and required) when {@link #action} is
             * {@link WebStepAction#IF}. Service will reject mismatches.
             */
            @Valid WebStepCondition condition
    ) {}

    public record StepUpdateRequest(
            @NotNull WebStepAction action,
            String selector,
            String value,
            Long targetElementId,
            Long dataId,
            @Min(0) Integer timeoutMs,
            Boolean screenshotAfter,
            /** Update the IF predicate. Ignored for non-IF actions. */
            @Valid WebStepCondition condition
    ) {}

    public record ReorderRequest(@NotNull @Valid List<@NotNull Long> stepIds) {}

    /**
     * Recursive — {@link #children} is only populated for {@code IF} steps,
     * and itself is the union of "then" + "else" branch members (each child
     * carries its own {@link #branchLabel} so the renderer can split them
     * into two lanes).
     */
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
            Long parentStepId,
            String branchLabel,
            WebStepCondition condition,
            List<StepView> children,
            Instant createdAt
    ) {}
}
