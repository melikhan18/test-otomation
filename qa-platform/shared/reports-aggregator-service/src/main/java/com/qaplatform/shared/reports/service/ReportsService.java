package com.qaplatform.shared.reports.service;

import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.common.runengine.status.RunStatus;
import com.qaplatform.shared.reports.api.dto.ReportsDtos;
import com.qaplatform.shared.reports.domain.RunSummaryEntity;
import com.qaplatform.shared.reports.domain.RunSummaryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read + write surface for the reports aggregator.
 *
 * <p>Auth model in F7 is intentionally coarse: any authenticated caller can
 * push or read. Push is meant to be a service-to-service call (Android's
 * automation-service POSTs over the docker network), and read is gated by
 * the same JWT the dashboard already uses. Per-project access control
 * tightens when company/project ownership migrates into tenant-service
 * in a later faz.</p>
 */
@Service
public class ReportsService {

    private final RunSummaryRepository repo;

    public ReportsService(RunSummaryRepository repo) {
        this.repo = repo;
    }

    /**
     * Upsert a run summary. (platform, sourceRunId) is the natural key —
     * re-emitting the same terminal status is a no-op-shaped overwrite,
     * which lets the publisher retry without bookkeeping.
     */
    @Transactional
    public ReportsDtos.RunSummaryView push(JwtPrincipal caller, ReportsDtos.PushRunSummary req) {
        require(caller);
        RunSummaryEntity row = repo.findByPlatformAndSourceRunId(req.platform(), req.sourceRunId())
                .orElseGet(() -> new RunSummaryEntity());
        row.setPlatform(req.platform());
        row.setSourceRunId(req.sourceRunId());
        row.setCompanyId(req.companyId());
        row.setProjectId(req.projectId());
        row.setStatus(req.status());
        row.setScenarioName(req.scenarioName());
        row.setTriggeredByUserId(req.triggeredByUserId());
        row.setTotalSteps(req.totalSteps() == null ? 0 : req.totalSteps());
        row.setPassedSteps(req.passedSteps() == null ? 0 : req.passedSteps());
        row.setFailedSteps(req.failedSteps() == null ? 0 : req.failedSteps());
        row.setDurationMs(req.durationMs());
        row.setStartedAt(req.startedAt());
        row.setFinishedAt(req.finishedAt());
        row.setErrorSummary(req.errorSummary());
        return toView(repo.save(row));
    }

    @Transactional(readOnly = true)
    public ReportsDtos.RunSummaryList list(JwtPrincipal caller, long projectId, int limit) {
        require(caller);
        int capped = Math.max(1, Math.min(limit, 200));
        List<ReportsDtos.RunSummaryView> items = repo
                .findAllByProjectIdOrderByFinishedAtDescIdDesc(projectId, PageRequest.of(0, capped))
                .stream().map(ReportsService::toView).toList();
        return new ReportsDtos.RunSummaryList(projectId, capped, items);
    }

    @Transactional(readOnly = true)
    public ReportsDtos.PlatformStatusSummary summary(JwtPrincipal caller, long projectId, Instant since) {
        require(caller);
        Map<String, Map<String, Long>> nested = new HashMap<>();
        for (var row : repo.aggregateByPlatformAndStatus(projectId, since)) {
            nested.computeIfAbsent(row.getPlatform(), k -> new HashMap<>())
                  .put(row.getStatus().name(), row.getCount());
        }
        // Ensure every active platform appears as a key even with zero rows —
        // makes the dashboard widget render predictably.
        for (String p : List.of("ANDROID", "IOS", "BACKEND", "WEB")) {
            nested.computeIfAbsent(p, k -> new HashMap<>());
            for (RunStatus s : RunStatus.values()) {
                nested.get(p).putIfAbsent(s.name(), 0L);
            }
        }
        return new ReportsDtos.PlatformStatusSummary(projectId, since, nested);
    }

    /**
     * Accepts any authenticated principal — both user JWTs (dashboard reads)
     * and service JWTs (platform stacks pushing summaries). userId can be null
     * for service tokens; that's intentional.
     */
    private static void require(JwtPrincipal caller) {
        if (caller == null) throw ApiException.unauthorized("missing identity");
    }

    private static ReportsDtos.RunSummaryView toView(RunSummaryEntity e) {
        return new ReportsDtos.RunSummaryView(
                e.getId(),
                e.getPlatform(),
                e.getSourceRunId(),
                e.getCompanyId(),
                e.getProjectId(),
                e.getStatus(),
                e.getScenarioName(),
                e.getTriggeredByUserId(),
                e.getTotalSteps(),
                e.getPassedSteps(),
                e.getFailedSteps(),
                e.getDurationMs(),
                e.getStartedAt(),
                e.getFinishedAt(),
                e.getErrorSummary(),
                e.getReceivedAt()
        );
    }
}
