package com.qaplatform.web.automation.service;

import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.web.automation.api.dto.WebRunDtos;
import com.qaplatform.web.automation.browser.BrowserCatalog;
import com.qaplatform.web.automation.domain.WebRunEntity;
import com.qaplatform.web.automation.domain.WebRunRepository;
import com.qaplatform.web.automation.domain.WebScenarioEntity;
import com.qaplatform.web.automation.domain.WebScenarioRepository;
import com.qaplatform.web.automation.domain.WebStepResultEntity;
import com.qaplatform.web.automation.domain.WebStepResultRepository;
import com.qaplatform.web.automation.service.run.WebRunOrchestrator;
import com.qaplatform.web.automation.tenancy.ProjectContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST-facing run management — create / get / list. The actual execution is
 * handed off to {@link WebRunOrchestrator} via an after-commit hook so the
 * POST returns the run id immediately and the orchestrator picks it up in
 * its own thread pool.
 */
@Service
public class WebRunService {

    private final WebRunRepository runs;
    private final WebScenarioRepository scenarios;
    private final WebStepResultRepository stepResults;
    private final BrowserCatalog browsers;
    private final WebRunOrchestrator orchestrator;

    public WebRunService(WebRunRepository runs, WebScenarioRepository scenarios,
                         WebStepResultRepository stepResults,
                         BrowserCatalog browsers,
                         WebRunOrchestrator orchestrator) {
        this.runs = runs;
        this.scenarios = scenarios;
        this.stepResults = stepResults;
        this.browsers = browsers;
        this.orchestrator = orchestrator;
    }

    @Transactional
    public WebRunDtos.View create(JwtPrincipal caller, ProjectContext ctx, WebRunDtos.CreateRequest req) {
        WebScenarioEntity sc = scenarios.findById(req.scenarioId())
                .orElseThrow(() -> ApiException.notFound("scenario"));
        if (!ctx.projectId().equals(sc.getProjectId())) throw ApiException.forbidden("scenario not in active project");
        browsers.find(req.browserProfileId())
                .orElseThrow(() -> ApiException.badRequest("unknown browser profile: " + req.browserProfileId()));

        WebRunEntity run = new WebRunEntity(ctx.projectId(), sc.getId(), req.browserProfileId(),
                caller.userId(), req.environment() == null ? "default" : req.environment());
        WebRunEntity saved = runs.save(run);

        final long runId = saved.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { orchestrator.submit(runId); }
        });

        return toView(saved);
    }

    @Transactional(readOnly = true)
    public WebRunDtos.View get(JwtPrincipal caller, ProjectContext ctx, long id) {
        WebRunEntity run = runs.findById(id).orElseThrow(() -> ApiException.notFound("run"));
        if (!ctx.projectId().equals(run.getProjectId())) throw ApiException.forbidden("run not in active project");
        return toView(run);
    }

    @Transactional(readOnly = true)
    public List<WebRunDtos.Summary> list(JwtPrincipal caller, ProjectContext ctx, Long scenarioId) {
        List<WebRunEntity> raw = scenarioId != null
                ? runs.findTop50ByProjectIdAndScenarioIdOrderByCreatedAtDesc(ctx.projectId(), scenarioId)
                : runs.findTop200ByProjectIdOrderByCreatedAtDesc(ctx.projectId());

        var scenarioIds = raw.stream().map(WebRunEntity::getScenarioId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        if (!scenarioIds.isEmpty()) {
            scenarios.findAllById(scenarioIds).forEach(s -> names.put(s.getId(), s.getName()));
        }
        return raw.stream().map(r -> new WebRunDtos.Summary(
                r.getId(), r.getScenarioId(),
                r.getScenarioId() != null ? names.get(r.getScenarioId()) : null,
                r.getBrowserProfileId(), r.getEnvironment(), r.getStatus(),
                r.getTotalSteps(), r.getPassedSteps(), r.getFailedSteps(),
                r.getDurationMs(),
                r.getVideoUrl(), r.getTraceUrl(),
                r.getCreatedAt(), r.getStartedAt(), r.getFinishedAt()
        )).toList();
    }

    private WebRunDtos.View toView(WebRunEntity r) {
        String scenarioName = r.getScenarioId() == null ? null
                : scenarios.findById(r.getScenarioId()).map(WebScenarioEntity::getName).orElse(null);
        List<WebRunDtos.StepResultView> srViews = stepResults.findAllByRunIdOrderByOrderIndexAsc(r.getId())
                .stream().map(WebRunService::toStepResultView).toList();
        return new WebRunDtos.View(
                r.getId(), r.getScenarioId(), scenarioName, r.getScenarioVersion(),
                r.getBrowserProfileId(), r.getEnvironment(),
                r.getStatus(), r.getTriggeredByUserId(),
                r.getStartedAt(), r.getFinishedAt(), r.getDurationMs(),
                r.getTotalSteps(), r.getPassedSteps(), r.getFailedSteps(),
                r.getErrorSummary(), r.getVideoUrl(), r.getTraceUrl(),
                r.getCreatedAt(), srViews
        );
    }

    private static WebRunDtos.StepResultView toStepResultView(WebStepResultEntity s) {
        return new WebRunDtos.StepResultView(
                s.getId(), s.getStepId(), s.getOrderIndex(), s.getAction(),
                s.getStatus(),
                s.getStartedAt(), s.getFinishedAt(), s.getDurationMs(),
                s.getErrorMessage(), s.getScreenshotUrl()
        );
    }
}
