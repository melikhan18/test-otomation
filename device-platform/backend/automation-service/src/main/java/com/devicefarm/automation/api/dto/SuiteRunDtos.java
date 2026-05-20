package com.devicefarm.automation.api.dto;

import com.devicefarm.automation.domain.RunStatus;
import com.devicefarm.automation.domain.SuiteRunStatus;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public class SuiteRunDtos {

    public record CreateRequest(
            @NotNull Long suiteId,
            @NotNull Long deviceId,
            String environment,
            /** Forwarded to each child run (sleep between steps; null = server default). */
            Integer interStepDelayMs,
            /** Forwarded to each child run. */
            Boolean adaptiveWait
    ) {}

    /** Lightweight row for the suite-runs list. */
    public record Summary(
            long id,
            long productId,
            long suiteId,
            String suiteName,
            Long deviceId,
            String environment,
            SuiteRunStatus status,
            int totalScenarios,
            int passedScenarios,
            int failedScenarios,
            Integer durationMs,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt
    ) {}

    public record View(
            long id,
            long productId,
            long suiteId,
            String suiteName,
            Long deviceId,
            String environment,
            SuiteRunStatus status,
            String triggerType,
            long triggeredByUserId,
            Instant startedAt,
            Instant finishedAt,
            Integer durationMs,
            int totalScenarios,
            int passedScenarios,
            int failedScenarios,
            String errorSummary,
            Instant createdAt,
            List<ChildRun> runs
    ) {}

    /** One child run as it appears under the suite-run aggregation. */
    public record ChildRun(
            long id,
            Long scenarioId,
            String scenarioName,
            RunStatus status,
            int totalSteps,
            int passedSteps,
            int failedSteps,
            Integer durationMs,
            String videoUrl,
            Instant startedAt,
            Instant finishedAt
    ) {}
}
