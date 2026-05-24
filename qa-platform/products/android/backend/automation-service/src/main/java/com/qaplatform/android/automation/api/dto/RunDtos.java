package com.qaplatform.android.automation.api.dto;

import com.qaplatform.android.automation.domain.StepAction;
import com.qaplatform.common.runengine.status.RunStatus;
import com.qaplatform.common.runengine.status.StepResultStatus;
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
            Boolean adaptiveWait,
            /** APK version to install/launch before the first step. Null = skip app prep. */
            Long targetAppVersionId,
            /** Press HOME after the run finishes. Defaults to true server-side when null. */
            Boolean resetHomeAfter,
            /** Force-stop the target app on reset (Device Owner cihazda effective). */
            Boolean killProcessAfter
    ) {}

    /** Replace the full tag set on a report. Server normalises + caps to 16 tags. */
    public record TagsRequest(List<String> tags) {}

    /** Lightweight row for the runs list. */
    public record Summary(
            long id,
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
            Instant finishedAt,
            /** APK that was prepared on the device, if any. Null = no app target. */
            Long targetAppVersionId,
            /** Outcome of the prep phase (NOT_REQUESTED / ALREADY_LATEST / INSTALLED / UPDATED / FAILED). */
            String appPrepStatus
    ) {}

    public record View(
            long id,
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
            List<StepResultView> stepResults,
            /** ── Faz 4: target app + prep + reset ──────────────────────────── */
            Long targetAppVersionId,
            /** Denormalised so the UI doesn't N+1 to apps/versions for a run view. */
            TargetAppRef targetApp,
            String appPrepStatus,
            Integer appPrepDurationMs,
            String appPrepError,
            boolean resetHomeAfter,
            boolean killProcessAfter
    ) {}

    /** Compact app + version reference embedded in run views. */
    public record TargetAppRef(
            long appId,
            String packageName,
            String displayName,
            long versionId,
            long versionCode,
            String versionName
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
