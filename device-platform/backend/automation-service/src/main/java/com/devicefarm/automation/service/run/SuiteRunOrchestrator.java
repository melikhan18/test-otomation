package com.devicefarm.automation.service.run;

import com.devicefarm.automation.domain.*;
import com.devicefarm.common.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Drives a {@link SuiteRunEntity} by chaining child {@link RunEntity}s sequentially on
 * the same device. Each child is submitted to the existing {@link RunOrchestrator} and
 * we poll the DB until it reaches a terminal status before starting the next.
 *
 * Sequential (not parallel) by design — the typical user expectation is "one suite =
 * one device session, ordered scenarios". Parallel multi-device execution is a separate
 * feature (Faz U).
 *
 * On a single child failure we do NOT abort the suite — we keep running so the user gets
 * full coverage data. Per-suite "stop on failure" is a future toggle.
 */
@Service
public class SuiteRunOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SuiteRunOrchestrator.class);

    /** Small pool — a typical org runs a handful of suites concurrently at most. */
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "suite-orchestrator");
        t.setDaemon(true);
        return t;
    });

    /** Poll cadence while waiting for a child run to terminate. */
    private static final long POLL_INTERVAL_MS = 1_000;

    /** Safety net so a stuck child can't pin a suite-orchestrator thread forever. */
    private static final long PER_RUN_TIMEOUT_MS = 30 * 60 * 1_000L; // 30 min

    private final SuiteRunRepository suiteRuns;
    private final SuiteRepository suites;
    private final SuiteScenarioRepository suiteScenarios;
    private final ScenarioRepository scenarios;
    private final RunRepository runs;
    private final RunOrchestrator runOrchestrator;
    private final SuiteRunCancellationRegistry suiteCancels;
    private final RunCancellationRegistry runCancels;

    public SuiteRunOrchestrator(SuiteRunRepository suiteRuns, SuiteRepository suites,
                                SuiteScenarioRepository suiteScenarios, ScenarioRepository scenarios,
                                RunRepository runs, RunOrchestrator runOrchestrator,
                                SuiteRunCancellationRegistry suiteCancels,
                                RunCancellationRegistry runCancels) {
        this.suiteRuns = suiteRuns;
        this.suites = suites;
        this.suiteScenarios = suiteScenarios;
        this.scenarios = scenarios;
        this.runs = runs;
        this.runOrchestrator = runOrchestrator;
        this.suiteCancels = suiteCancels;
        this.runCancels = runCancels;
    }

    public void submit(long suiteRunId, String userJwt,
                       Integer interStepDelayMs, Boolean adaptiveWait) {
        pool.submit(() -> {
            try { execute(suiteRunId, userJwt, interStepDelayMs, adaptiveWait); }
            catch (Throwable t) {
                log.error("suite run {} crashed", suiteRunId, t);
                markAborted(suiteRunId, "orchestrator crash: " + t.getMessage());
            }
            finally { suiteCancels.clear(suiteRunId); }
        });
    }

    /* ───────────────────────────  main loop  ─────────────────────────── */

    private void execute(long suiteRunId, String userJwt,
                         Integer interStepDelayMs, Boolean adaptiveWait) {
        SuiteRunEntity sr = suiteRuns.findById(suiteRunId)
                .orElseThrow(() -> ApiException.notFound("suite run"));
        SuiteEntity suite = suites.findById(sr.getSuiteId())
                .orElseThrow(() -> ApiException.notFound("suite"));

        List<SuiteScenarioEntity> links = suiteScenarios.findAllBySuiteIdOrderByOrderIndexAsc(suite.getId());
        if (links.isEmpty()) {
            markAborted(suiteRunId, "suite has no scenarios");
            return;
        }
        markRunning(sr, links.size());

        int passed = 0, failed = 0;
        boolean cancelled = false;
        for (SuiteScenarioEntity link : links) {
            // Cancel checkpoint before starting the next child. If the user pressed
            // Stop while we were waiting for the previous child to terminate, we
            // break here without launching the next one.
            if (suiteCancels.isCancelled(suiteRunId)) {
                cancelled = true;
                log.info("suite run {} cancel requested — stopping before scenario {}", suiteRunId, link.getScenarioId());
                break;
            }

            ScenarioEntity scenario = scenarios.findById(link.getScenarioId()).orElse(null);
            if (scenario == null) {
                // Scenario was deleted after suite was assembled. Skip but count as failed
                // so the user sees there's a missing piece.
                failed++;
                log.warn("suite run {} skipping missing scenario {}", suiteRunId, link.getScenarioId());
                continue;
            }
            RunEntity childRun = createChildRun(sr, scenario, interStepDelayMs, adaptiveWait);
            runOrchestrator.submit(childRun.getId(), userJwt);

            RunStatus terminal = waitForTerminal(suiteRunId, childRun.getId());
            if (terminal == RunStatus.PASSED) passed++;
            else failed++;
            if (terminal == RunStatus.CANCELLED) {
                // Either the user cancelled the child directly, or we cascaded from a
                // suite-level cancel. Either way the suite stops here.
                cancelled = true;
                break;
            }
        }

        if (cancelled) finalizeCancelled(suiteRunId, passed, failed);
        else           finalize(suiteRunId, passed, failed);
    }

    /**
     * Waits for the child run to terminate, but also watches its parent's cancel
     * flag — if the user clicks Stop on the suite while a child is mid-flight, we
     * forward the cancel to the child orchestrator so it stops at its next safe
     * checkpoint and uploads the partial recording. The child's CANCELLED status
     * propagates back here and breaks the suite loop.
     */
    private RunStatus waitForTerminal(long suiteRunId, long runId) {
        long deadline = System.currentTimeMillis() + PER_RUN_TIMEOUT_MS;
        boolean forwarded = false;
        while (System.currentTimeMillis() < deadline) {
            if (!forwarded && suiteCancels.isCancelled(suiteRunId)) {
                runCancels.requestCancel(runId);
                forwarded = true;
            }
            RunStatus s = runs.findById(runId).map(RunEntity::getStatus).orElse(null);
            if (s == null) return RunStatus.ERROR;
            if (s == RunStatus.PASSED || s == RunStatus.FAILED ||
                s == RunStatus.ERROR  || s == RunStatus.CANCELLED) return s;
            try { Thread.sleep(POLL_INTERVAL_MS); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); return RunStatus.ERROR; }
        }
        log.warn("child run {} exceeded {}ms in suite orchestrator", runId, PER_RUN_TIMEOUT_MS);
        return RunStatus.ERROR;
    }

    /* ─────────────────────  persistence helpers  ───────────────────── */

    @Transactional
    protected RunEntity createChildRun(SuiteRunEntity sr, ScenarioEntity scenario,
                                       Integer interStepDelayMs, Boolean adaptiveWait) {
        RunEntity run = new RunEntity(sr.getProductId(), sr.getProjectId(),
                scenario.getId(), sr.getDeviceId(),
                sr.getTriggeredByUserId(), sr.getEnvironment());
        run.setScenarioVersion(scenario.getVersion());
        run.setSuiteId(sr.getSuiteId());
        run.setSuiteRunId(sr.getId());
        run.setTriggerType("SUITE");
        if (interStepDelayMs != null) {
            run.setInterStepDelayMs(Math.max(0, Math.min(30_000, interStepDelayMs)));
        }
        if (adaptiveWait != null) {
            run.setAdaptiveWait(adaptiveWait);
        }
        return runs.save(run);
    }

    @Transactional
    protected void markRunning(SuiteRunEntity sr, int total) {
        sr.setStatus(SuiteRunStatus.RUNNING);
        sr.setStartedAt(Instant.now());
        sr.setTotalScenarios(total);
        suiteRuns.save(sr);
    }

    @Transactional
    protected void finalize(long suiteRunId, int passed, int failed) {
        suiteRuns.findById(suiteRunId).ifPresent(sr -> {
            Instant now = Instant.now();
            sr.setFinishedAt(now);
            if (sr.getStartedAt() != null) {
                sr.setDurationMs((int) (now.toEpochMilli() - sr.getStartedAt().toEpochMilli()));
            }
            sr.setPassedScenarios(passed);
            sr.setFailedScenarios(failed);
            sr.setStatus(failed == 0 ? SuiteRunStatus.PASSED : SuiteRunStatus.FAILED);
            suiteRuns.save(sr);
            log.info("suite run {} finished: {} ({}p / {}f)", suiteRunId, sr.getStatus(), passed, failed);
        });
    }

    @Transactional
    protected void finalizeCancelled(long suiteRunId, int passed, int failed) {
        suiteRuns.findById(suiteRunId).ifPresent(sr -> {
            Instant now = Instant.now();
            sr.setFinishedAt(now);
            if (sr.getStartedAt() != null) {
                sr.setDurationMs((int) (now.toEpochMilli() - sr.getStartedAt().toEpochMilli()));
            }
            sr.setPassedScenarios(passed);
            sr.setFailedScenarios(failed);
            sr.setStatus(SuiteRunStatus.CANCELLED);
            suiteRuns.save(sr);
            log.info("suite run {} cancelled by user ({}p / {}f so far)", suiteRunId, passed, failed);
        });
        suiteCancels.clear(suiteRunId);
    }

    @Transactional
    protected void markAborted(long suiteRunId, String reason) {
        suiteRuns.findById(suiteRunId).ifPresent(sr -> {
            sr.setStatus(SuiteRunStatus.ERROR);
            sr.setErrorSummary(reason);
            sr.setFinishedAt(Instant.now());
            if (sr.getStartedAt() != null) {
                sr.setDurationMs((int) (sr.getFinishedAt().toEpochMilli() - sr.getStartedAt().toEpochMilli()));
            }
            suiteRuns.save(sr);
        });
    }
}
