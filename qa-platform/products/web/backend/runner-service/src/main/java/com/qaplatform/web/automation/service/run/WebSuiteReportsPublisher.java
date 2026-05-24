package com.qaplatform.web.automation.service.run;

import com.qaplatform.common.jwt.JwtTokenService;
import com.qaplatform.web.automation.domain.WebSuiteRunEntity;
import com.qaplatform.web.automation.domain.WebSuiteRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Push of terminal-state suite runs to {@code reports-aggregator-service}.
 * Mirrors {@link WebReportsPublisher} but keys the row as "WEB_SUITE" so
 * the dashboard can distinguish individual runs from aggregate suite runs
 * in the cross-platform feed.
 */
@Component
public class WebSuiteReportsPublisher {

    private static final Logger log = LoggerFactory.getLogger(WebSuiteReportsPublisher.class);
    private static final String PLATFORM = "WEB";
    /** Prefix on sourceRunId so suite ids don't collide with scenario-run ids
     *  in the aggregator's (platform, sourceRunId) uniqueness constraint. */
    private static final long SUITE_SOURCE_OFFSET = 1_000_000_000L;

    private final WebSuiteRunRepository suiteRuns;
    private final JwtTokenService tokens;
    private final RestClient http;

    public WebSuiteReportsPublisher(WebSuiteRunRepository suiteRuns,
                                    JwtTokenService tokens,
                                    @Value("${app.services.reports.url:http://localhost:8090}") String baseUrl) {
        this.suiteRuns = suiteRuns;
        this.tokens = tokens;
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Async
    public void publishAsync(long suiteRunId) {
        try {
            WebSuiteRunEntity sr = suiteRuns.findById(suiteRunId).orElse(null);
            if (sr == null) {
                log.warn("WebSuiteReportsPublisher: suite run {} not found", suiteRunId);
                return;
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("platform", PLATFORM);
            // Offset suite ids so the aggregator's (platform, sourceRunId) UNIQUE
            // constraint doesn't collide with individual run ids.
            payload.put("sourceRunId", SUITE_SOURCE_OFFSET + sr.getId());
            payload.put("projectId", sr.getProjectId());
            payload.put("status", sr.getStatus().name());
            if (sr.getSuiteName() != null) payload.put("scenarioName", "[suite] " + sr.getSuiteName());
            payload.put("triggeredByUserId", sr.getTriggeredByUserId());
            payload.put("totalSteps", sr.getTotalScenarios());
            payload.put("passedSteps", sr.getPassedScenarios());
            payload.put("failedSteps", sr.getFailedScenarios());
            if (sr.getDurationMs() != null) payload.put("durationMs", sr.getDurationMs().longValue());
            payload.put("startedAt", sr.getStartedAt() == null ? sr.getCreatedAt() : sr.getStartedAt());
            payload.put("finishedAt", sr.getFinishedAt());
            if (sr.getErrorSummary() != null) payload.put("errorSummary", sr.getErrorSummary());

            String token = tokens.issueServiceToken("web-runner");
            http.post()
                    .uri("/api/reports/runs")
                    .headers(h -> h.setBearerAuth(token))
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("pushed web suite run {} to reports-aggregator", suiteRunId);
        } catch (Exception e) {
            log.warn("failed to push web suite run {} to reports-aggregator: {}", suiteRunId, e.getMessage());
        }
    }
}
