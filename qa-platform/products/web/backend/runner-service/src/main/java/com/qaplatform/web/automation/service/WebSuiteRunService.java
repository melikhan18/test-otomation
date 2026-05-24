package com.qaplatform.web.automation.service;

import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.web.automation.api.dto.WebSuiteRunDtos;
import com.qaplatform.web.automation.browser.BrowserCatalog;
import com.qaplatform.web.automation.domain.WebRunEntity;
import com.qaplatform.web.automation.domain.WebRunRepository;
import com.qaplatform.web.automation.domain.WebScenarioEntity;
import com.qaplatform.web.automation.domain.WebScenarioRepository;
import com.qaplatform.web.automation.domain.WebSuiteEntity;
import com.qaplatform.web.automation.domain.WebSuiteRepository;
import com.qaplatform.web.automation.domain.WebSuiteRunEntity;
import com.qaplatform.web.automation.domain.WebSuiteRunRepository;
import com.qaplatform.web.automation.domain.WebSuiteScenarioRepository;
import com.qaplatform.web.automation.service.run.WebSuiteRunOrchestrator;
import com.qaplatform.web.automation.tenancy.ProjectContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WebSuiteRunService {

    private final WebSuiteRunRepository suiteRuns;
    private final WebSuiteRepository suites;
    private final WebSuiteScenarioRepository suiteScenarios;
    private final WebRunRepository runs;
    private final WebScenarioRepository scenarios;
    private final BrowserCatalog browsers;
    private final WebSuiteRunOrchestrator orchestrator;

    public WebSuiteRunService(WebSuiteRunRepository suiteRuns, WebSuiteRepository suites,
                              WebSuiteScenarioRepository suiteScenarios,
                              WebRunRepository runs, WebScenarioRepository scenarios,
                              BrowserCatalog browsers,
                              WebSuiteRunOrchestrator orchestrator) {
        this.suiteRuns = suiteRuns;
        this.suites = suites;
        this.suiteScenarios = suiteScenarios;
        this.runs = runs;
        this.scenarios = scenarios;
        this.browsers = browsers;
        this.orchestrator = orchestrator;
    }

    @Transactional
    public WebSuiteRunDtos.View create(JwtPrincipal caller, ProjectContext ctx, WebSuiteRunDtos.CreateRequest req) {
        WebSuiteEntity suite = suites.findById(req.suiteId())
                .orElseThrow(() -> ApiException.notFound("suite"));
        if (!ctx.projectId().equals(suite.getProjectId())) {
            throw ApiException.forbidden("suite not in active project");
        }
        if (suiteScenarios.countBySuiteId(suite.getId()) == 0) {
            throw ApiException.badRequest("suite has no scenarios");
        }
        browsers.find(req.browserProfileId())
                .orElseThrow(() -> ApiException.badRequest("unknown browser profile: " + req.browserProfileId()));

        WebSuiteRunEntity sr = new WebSuiteRunEntity(
                ctx.projectId(), suite.getId(), suite.getName(),
                req.browserProfileId(), caller.userId(),
                req.environment() == null ? "default" : req.environment());
        WebSuiteRunEntity saved = suiteRuns.save(sr);

        final long suiteRunId = saved.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { orchestrator.submit(suiteRunId); }
        });

        return toView(saved);
    }

    @Transactional(readOnly = true)
    public WebSuiteRunDtos.View get(JwtPrincipal caller, ProjectContext ctx, long id) {
        WebSuiteRunEntity sr = suiteRuns.findById(id).orElseThrow(() -> ApiException.notFound("suite run"));
        if (!ctx.projectId().equals(sr.getProjectId())) throw ApiException.forbidden("suite run not in active project");
        return toView(sr);
    }

    @Transactional(readOnly = true)
    public List<WebSuiteRunDtos.Summary> list(JwtPrincipal caller, ProjectContext ctx, Long suiteId) {
        List<WebSuiteRunEntity> raw = suiteId != null
                ? suiteRuns.findTop50ByProjectIdAndSuiteIdOrderByCreatedAtDesc(ctx.projectId(), suiteId)
                : suiteRuns.findTop100ByProjectIdOrderByCreatedAtDesc(ctx.projectId());
        return raw.stream().map(WebSuiteRunService::toSummary).toList();
    }

    private static WebSuiteRunDtos.Summary toSummary(WebSuiteRunEntity sr) {
        return new WebSuiteRunDtos.Summary(
                sr.getId(), sr.getSuiteId() == null ? 0L : sr.getSuiteId(), sr.getSuiteName(),
                sr.getBrowserProfileId(), sr.getEnvironment(),
                sr.getStatus(),
                sr.getTotalScenarios(), sr.getPassedScenarios(), sr.getFailedScenarios(),
                sr.getDurationMs(),
                sr.getCreatedAt(), sr.getStartedAt(), sr.getFinishedAt()
        );
    }

    private WebSuiteRunDtos.View toView(WebSuiteRunEntity sr) {
        List<WebRunEntity> childRuns = runs.findAllBySuiteRunIdOrderByCreatedAtAsc(sr.getId());
        var scenarioIds = childRuns.stream()
                .map(WebRunEntity::getScenarioId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        if (!scenarioIds.isEmpty()) {
            scenarios.findAllById(scenarioIds).forEach((WebScenarioEntity s) -> names.put(s.getId(), s.getName()));
        }
        var children = childRuns.stream().map(r -> new WebSuiteRunDtos.ChildRun(
                r.getId(), r.getScenarioId(),
                r.getScenarioId() != null ? names.get(r.getScenarioId()) : null,
                r.getStatus(),
                r.getTotalSteps(), r.getPassedSteps(), r.getFailedSteps(),
                r.getDurationMs(),
                r.getVideoUrl(), r.getTraceUrl(),
                r.getStartedAt(), r.getFinishedAt()
        )).toList();
        return new WebSuiteRunDtos.View(
                sr.getId(), sr.getSuiteId() == null ? 0L : sr.getSuiteId(), sr.getSuiteName(),
                sr.getBrowserProfileId(), sr.getEnvironment(),
                sr.getStatus(), sr.getTriggeredByUserId(),
                sr.getStartedAt(), sr.getFinishedAt(), sr.getDurationMs(),
                sr.getTotalScenarios(), sr.getPassedScenarios(), sr.getFailedScenarios(),
                sr.getErrorSummary(),
                sr.getCreatedAt(),
                children
        );
    }
}
