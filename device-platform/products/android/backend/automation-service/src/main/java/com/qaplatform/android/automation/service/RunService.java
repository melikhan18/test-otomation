package com.qaplatform.android.automation.service;

import com.qaplatform.android.automation.api.dto.RunDtos;
import com.qaplatform.android.automation.domain.*;
import com.qaplatform.android.automation.service.run.RunOrchestrator;
import com.qaplatform.android.automation.tenancy.ProjectContext;
import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
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
    private final AppRepository apps;
    private final AppVersionRepository appVersions;
    private final RunOrchestrator orchestrator;
    private final com.qaplatform.android.automation.service.run.RunCancellationRegistry cancels;

    public RunService(RunRepository runs, StepResultRepository stepResults,
                      ScenarioRepository scenarios,
                      AppRepository apps, AppVersionRepository appVersions,
                      RunOrchestrator orchestrator,
                      com.qaplatform.android.automation.service.run.RunCancellationRegistry cancels) {
        this.runs = runs;
        this.stepResults = stepResults;
        this.scenarios = scenarios;
        this.apps = apps;
        this.appVersions = appVersions;
        this.orchestrator = orchestrator;
        this.cancels = cancels;
    }

    @Transactional
    public RunDtos.View create(JwtPrincipal caller, ProjectContext ctx,
                               RunDtos.CreateRequest req, String userJwt) {
        if (userJwt == null || userJwt.isBlank()) throw ApiException.unauthorized("missing Authorization header");

        ScenarioEntity sc = scenarios.findById(req.scenarioId())
                .orElseThrow(() -> ApiException.notFound("scenario"));
        if (!ctx.projectId().equals(sc.getProjectId())) throw ApiException.forbidden("scenario not in active project");

        RunEntity run = new RunEntity(ctx.legacyProductId(), ctx.projectId(),
                sc.getId(), req.deviceId(), caller.userId(), req.environment());
        run.setScenarioVersion(sc.getVersion());
        if (req.interStepDelayMs() != null) {
            run.setInterStepDelayMs(Math.max(0, Math.min(30_000, req.interStepDelayMs())));
        }
        if (req.adaptiveWait() != null) {
            run.setAdaptiveWait(req.adaptiveWait());
        }
        // Faz 4 — target app + reset config. Validate cross-project access on the
        // APK version so a project can't smuggle in a foreign company's binary.
        if (req.targetAppVersionId() != null) {
            AppVersionEntity v = appVersions.findById(req.targetAppVersionId())
                    .orElseThrow(() -> ApiException.notFound("app version"));
            AppEntity app = apps.findById(v.getAppId())
                    .orElseThrow(() -> ApiException.notFound("app"));
            if (!ctx.projectId().equals(app.getProjectId())) {
                throw ApiException.forbidden("app version not in active project");
            }
            if (app.getArchivedAt() != null) {
                throw ApiException.badRequest("target app has been archived");
            }
            run.setTargetAppVersionId(v.getId());
        }
        if (req.resetHomeAfter() != null)   run.setResetHomeAfter(req.resetHomeAfter());
        if (req.killProcessAfter() != null) run.setKillProcessAfter(req.killProcessAfter());
        run = runs.save(run);

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
    public RunDtos.View get(JwtPrincipal caller, ProjectContext ctx, long id) {
        RunEntity run = ensureInProject(ctx, id);
        ScenarioEntity sc = run.getScenarioId() != null ? scenarios.findById(run.getScenarioId()).orElse(null) : null;
        return toView(run, sc);
    }

    @Transactional(readOnly = true)
    public List<RunDtos.Summary> list(JwtPrincipal caller, ProjectContext ctx, Long scenarioId) {
        List<RunEntity> raw = scenarioId != null
                ? runs.findTop50ByProjectIdAndScenarioIdOrderByCreatedAtDesc(ctx.projectId(), scenarioId)
                : runs.findTop200ByProjectIdOrderByCreatedAtDesc(ctx.projectId());

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
                r.getCreatedAt(), r.getStartedAt(), r.getFinishedAt(),
                r.getTargetAppVersionId(),
                r.getAppPrepStatus()
        )).toList();
    }

    /** Resolve APK + version for the view layer. Returns null when no target was set
     *  or the version has been deleted (ON DELETE SET NULL leaves a null FK). */
    private RunDtos.TargetAppRef resolveTargetApp(Long versionId) {
        if (versionId == null) return null;
        AppVersionEntity v = appVersions.findById(versionId).orElse(null);
        if (v == null) return null;
        AppEntity app = apps.findById(v.getAppId()).orElse(null);
        if (app == null) return null;
        return new RunDtos.TargetAppRef(
                app.getId(), app.getPackageName(), app.getDisplayName(),
                v.getId(), v.getVersionCode(), v.getVersionName()
        );
    }

    @Transactional
    public RunDtos.View updateTags(JwtPrincipal caller, ProjectContext ctx, long id, List<String> tags) {
        RunEntity run = ensureInProject(ctx, id);
        run.setTags(Tags.normalize(tags));
        runs.save(run);
        ScenarioEntity sc = run.getScenarioId() != null
                ? scenarios.findById(run.getScenarioId()).orElse(null) : null;
        return toView(run, sc);
    }

    /**
     * Signal a running/queued run to stop at its next safe checkpoint. We don't
     * abort the orchestrator's thread or kill the device session here — that
     * would leave the recording without a graceful close. Instead we flip the
     * in-memory cancel flag and let the worker see it between steps; the finally
     * block still uploads the partial MP4 and stamps {@code CANCELLED}.
     *
     * Idempotent: cancelling an already-terminal run is a no-op (and not an
     * error — the UI may double-click the stop button).
     */
    @Transactional(readOnly = true)
    public RunDtos.View cancel(JwtPrincipal caller, ProjectContext ctx, long id) {
        RunEntity run = ensureInProject(ctx, id);
        RunStatus s = run.getStatus();
        if (s != RunStatus.QUEUED && s != RunStatus.RUNNING) {
            // Already done — return the current view so the client just re-renders.
            ScenarioEntity sc = run.getScenarioId() != null
                    ? scenarios.findById(run.getScenarioId()).orElse(null) : null;
            return toView(run, sc);
        }
        cancels.requestCancel(id);
        ScenarioEntity sc = run.getScenarioId() != null
                ? scenarios.findById(run.getScenarioId()).orElse(null) : null;
        return toView(run, sc);
    }

    /* ──────────────────────  helpers  ──────────────────────── */

    private RunEntity ensureInProject(ProjectContext ctx, long id) {
        RunEntity r = runs.findById(id).orElseThrow(() -> ApiException.notFound("run"));
        if (!ctx.projectId().equals(r.getProjectId())) throw ApiException.forbidden("run not in active project");
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
                rows,
                r.getTargetAppVersionId(),
                resolveTargetApp(r.getTargetAppVersionId()),
                r.getAppPrepStatus(),
                r.getAppPrepDurationMs(),
                r.getAppPrepError(),
                r.isResetHomeAfter(),
                r.isKillProcessAfter()
        );
    }
}
