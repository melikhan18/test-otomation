package com.qaplatform.web.automation.api.dto;

import com.qaplatform.common.runengine.status.RunStatus;
import com.qaplatform.common.runengine.status.StepResultStatus;
import com.qaplatform.web.automation.domain.WebStepAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public class WebRunDtos {

    public record CreateRequest(
            @NotNull Long scenarioId,
            /** Profile id from the BrowserCatalog (e.g. "desktop-chrome-1080p"). */
            @NotBlank String browserProfileId,
            String environment
    ) {}

    public record Summary(
            long id,
            Long scenarioId,
            String scenarioName,
            String browserProfileId,
            String environment,
            RunStatus status,
            int totalSteps,
            int passedSteps,
            int failedSteps,
            Integer durationMs,
            String videoUrl,
            String traceUrl,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt
    ) {}

    public record View(
            long id,
            Long scenarioId,
            String scenarioName,
            Integer scenarioVersion,
            String browserProfileId,
            String environment,
            RunStatus status,
            long triggeredByUserId,
            Instant startedAt,
            Instant finishedAt,
            Integer durationMs,
            int totalSteps,
            int passedSteps,
            int failedSteps,
            String errorSummary,
            String videoUrl,
            String traceUrl,
            Instant createdAt,
            List<StepResultView> stepResults
    ) {}

    public record StepResultView(
            long id,
            Long stepId,
            int orderIndex,
            WebStepAction action,
            StepResultStatus status,
            Instant startedAt,
            Instant finishedAt,
            Integer durationMs,
            String errorMessage,
            String screenshotUrl
    ) {}
}
