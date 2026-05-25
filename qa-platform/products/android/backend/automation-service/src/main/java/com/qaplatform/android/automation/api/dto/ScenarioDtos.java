package com.qaplatform.android.automation.api.dto;

import com.qaplatform.android.automation.domain.StepAction;
import com.qaplatform.android.automation.locator.LocatorStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public class ScenarioDtos {

    /* ── Scenario CRUD ─────────────────────────────────────────────── */

    public record CreateRequest(
            @NotBlank @Size(max = 255) String name,
            String description,
            List<@NotBlank String> tags,
            String preconditions
    ) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 255) String name,
            String description,
            List<@NotBlank String> tags,
            String preconditions
    ) {}

    /** Lightweight projection for the scenarios list. */
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

    /** Full scenario including ordered steps and the suites it currently belongs to. */
    public record View(
            long id,
            String name,
            String description,
            List<String> tags,
            String preconditions,
            int version,
            Instant createdAt,
            Instant updatedAt,
            List<StepView> steps,
            /**
             * Suites that reference this scenario. Editing or deleting the scenario propagates
             * to all of these — the UI uses this list to warn the user about the blast radius.
             */
            List<ParentSuiteRef> parentSuites
    ) {}

    /** Lightweight back-reference from a scenario to one of its containing suites. */
    public record ParentSuiteRef(
            long id,
            String name,
            List<String> tags
    ) {}

    /* ── Steps ─────────────────────────────────────────────────────── */

    public record StepCreateRequest(
            @NotNull StepAction action,
            Long targetElementId,
            Long dataId,
            String literalValue,
            /** Xray-style expected outcome — documentation, not executed. */
            String expectedResult,
            @Min(0) Integer timeoutMs,
            @Min(0) Integer retryCount,
            Boolean screenshotAfter,
            /** Insertion index within the target scope; null = append. */
            Integer position,
            /** Tree position. Both null = root level (legacy/flat). Both set
             *  = child inside an IF's "then" or "else" branch. */
            Long parentStepId,
            @jakarta.validation.constraints.Pattern(regexp = "then|else") String branchLabel,
            /** Predicate JSON. Required when action == IF, rejected otherwise. */
            @Valid com.qaplatform.android.automation.domain.StepCondition condition
    ) {}

    public record StepUpdateRequest(
            @NotNull StepAction action,
            Long targetElementId,
            Long dataId,
            String literalValue,
            String expectedResult,
            @Min(0) Integer timeoutMs,
            @Min(0) Integer retryCount,
            Boolean screenshotAfter,
            /** Update the IF predicate; ignored for non-IF actions. */
            @Valid com.qaplatform.android.automation.domain.StepCondition condition
    ) {}

    public record ReorderRequest(@NotNull @Valid List<@NotNull Long> stepIds) {}

    /**
     * Step view with denormalised element / data refs so the editor can render thumbnails
     * and labels without N+1 queries.
     *
     * <p>Recursive — {@link #children} is only populated for {@code IF} steps and
     * is the union of "then" + "else" branch members (each child carries its
     * own {@link #branchLabel}).</p>
     */
    public record StepView(
            long id,
            long scenarioId,
            int orderIndex,
            StepAction action,
            ElementRef targetElement,
            DataRef data,
            String literalValue,
            String expectedResult,
            int timeoutMs,
            int retryCount,
            boolean screenshotAfter,
            Long parentStepId,
            String branchLabel,
            com.qaplatform.android.automation.domain.StepCondition condition,
            List<StepView> children,
            Instant createdAt
    ) {}

    public record ElementRef(
            long id,
            String name,
            LocatorStrategy primaryStrategy,
            String primaryValue,
            /** Tiny base64 thumbnail captured at definition time — may be null. */
            String screenshotData
    ) {}

    public record DataRef(
            long id,
            String name,
            String environment,
            boolean sensitive
    ) {}
}
