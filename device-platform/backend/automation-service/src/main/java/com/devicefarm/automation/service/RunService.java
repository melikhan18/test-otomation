package com.devicefarm.automation.service;

import com.devicefarm.automation.api.dto.RunDtos;
import com.devicefarm.automation.domain.*;
import com.devicefarm.automation.service.run.RunOrchestrator;
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
public class RunService {

    private final RunRepository runs;
    private final StepResultRepository stepResults;
    private final ScenarioRepository scenarios;
    private final RunOrchestrator orchestrator;

    public RunService(RunRepository runs, StepResultRepository stepResults,
                      ScenarioRepository scenarios, RunOrchestrator orchestrator) {
        this.runs = runs;
        this.stepResults = stepResults;
        this.scenarios = scenarios;
        this.orchestrator = orchestrator;
    }

    /**
     * Create a run and schedule it on the background pool.
     * The user JWT is required so the orchestrator can reserve a device session on their
     * behalf (session-service enforces ownership).
     */
    @Transactional
    public RunDtos.View create(JwtPrincipal caller, RunDtos.CreateRequest req, String userJwt) {
        if (caller == null || caller.productId() == null) throw ApiException.unauthorized("missing identity");
        if (userJwt == null || userJwt.isBlank()) throw ApiException.unauthorized("missing Authorization header");

        ScenarioEntity sc = scenarios.findById(req.scenarioId())
                .orElseThrow(() -> ApiException.notFound("scenario"));
        if (!sc.getProductId().equals(caller.productId())) throw ApiException.forbidden("cross-product");

        RunEntity run = new RunEntity(caller.productId(), sc.getId(), req.deviceId(),
                caller.userId(), req.environment());
        run.setScenarioVersion(sc.getVersion());
        if (req.interStepDelayMs() != null) {
            run.setInterStepDelayMs(Math.max(0, Math.min(30_000, req.interStepDelayMs())));
        }
        if (req.adaptiveWait() != null) {
            run.setAdaptiveWait(req.adaptiveWait());
        }
        run = runs.save(run);

        // Submit AFTER commit — otherwise the worker thread races the transaction and
        // sees an empty `runs.findById(runId)` ("run not found"), leaving the run stuck in QUEUED.
        final long runId = run.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { orchestrator.submit(runId, userJwt); }
            });
        } else {
            orchestrator.submit(runId, userJwt);
        }
        return toView(run, sc);
    }

    @Transactional(readOnly = true)
    public RunDtos.View get(JwtPrincipal caller, long id) {
        RunEntity run = ensureOwned(caller, id);
        ScenarioEntity sc = run.getScenarioId() != null ? scenarios.findById(run.getScenarioId()).orElse(null) : null;
        return toView(run, sc);
    }

    @Transactional(readOnly = true)
    public List<RunDtos.Summary> list(JwtPrincipal caller, Long scenarioId) {
        if (caller == null || caller.productId() == null) throw ApiException.unauthorized("missing identity");
        Long pid = caller.productId();
        List<RunEntity> raw = scenarioId != null
                ? runs.findTop50ByProductIdAndScenarioIdOrderByCreatedAtDesc(pid, scenarioId)
                : runs.findTop200ByProductIdOrderByCreatedAtDesc(pid);

        // Bulk-fetch the referenced scenarios for nice display labels.
        var scenarioIds = raw.stream().map(RunEntity::getScenarioId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        if (!scenarioIds.isEmpty()) {
            scenarios.findAllById(scenarioIds).forEach(s -> names.put(s.getId(), s.getName()));
        }

        return raw.stream().map(r -> new RunDtos.Summary(
                r.getId(), r.getProductId(), r.getScenarioId(),
                r.getScenarioId() != null ? names.get(r.getScenarioId()) : null,
                r.getDeviceId(), r.getEnvironment(), r.getStatus(),
                r.getTotalSteps(), r.getPassedSteps(), r.getFailedSteps(),
                r.getDurationMs(),
                r.getVideoUrl(), r.getSuiteRunId(),
                Tags.asList(r.getTags()),
                r.getCreatedAt(), r.getStartedAt(), r.getFinishedAt()
        )).toList();
    }

    /** Replace the tag set on a run. Returns the refreshed view so the UI can render. */
    @Transactional
    public RunDtos.View updateTags(JwtPrincipal caller, long id, List<String> tags) {
        RunEntity run = ensureOwned(caller, id);
        run.setTags(Tags.normalize(tags));
        runs.save(run);
        ScenarioEntity sc = run.getScenarioId() != null
                ? scenarios.findById(run.getScenarioId()).orElse(null) : null;
        return toView(run, sc);
    }

    /* ──────────────────────  helpers  ──────────────────────── */

    private RunEntity ensureOwned(JwtPrincipal caller, long id) {
        if (caller == null || caller.productId() == null) throw ApiException.unauthorized("missing identity");
        RunEntity r = runs.findById(id).orElseThrow(() -> ApiException.notFound("run"));
        if (!r.getProductId().equals(caller.productId())) throw ApiException.forbidden("cross-product");
        return r;
    }

    private RunDtos.View toView(RunEntity r, ScenarioEntity sc) {
        var rows = stepResults.findAllByRunIdOrderByOrderIndexAsc(r.getId()).stream()
                .map(s -> new RunDtos.StepResultView(
                        s.getId(), s.getStepId(), s.getOrderIndex(), s.getAction(), s.getStatus(),
                        s.getStartedAt(), s.getFinishedAt(), s.getDurationMs(),
                        s.getErrorMessage(), s.getScreenshotUrl(), s.getResolvedLocator(), s.getRetriesUsed()
                ))
                .toList();
        return new RunDtos.View(
                r.getId(), r.getProductId(), r.getScenarioId(),
                sc != null ? sc.getName() : null,
                r.getScenarioVersion(),
                r.getDeviceId(), r.getSessionId(), r.getEnvironment(),
                r.getStatus(), r.getTriggerType(), r.getTriggeredByUserId(),
                r.getStartedAt(), r.getFinishedAt(), r.getDurationMs(),
                r.getTotalSteps(), r.getPassedSteps(), r.getFailedSteps(),
                r.getErrorSummary(),
                r.getInterStepDelayMs(),
                r.isAdaptiveWait(),
                r.getVideoUrl(),
                Tags.asList(r.getTags()),
                r.getCreatedAt(),
                rows
        );
    }
}
