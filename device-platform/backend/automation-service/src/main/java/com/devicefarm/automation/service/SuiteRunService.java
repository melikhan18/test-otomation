package com.devicefarm.automation.service;

import com.devicefarm.automation.api.dto.SuiteRunDtos;
import com.devicefarm.automation.domain.*;
import com.devicefarm.automation.service.run.SuiteRunOrchestrator;
import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SuiteRunService {

    private final SuiteRunRepository suiteRuns;
    private final SuiteRepository suites;
    private final SuiteScenarioRepository suiteScenarios;
    private final ScenarioRepository scenarios;
    private final RunRepository runs;
    private final SuiteRunOrchestrator orchestrator;

    public SuiteRunService(SuiteRunRepository suiteRuns, SuiteRepository suites,
                           SuiteScenarioRepository suiteScenarios, ScenarioRepository scenarios,
                           RunRepository runs, SuiteRunOrchestrator orchestrator) {
        this.suiteRuns = suiteRuns;
        this.suites = suites;
        this.suiteScenarios = suiteScenarios;
        this.scenarios = scenarios;
        this.runs = runs;
        this.orchestrator = orchestrator;
    }

    @Transactional
    public SuiteRunDtos.View create(JwtPrincipal caller, SuiteRunDtos.CreateRequest req, String userJwt) {
        if (caller == null || caller.productId() == null) throw ApiException.unauthorized("missing identity");
        if (userJwt == null || userJwt.isBlank()) throw ApiException.unauthorized("missing Authorization header");

        SuiteEntity suite = suites.findById(req.suiteId())
                .orElseThrow(() -> ApiException.notFound("suite"));
        if (!suite.getProductId().equals(caller.productId())) throw ApiException.forbidden("cross-product");

        long scenarioCount = suiteScenarios.countBySuiteId(suite.getId());
        if (scenarioCount == 0) throw ApiException.badRequest("suite has no scenarios");

        SuiteRunEntity sr = new SuiteRunEntity(caller.productId(), suite.getId(), suite.getName(),
                req.deviceId(), caller.userId(), req.environment());
        sr = suiteRuns.save(sr);

        // After commit submission — see RunService for the same race justification.
        final long sid = sr.getId();
        final Integer isd = req.interStepDelayMs();
        final Boolean aw  = req.adaptiveWait();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { orchestrator.submit(sid, userJwt, isd, aw); }
            });
        } else {
            orchestrator.submit(sid, userJwt, isd, aw);
        }
        return toView(sr);
    }

    @Transactional(readOnly = true)
    public SuiteRunDtos.View get(JwtPrincipal caller, long id) {
        SuiteRunEntity sr = ensureOwned(caller, id);
        return toView(sr);
    }

    @Transactional(readOnly = true)
    public List<SuiteRunDtos.Summary> list(JwtPrincipal caller, Long suiteId) {
        if (caller == null || caller.productId() == null) throw ApiException.unauthorized("missing identity");
        Long pid = caller.productId();
        List<SuiteRunEntity> raw = suiteId != null
                ? suiteRuns.findTop50ByProductIdAndSuiteIdOrderByCreatedAtDesc(pid, suiteId)
                : suiteRuns.findTop100ByProductIdOrderByCreatedAtDesc(pid);
        return raw.stream().map(this::toSummary).toList();
    }

    /* ──────────────────────  helpers  ──────────────────────── */

    private SuiteRunEntity ensureOwned(JwtPrincipal caller, long id) {
        if (caller == null || caller.productId() == null) throw ApiException.unauthorized("missing identity");
        SuiteRunEntity sr = suiteRuns.findById(id).orElseThrow(() -> ApiException.notFound("suite run"));
        if (!sr.getProductId().equals(caller.productId())) throw ApiException.forbidden("cross-product");
        return sr;
    }

    private SuiteRunDtos.Summary toSummary(SuiteRunEntity sr) {
        return new SuiteRunDtos.Summary(
                sr.getId(), sr.getProductId(), sr.getSuiteId(), sr.getSuiteName(),
                sr.getDeviceId(), sr.getEnvironment(), sr.getStatus(),
                sr.getTotalScenarios(), sr.getPassedScenarios(), sr.getFailedScenarios(),
                sr.getDurationMs(),
                Tags.asList(sr.getTags()),
                sr.getCreatedAt(), sr.getStartedAt(), sr.getFinishedAt());
    }

    /** Replace the tag set on a suite run. */
    @Transactional
    public SuiteRunDtos.View updateTags(JwtPrincipal caller, long id, List<String> tags) {
        SuiteRunEntity sr = ensureOwned(caller, id);
        sr.setTags(Tags.normalize(tags));
        suiteRuns.save(sr);
        return toView(sr);
    }

    private SuiteRunDtos.View toView(SuiteRunEntity sr) {
        List<RunEntity> childRuns = runs.findAllBySuiteRunIdOrderByCreatedAtAsc(sr.getId());

        // Bulk-fetch scenario names so we don't N+1 query inside the map below.
        var scenarioIds = childRuns.stream()
                .map(RunEntity::getScenarioId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        if (!scenarioIds.isEmpty()) {
            scenarios.findAllById(scenarioIds).forEach(s -> names.put(s.getId(), s.getName()));
        }

        var children = childRuns.stream().map(r -> new SuiteRunDtos.ChildRun(
                r.getId(), r.getScenarioId(),
                r.getScenarioId() != null ? names.get(r.getScenarioId()) : null,
                r.getStatus(),
                r.getTotalSteps(), r.getPassedSteps(), r.getFailedSteps(),
                r.getDurationMs(),
                r.getVideoUrl(),
                r.getStartedAt(), r.getFinishedAt()
        )).toList();

        return new SuiteRunDtos.View(
                sr.getId(), sr.getProductId(), sr.getSuiteId(), sr.getSuiteName(),
                sr.getDeviceId(), sr.getEnvironment(), sr.getStatus(),
                sr.getTriggerType(), sr.getTriggeredByUserId(),
                sr.getStartedAt(), sr.getFinishedAt(), sr.getDurationMs(),
                sr.getTotalScenarios(), sr.getPassedScenarios(), sr.getFailedScenarios(),
                sr.getErrorSummary(),
                Tags.asList(sr.getTags()),
                sr.getCreatedAt(),
                children
        );
    }
}
