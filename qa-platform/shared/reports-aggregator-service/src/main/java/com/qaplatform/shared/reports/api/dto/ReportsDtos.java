package com.qaplatform.shared.reports.api.dto;

import com.qaplatform.common.runengine.status.RunStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ReportsDtos {

    /**
     * Push payload — what a platform stack sends when a run reaches a
     * terminal state. Idempotent on (platform, sourceRunId).
     */
    public record PushRunSummary(
            @NotNull @Pattern(regexp = "^(ANDROID|IOS|BACKEND|WEB)$",
                    message = "platform must be one of ANDROID|IOS|BACKEND|WEB")
            String platform,
            @NotNull Long sourceRunId,
            Long companyId,
            @NotNull Long projectId,
            @NotNull RunStatus status,
            String scenarioName,
            Long triggeredByUserId,
            @PositiveOrZero Integer totalSteps,
            @PositiveOrZero Integer passedSteps,
            @PositiveOrZero Integer failedSteps,
            Long durationMs,
            @NotNull Instant startedAt,
            Instant finishedAt,
            String errorSummary
    ) {}

    /** One row in the dashboard run feed. */
    public record RunSummaryView(
            long id,
            String platform,
            long sourceRunId,
            Long companyId,
            long projectId,
            RunStatus status,
            String scenarioName,
            Long triggeredByUserId,
            int totalSteps,
            int passedSteps,
            int failedSteps,
            Long durationMs,
            Instant startedAt,
            Instant finishedAt,
            String errorSummary,
            Instant receivedAt
    ) {}

    /**
     * Aggregate rollup for the dashboard "platform × status" widget.
     * Outer key is platform ("ANDROID"…), inner key is status name.
     */
    public record PlatformStatusSummary(
            long projectId,
            Instant since,
            Map<String, Map<String, Long>> byPlatformAndStatus
    ) {}

    /** Light wrapper used by GET /api/reports/runs. */
    public record RunSummaryList(
            long projectId,
            int limit,
            List<RunSummaryView> items
    ) {}
}
