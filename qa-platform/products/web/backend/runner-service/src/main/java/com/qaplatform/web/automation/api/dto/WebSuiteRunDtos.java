package com.qaplatform.web.automation.api.dto;

import com.qaplatform.common.runengine.status.RunStatus;
import com.qaplatform.common.runengine.status.SuiteRunStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public class WebSuiteRunDtos {

    public record CreateRequest(
            @NotNull Long suiteId,
            @NotBlank String browserProfileId,
            String environment
    ) {}

    public record Summary(
            long id,
            long suiteId,
            String suiteName,
            String browserProfileId,
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
            long suiteId,
            String suiteName,
            String browserProfileId,
            String environment,
            SuiteRunStatus status,
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
            String traceUrl,
            Instant startedAt,
            Instant finishedAt
    ) {}
}
