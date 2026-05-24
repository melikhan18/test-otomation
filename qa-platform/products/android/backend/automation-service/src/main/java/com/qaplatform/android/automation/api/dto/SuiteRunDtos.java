package com.qaplatform.android.automation.api.dto;

import com.qaplatform.common.runengine.status.RunStatus;
import com.qaplatform.common.runengine.status.SuiteRunStatus;
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
            Boolean adaptiveWait,
            /** APK version installed once per child run before its first step. */
            Long targetAppVersionId,
            /** Forwarded to each child run; defaults to true server-side when null. */
            Boolean resetHomeAfter,
            Boolean killProcessAfter
    ) {}

    /** Replace the full tag set on a suite-run. Server normalises + caps to 16 tags. */
    public record TagsRequest(List<String> tags) {}

    /** Lightweight row for the suite-runs list. */
    public record Summary(
            long id,
            long suiteId,
            String suiteName,
            Long deviceId,
            String environment,
            SuiteRunStatus status,
            int totalScenarios,
            int passedScenarios,
            int failedScenarios,
            Integer durationMs,
            List<String> tags,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt
    ) {}

    public record View(
            long id,
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
            List<String> tags,
            Instant createdAt,
            List<ChildRun> runs,
            /** ── Faz 4: target app + reset config ────────────────────────── */
            Long targetAppVersionId,
            RunDtos.TargetAppRef targetApp,
            boolean resetHomeAfter,
            boolean killProcessAfter
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
            Instant finishedAt,
            /** App prep outcome propagated up from the child run's row. */
            String appPrepStatus
    ) {}
}
