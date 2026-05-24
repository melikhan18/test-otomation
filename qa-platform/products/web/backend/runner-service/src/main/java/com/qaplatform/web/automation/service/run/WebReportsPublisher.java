package com.qaplatform.web.automation.service.run;

import com.qaplatform.common.jwt.JwtTokenService;
import com.qaplatform.web.automation.domain.WebRunEntity;
import com.qaplatform.web.automation.domain.WebRunRepository;
import com.qaplatform.web.automation.domain.WebScenarioEntity;
import com.qaplatform.web.automation.domain.WebScenarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Fire-and-forget POST of terminal web run summaries to
 * reports-aggregator-service. Same shape as Android's
 * {@code ReportsPublisher} — sets {@code platform="WEB"} so the
 * cross-platform dashboard groups runs by stack.
 */
@Component
public class WebReportsPublisher {

    private static final Logger log = LoggerFactory.getLogger(WebReportsPublisher.class);
    private static final String PLATFORM = "WEB";

    private final WebRunRepository runs;
    private final WebScenarioRepository scenarios;
    private final JwtTokenService tokens;
    private final RestClient http;

    public WebReportsPublisher(WebRunRepository runs,
                               WebScenarioRepository scenarios,
                               JwtTokenService tokens,
                               @Value("${app.services.reports.url:http://localhost:8090}") String baseUrl) {
        this.runs = runs;
        this.scenarios = scenarios;
        this.tokens = tokens;
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Async
    public void publishAsync(long runId) {
        try {
            WebRunEntity run = runs.findById(runId).orElse(null);
            if (run == null) {
                log.warn("WebReportsPublisher: run {} not found, skipping push", runId);
                return;
            }
            String scenarioName = run.getScenarioId() == null ? null
                    : scenarios.findById(run.getScenarioId()).map(WebScenarioEntity::getName).orElse(null);

            Map<String, Object> payload = new HashMap<>();
            payload.put("platform", PLATFORM);
            payload.put("sourceRunId", run.getId());
            payload.put("projectId", run.getProjectId());
            payload.put("status", run.getStatus().name());
            if (scenarioName != null) payload.put("scenarioName", scenarioName);
            payload.put("triggeredByUserId", run.getTriggeredByUserId());
            payload.put("totalSteps", run.getTotalSteps());
            payload.put("passedSteps", run.getPassedSteps());
            payload.put("failedSteps", run.getFailedSteps());
            if (run.getDurationMs() != null) payload.put("durationMs", run.getDurationMs().longValue());
            payload.put("startedAt", run.getStartedAt() == null ? run.getCreatedAt() : run.getStartedAt());
            payload.put("finishedAt", run.getFinishedAt());
            if (run.getErrorSummary() != null) payload.put("errorSummary", run.getErrorSummary());

            String token = tokens.issueServiceToken("web-runner");
            http.post()
                    .uri("/api/reports/runs")
                    .headers(h -> h.setBearerAuth(token))
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("pushed web run {} summary to reports-aggregator", runId);
        } catch (Exception e) {
            log.warn("failed to push web run {} summary to reports-aggregator: {}", runId, e.getMessage());
        }
    }
}
