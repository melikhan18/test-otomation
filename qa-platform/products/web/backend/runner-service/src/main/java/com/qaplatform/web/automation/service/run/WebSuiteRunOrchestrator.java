package com.qaplatform.web.automation.service.run;

import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.runengine.status.RunStatus;
import com.qaplatform.common.runengine.status.SuiteRunStatus;
import com.qaplatform.web.automation.domain.WebRunEntity;
import com.qaplatform.web.automation.domain.WebRunRepository;
import com.qaplatform.web.automation.domain.WebScenarioEntity;
import com.qaplatform.web.automation.domain.WebScenarioRepository;
import com.qaplatform.web.automation.domain.WebSuiteEntity;
import com.qaplatform.web.automation.domain.WebSuiteRepository;
import com.qaplatform.web.automation.domain.WebSuiteRunEntity;
import com.qaplatform.web.automation.domain.WebSuiteRunRepository;
import com.qaplatform.web.automation.domain.WebSuiteScenarioEntity;
import com.qaplatform.web.automation.domain.WebSuiteScenarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Suite-level orchestrator — the analog of Android's
 * {@code SuiteRunOrchestrator}. For each scenario in the suite, creates a
 * child {@link WebRunEntity} pointing back at this {@link WebSuiteRunEntity},
 * submits it to {@link WebRunOrchestrator}, and waits (DB-poll) for the
 * terminal state before kicking off the next one.
 *
 * <p>Sequential execution — same scenario can't safely run in parallel on the
 * same browser context anyway; parallelisation across browsers will land
 * when we lift the orchestrator into a worker pool model.</p>
 */
@Service
public class WebSuiteRunOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(WebSuiteRunOrchestrator.class);
    private static final long POLL_INTERVAL_MS = 1500L;
    private static final long PER_SCENARIO_TIMEOUT_MS = 30 * 60 * 1000L;  // 30 min

    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "web-suite-orchestrator");
        t.setDaemon(true);
        return t;
    });

    private final WebSuiteRunRepository suiteRuns;
    private final WebSuiteRepository suites;
    private final WebSuiteScenarioRepository suiteScenarios;
    private final WebRunRepository runs;
    private final WebScenarioRepository scenarios;
    private final WebRunOrchestrator runOrchestrator;
    private final WebSuiteReportsPublisher suiteReportsPublisher;

    public WebSuiteRunOrchestrator(WebSuiteRunRepository suiteRuns,
                                   WebSuiteRepository suites,
                                   WebSuiteScenarioRepository suiteScenarios,
                                   WebRunRepository runs,
                                   WebScenarioRepository scenarios,
                                   WebRunOrchestrator runOrchestrator,
                                   WebSuiteReportsPublisher suiteReportsPublisher) {
        this.suiteRuns = suiteRuns;
        this.suites = suites;
        this.suiteScenarios = suiteScenarios;
        this.runs = runs;
        this.scenarios = scenarios;
        this.runOrchestrator = runOrchestrator;
        this.suiteReportsPublisher = suiteReportsPublisher;
    }

    public void submit(long suiteRunId) {
        pool.submit(() -> {
            try { execute(suiteRunId); }
            catch (Throwable t) {
                log.error("web suite run {} crashed", suiteRunId, t);
                fail(suiteRunId, "orchestrator crash: " + t.getMessage());
            }
        });
    }

    private void execute(long suiteRunId) {
        WebSuiteRunEntity sr = suiteRuns.findById(suiteRunId).orElse(null);
        if (sr == null) { log.warn("web suite run {} disappeared", suiteRunId); return; }
        if (sr.getSuiteId() == null) { fail(suiteRunId, "suite not found"); return; }

        WebSuiteEntity suite = suites.findById(sr.getSuiteId()).orElse(null);
        if (suite == null) { fail(suiteRunId, "suite not found"); return; }

        List<WebSuiteScenarioEntity> links = suiteScenarios.findAllBySuiteIdOrderByOrderIndexAsc(suite.getId());
        if (links.isEmpty()) { fail(suiteRunId, "suite has no scenarios"); return; }

        markRunning(sr, links.size());

        int passed = 0, failed = 0;
        boolean anyError = false;

        for (WebSuiteScenarioEntity link : links) {
            WebScenarioEntity scenario = scenarios.findById(link.getScenarioId()).orElse(null);
            if (scenario == null) {
                failed++;
                anyError = true;
                continue;
            }

            // Reuse RunOrchestrator's full pipeline — fresh Playwright, fresh
            // BrowserContext, video + trace per scenario — same fidelity as a
            // standalone run, only difference is the suite_run_id backlink.
            WebRunEntity childRun = createChildRun(sr, scenario);
            runOrchestrator.submit(childRun.getId());

            RunStatus terminal = waitForTerminal(childRun.getId());
            if (terminal == RunStatus.PASSED) passed++;
            else { failed++; if (terminal == RunStatus.ERROR) anyError = true; }
        }

        finalize(suiteRunId, passed, failed, anyError);
        suiteReportsPublisher.publishAsync(suiteRunId);
    }

    private RunStatus waitForTerminal(long runId) {
        long deadline = System.currentTimeMillis() + PER_SCENARIO_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try { Thread.sleep(POLL_INTERVAL_MS); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); return RunStatus.ERROR; }
            RunStatus status = runs.findById(runId).map(WebRunEntity::getStatus).orElse(null);
            if (status == null) return RunStatus.ERROR;
            if (status == RunStatus.PASSED || status == RunStatus.FAILED
                    || status == RunStatus.ERROR || status == RunStatus.CANCELLED) return status;
        }
        log.warn("web suite run timed out waiting for child run {}; treating as ERROR", runId);
        return RunStatus.ERROR;
    }

    /* ──────────────────────── persistence ──────────────────────────────── */

    @Transactional
    protected WebRunEntity createChildRun(WebSuiteRunEntity sr, WebScenarioEntity scenario) {
        WebRunEntity run = new WebRunEntity(
                sr.getProjectId(), scenario.getId(),
                sr.getBrowserProfileId(),
                sr.getTriggeredByUserId(), sr.getEnvironment());
        run.setScenarioVersion(scenario.getVersion());
        run.setSuiteRunId(sr.getId());
        return runs.save(run);
    }

    @Transactional
    protected void markRunning(WebSuiteRunEntity sr, int totalScenarios) {
        sr.setStatus(SuiteRunStatus.RUNNING);
        sr.setStartedAt(Instant.now());
        sr.setTotalScenarios(totalScenarios);
        suiteRuns.save(sr);
    }

    @Transactional
    protected void finalize(long suiteRunId, int passed, int failed, boolean anyError) {
        suiteRuns.findById(suiteRunId).ifPresent(sr -> {
            Instant now = Instant.now();
            sr.setFinishedAt(now);
            if (sr.getStartedAt() != null) {
                sr.setDurationMs((int) (now.toEpochMilli() - sr.getStartedAt().toEpochMilli()));
            }
            sr.setPassedScenarios(passed);
            sr.setFailedScenarios(failed);
            SuiteRunStatus status;
            if (anyError && passed == 0) status = SuiteRunStatus.ERROR;
            else status = failed == 0 ? SuiteRunStatus.PASSED : SuiteRunStatus.FAILED;
            sr.setStatus(status);
            suiteRuns.save(sr);
            log.info("web suite run {} finished: {} ({}p / {}f)", suiteRunId, status, passed, failed);
        });
    }

    @Transactional
    protected void fail(long suiteRunId, String message) {
        suiteRuns.findById(suiteRunId).ifPresent(sr -> {
            sr.setStatus(SuiteRunStatus.ERROR);
            sr.setErrorSummary(message);
            sr.setFinishedAt(Instant.now());
            if (sr.getStartedAt() != null) {
                sr.setDurationMs((int) (sr.getFinishedAt().toEpochMilli() - sr.getStartedAt().toEpochMilli()));
            }
            suiteRuns.save(sr);
        });
        suiteReportsPublisher.publishAsync(suiteRunId);
    }
}
