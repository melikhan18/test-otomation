package com.qaplatform.android.automation.service;

import com.qaplatform.android.automation.api.dto.SuiteRunDtos;
import com.qaplatform.android.automation.domain.*;
import com.qaplatform.android.automation.service.run.SuiteRunOrchestrator;
import com.qaplatform.android.automation.tenancy.ProjectContext;
import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.common.runengine.status.SuiteRunStatus;
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
    private final AppRepository apps;
    private final AppVersionRepository appVersions;
    private final SuiteRunOrchestrator orchestrator;
    private final com.qaplatform.android.automation.service.run.SuiteRunCancellationRegistry cancels;

    public SuiteRunService(SuiteRunRepository suiteRuns, SuiteRepository suites,
                           SuiteScenarioRepository suiteScenarios, ScenarioRepository scenarios,
                           RunRepository runs,
                           AppRepository apps, AppVersionRepository appVersions,
                           SuiteRunOrchestrator orchestrator,
                           com.qaplatform.android.automation.service.run.SuiteRunCancellationRegistry cancels) {
        this.suiteRuns = suiteRuns;
        this.suites = suites;
        this.suiteScenarios = suiteScenarios;
        this.scenarios = scenarios;
        this.runs = runs;
        this.apps = apps;
        this.appVersions = appVersions;
        this.orchestrator = orchestrator;
        this.cancels = cancels;
    }

    @Transactional
    public SuiteRunDtos.View create(JwtPrincipal caller, ProjectContext ctx,
                                    SuiteRunDtos.CreateRequest req, String userJwt) {
        if (userJwt == null || userJwt.isBlank()) throw ApiException.unauthorized("missing Authorization header");

        SuiteEntity suite = suites.findById(req.suiteId())
                .orElseThrow(() -> ApiException.notFound("suite"));
        if (!ctx.projectId().equals(suite.getProjectId())) throw ApiException.forbidden("suite not in active project");

        long scenarioCount = suiteScenarios.countBySuiteId(suite.getId());
        if (scenarioCount == 0) throw ApiException.badRequest("suite has no scenarios");

        SuiteRunEntity sr = new SuiteRunEntity(ctx.legacyProductId(), ctx.projectId(),
                suite.getId(), suite.getName(),
                req.deviceId(), caller.userId(), req.environment());
        // Faz 4 — validate target APK belongs to the same project as the suite.
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
            sr.setTargetAppVersionId(v.getId());
        }
        if (req.resetHomeAfter() != null)   sr.setResetHomeAfter(req.resetHomeAfter());
        if (req.killProcessAfter() != null) sr.setKillProcessAfter(req.killProcessAfter());
        sr = suiteRuns.save(sr);

        final long sid = sr.getId();
        final Integer isd = req.interStepDelayMs();
        final Boolean aw  = req.adaptiveWait();
        final Long appVer  = sr.getTargetAppVersionId();
        final boolean resetHome = sr.isResetHomeAfter();
        final boolean killProc  = sr.isKillProcessAfter();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    orchestrator.submit(sid, userJwt, isd, aw, appVer, resetHome, killProc);
                }
            });
        } else {
            orchestrator.submit(sid, userJwt, isd, aw, appVer, resetHome, killProc);
        }
        return toView(sr);
    }

    @Transactional(readOnly = true)
    public SuiteRunDtos.View get(JwtPrincipal caller, ProjectContext ctx, long id) {
        return toView(ensureInProject(ctx, id));
    }

    @Transactional(readOnly = true)
    public List<SuiteRunDtos.Summary> list(JwtPrincipal caller, ProjectContext ctx, Long suiteId) {
        List<SuiteRunEntity> raw = suiteId != null
                ? suiteRuns.findTop50ByProjectIdAndSuiteIdOrderByCreatedAtDesc(ctx.projectId(), suiteId)
                : suiteRuns.findTop100ByProjectIdOrderByCreatedAtDesc(ctx.projectId());
        return raw.stream().map(this::toSummary).toList();
    }

    @Transactional
    public SuiteRunDtos.View updateTags(JwtPrincipal caller, ProjectContext ctx, long id, List<String> tags) {
        SuiteRunEntity sr = ensureInProject(ctx, id);
        sr.setTags(Tags.normalize(tags));
        suiteRuns.save(sr);
        return toView(sr);
    }

    /**
     * Signal a queued/running suite to stop. Cooperative: the orchestrator picks
     * the flag up between child runs and forwards a cancel to the child that's
     * currently in flight, so its partial recording still uploads.
     * Idempotent — calling on a terminal suite is a no-op.
     */
    @Transactional(readOnly = true)
    public SuiteRunDtos.View cancel(JwtPrincipal caller, ProjectContext ctx, long id) {
        SuiteRunEntity sr = ensureInProject(ctx, id);
        SuiteRunStatus s = sr.getStatus();
        if (s != SuiteRunStatus.QUEUED && s != SuiteRunStatus.RUNNING) {
            return toView(sr);
        }
        cancels.requestCancel(id);
        return toView(sr);
    }

    private SuiteRunEntity ensureInProject(ProjectContext ctx, long id) {
        SuiteRunEntity sr = suiteRuns.findById(id).orElseThrow(() -> ApiException.notFound("suite run"));
        if (!ctx.projectId().equals(sr.getProjectId())) throw ApiException.forbidden("suite run not in active project");
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

    private SuiteRunDtos.View toView(SuiteRunEntity sr) {
        List<RunEntity> childRuns = runs.findAllBySuiteRunIdOrderByCreatedAtAsc(sr.getId());
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
                r.getStartedAt(), r.getFinishedAt(),
                r.getAppPrepStatus()
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
                children,
                sr.getTargetAppVersionId(),
                resolveTargetApp(sr.getTargetAppVersionId()),
                sr.isResetHomeAfter(),
                sr.isKillProcessAfter()
        );
    }

    /** Mirror of {@code RunService.resolveTargetApp} — kept local rather than shared
     *  to avoid coupling the two services through a common helper class. */
    private com.qaplatform.android.automation.api.dto.RunDtos.TargetAppRef resolveTargetApp(Long versionId) {
        if (versionId == null) return null;
        AppVersionEntity v = appVersions.findById(versionId).orElse(null);
        if (v == null) return null;
        AppEntity app = apps.findById(v.getAppId()).orElse(null);
        if (app == null) return null;
        return new com.qaplatform.android.automation.api.dto.RunDtos.TargetAppRef(
                app.getId(), app.getPackageName(), app.getDisplayName(),
                v.getId(), v.getVersionCode(), v.getVersionName()
        );
    }
}
