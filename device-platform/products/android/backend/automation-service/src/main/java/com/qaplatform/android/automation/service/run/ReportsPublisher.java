package com.qaplatform.android.automation.service.run;

import com.qaplatform.android.automation.domain.RunEntity;
import com.qaplatform.android.automation.domain.RunRepository;
import com.qaplatform.android.automation.domain.ScenarioEntity;
import com.qaplatform.android.automation.domain.ScenarioRepository;
import com.qaplatform.common.jwt.JwtTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Pushes a terminal-state run summary to reports-aggregator-service so the
 * cross-platform dashboard can see it.
 *
 * <p>Fire-and-forget by design — a downstream outage must not delay the
 * user's view of run completion. Failures are logged at WARN; the aggregator
 * publishes its own ingestion lag metrics that can flag persistent drops.</p>
 *
 * <p>Authenticates with a service-issued JWT signed by the shared secret;
 * the token is re-minted per call (cheap on HMAC) rather than cached, which
 * keeps the publisher stateless and avoids token-rotation races.</p>
 */
@Component
public class ReportsPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReportsPublisher.class);
    private static final String PLATFORM = "ANDROID";

    private final RunRepository runs;
    private final ScenarioRepository scenarios;
    private final JwtTokenService tokens;
    private final RestClient http;

    public ReportsPublisher(RunRepository runs,
                            ScenarioRepository scenarios,
                            JwtTokenService tokens,
                            @Value("${app.services.reports.url:http://localhost:8090}") String baseUrl) {
        this.runs = runs;
        this.scenarios = scenarios;
        this.tokens = tokens;
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * Loads the run by id and POSTs its summary. Safe to call multiple times —
     * the aggregator is idempotent on (platform, sourceRunId).
     */
    @Async
    public void publishAsync(long runId) {
        try {
            RunEntity run = runs.findById(runId).orElse(null);
            if (run == null) {
                log.warn("ReportsPublisher: run {} not found, skipping push", runId);
                return;
            }
            String scenarioName = run.getScenarioId() == null ? null
                    : scenarios.findById(run.getScenarioId()).map(ScenarioEntity::getName).orElse(null);

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

            String token = tokens.issueServiceToken("android-automation");
            http.post()
                    .uri("/api/reports/runs")
                    .headers(h -> h.setBearerAuth(token))
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("pushed run {} summary to reports-aggregator", runId);
        } catch (Exception e) {
            // Downstream failure must not bubble up — orchestrator already
            // committed the terminal state, the user sees the result either way.
            log.warn("failed to push run {} summary to reports-aggregator: {}", runId, e.getMessage());
        }
    }
}
