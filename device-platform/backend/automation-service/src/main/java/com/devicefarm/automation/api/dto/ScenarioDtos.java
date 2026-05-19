package com.devicefarm.automation.api.dto;

import com.devicefarm.automation.domain.StepAction;
import com.devicefarm.automation.locator.LocatorStrategy;
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
            long productId,
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
            long productId,
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
            /** Insertion index (0-based). When null → append at end. */
            Integer position
    ) {}

    public record StepUpdateRequest(
            @NotNull StepAction action,
            Long targetElementId,
            Long dataId,
            String literalValue,
            String expectedResult,
            @Min(0) Integer timeoutMs,
            @Min(0) Integer retryCount,
            Boolean screenshotAfter
    ) {}

    public record ReorderRequest(@NotNull @Valid List<@NotNull Long> stepIds) {}

    /**
     * Step view with denormalised element / data refs so the editor can render thumbnails
     * and labels without N+1 queries.
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
