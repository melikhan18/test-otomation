package com.devicefarm.automation.api.dto;

import com.devicefarm.automation.domain.RunStatus;
import com.devicefarm.automation.domain.StepAction;
import com.devicefarm.automation.domain.StepResultStatus;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public class RunDtos {

    public record CreateRequest(
            @NotNull Long scenarioId,
            @NotNull Long deviceId,
            /** Resolves test-data references when the scenario is parameterised. */
            String environment,
            /** Sleep applied between every step; null = server default. Clamped to [0, 30000]. */
            Integer interStepDelayMs,
            /** When true, replace fixed delay with poll-until-stable (caps at 5s). */
            Boolean adaptiveWait
    ) {}

    /** Replace the full tag set on a report. Server normalises + caps to 16 tags. */
    public record TagsRequest(List<String> tags) {}

    /** Lightweight row for the runs list. */
    public record Summary(
            long id,
            long productId,
            Long scenarioId,
            String scenarioName,
            Long deviceId,
            String environment,
            RunStatus status,
            int totalSteps,
            int passedSteps,
            int failedSteps,
            Integer durationMs,
            String videoUrl,
            Long suiteRunId,
            List<String> tags,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt
    ) {}

    public record View(
            long id,
            long productId,
            Long scenarioId,
            String scenarioName,
            Integer scenarioVersion,
            Long deviceId,
            Long sessionId,
            String environment,
            RunStatus status,
            String triggerType,
            long triggeredByUserId,
            Instant startedAt,
            Instant finishedAt,
            Integer durationMs,
            int totalSteps,
            int passedSteps,
            int failedSteps,
            String errorSummary,
            int interStepDelayMs,
            boolean adaptiveWait,
            String videoUrl,
            List<String> tags,
            Instant createdAt,
            List<StepResultView> stepResults
    ) {}

    public record StepResultView(
            long id,
            Long stepId,
            int orderIndex,
            StepAction action,
            StepResultStatus status,
            Instant startedAt,
            Instant finishedAt,
            Integer durationMs,
            String errorMessage,
            String screenshotUrl,
            String resolvedLocator,
            int retriesUsed
    ) {}
}
