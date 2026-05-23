package com.devicefarm.automation.service.run;

import com.devicefarm.automation.domain.*;
import com.devicefarm.automation.service.storage.ObjectStorage;
import com.devicefarm.common.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full run lifecycle — invoked once per accepted run request.
 *
 *   1. Reserve a session on the user's behalf (forwarded JWT)
 *   2. Mark run RUNNING, snapshot step plan into step_results rows
 *   3. Iterate steps sequentially with {@link StepRunner}
 *   4. Best-effort release the session
 *   5. Final run status: PASSED / FAILED / ERROR
 *
 * Runs in a fixed background pool so the REST POST returns immediately. Each task is
 * fully transactional at the per-row level (not the whole loop) so a partial run still
 * reports the steps that were executed.
 */
@Service
public class RunOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RunOrchestrator.class);

    /**
     * Idle time after the target app is launched, before the first step runs.
     * Many apps show a splash screen / animate the launcher transition / wait for a
     * cold-start network call — kicking off the locator resolve too early makes the
     * first ASSERT_VISIBLE or CLICK miss its element. 10 s is a conservative default
     * that handles every "normal" cold start; heavy apps may need more (future toggle).
     */
    private static final long APP_WARMUP_MS = 10_000L;

    /** Small pool — typical scenarios are I/O bound (HTTP to bridge + sleeps). */
    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "run-orchestrator");
        t.setDaemon(true);
        return t;
    });

    private final RunRepository runs;
    private final StepResultRepository stepResults;
    private final ScenarioRepository scenarios;
    private final StepRepository steps;
    private final ElementRepository elements;
    private final TestDataRepository testData;
    private final AppRepository apps;
    private final AppVersionRepository appVersions;
    private final SessionClient sessions;
    private final BridgeClient bridge;
    private final ObjectStorage storage;
    private final RunCancellationRegistry cancels;
    private final com.devicefarm.automation.tenancy.ProjectLookup projectLookup;

    public RunOrchestrator(RunRepository runs, StepResultRepository stepResults,
                           ScenarioRepository scenarios, StepRepository steps,
                           ElementRepository elements, TestDataRepository testData,
                           AppRepository apps, AppVersionRepository appVersions,
                           SessionClient sessions, BridgeClient bridge,
                           ObjectStorage storage,
                           RunCancellationRegistry cancels,
                           com.devicefarm.automation.tenancy.ProjectLookup projectLookup) {
        this.runs = runs;
        this.stepResults = stepResults;
        this.scenarios = scenarios;
        this.steps = steps;
        this.elements = elements;
        this.testData = testData;
        this.apps = apps;
        this.appVersions = appVersions;
        this.projectLookup = projectLookup;
        this.sessions = sessions;
        this.bridge = bridge;
        this.storage = storage;
        this.cancels = cancels;
    }

    public void submit(long runId, String userJwt) {
        pool.submit(() -> {
            try { execute(runId, userJwt); }
            catch (Throwable t) {
                log.error("run {} crashed", runId, t);
                fail(runId, "orchestrator crash: " + t.getMessage());
            }
        });
    }

    /* ────────────────────────  main loop  ──────────────────────── */

    private void execute(long runId, String userJwt) {
        RunEntity run = runs.findById(runId).orElseThrow(() -> ApiException.notFound("run"));
        ScenarioEntity scenario = scenarios.findById(run.getScenarioId())
                .orElseThrow(() -> ApiException.notFound("scenario"));

        List<StepEntity> plan = steps.findAllByScenarioIdOrderByOrderIndexAsc(scenario.getId());
        prePopulateResults(run, plan);

        markRunning(run, plan.size(), scenario.getVersion());

        SessionClient.Reservation reservation;
        try {
            // Forward the run's tenancy context to session-service so it can validate
            // device-vs-project access before locking. ProjectLookup gives us companyId
            // without holding a JPA reference to auth's schema.
            Long companyId = projectLookup.find(run.getProjectId())
                    .map(com.devicefarm.automation.tenancy.ProjectLookup.Info::companyId).orElse(null);
            reservation = sessions.reserve(run.getDeviceId(), userJwt, companyId, run.getProjectId());
        } catch (Exception e) {
            log.warn("run {} reservation failed: {}", runId, e.toString());
            fail(runId, "could not reserve device: " + e.getMessage());
            return;
        }
        markReserved(run, reservation.sessionId());

        StepRunner runner = new StepRunner(bridge, elements, testData,
                reservation.sessionId(), reservation.sessionToken(),
                run.getEnvironment());

        boolean anyFailure = false;
        boolean cancelled = false;
        boolean prepFailed = false;
        int interStepDelay = Math.max(0, run.getInterStepDelayMs());
        boolean adaptive = run.isAdaptiveWait();

        // Resolve target app + version once — used both by app prep and by the
        // post-run reset (so we don't requery in the finally block).
        String targetPackageName = null;
        if (run.getTargetAppVersionId() != null) {
            AppVersionEntity v = appVersions.findById(run.getTargetAppVersionId()).orElse(null);
            if (v != null) {
                AppEntity app = apps.findById(v.getAppId()).orElse(null);
                if (app != null) targetPackageName = app.getPackageName();
            }
        }

        // Start the recording BEFORE the keyframe nudge so the keyframe is included as the
        // first frame in the MP4 (otherwise the video opens on a partial P-frame).
        bridge.startRecording(reservation.sessionId(), reservation.sessionToken());

        try {
            // Ensure the bridge has a fresh keyframe (useful when we add screenshots later).
            bridge.forceKeyframe(reservation.sessionId(), reservation.sessionToken());

            // ── Faz 4: App preparation phase ─────────────────────────────────
            // Runs before the step loop. On failure we skip steps but still
            // capture the recording + release the session, so the user gets a
            // FAILED run with a meaningful errorSummary.
            AppPrepResult prep = runAppPrep(run, reservation);
            persistAppPrep(runId, prep);
            if (prep.failed) {
                prepFailed = true;
                anyFailure = true;
            }

            List<StepResultEntity> placeholders = stepResults.findAllByRunIdOrderByOrderIndexAsc(runId);
            if (prepFailed) {
                // Mark every step SKIPPED — orchestrator never ran them.
                for (StepResultEntity p : placeholders) {
                    if (p.getStatus() == StepResultStatus.PENDING) {
                        p.setStatus(StepResultStatus.SKIPPED);
                        stepResults.save(p);
                    }
                }
            }
            for (int i = 0; !prepFailed && i < plan.size(); i++) {
                // Cooperative cancel checkpoint — before kicking off the step. If the
                // user pressed Stop while we were sleeping between steps (or running
                // the previous step) we exit the loop cleanly here. The finally block
                // still uploads the partial recording and marks the run CANCELLED.
                if (cancels.isCancelled(runId)) {
                    cancelled = true;
                    log.info("run {} cancel requested — stopping at step {}", runId, i);
                    break;
                }

                StepEntity step = plan.get(i);
                StepResultEntity row = placeholders.get(i);

                row.setStatus(StepResultStatus.RUNNING);
                row.setStartedAt(Instant.now());
                stepResults.save(row);

                StepRunner.StepResult result = runner.run(step);

                row.setStatus(result.status());
                row.setFinishedAt(Instant.now());
                row.setDurationMs(result.durationMs());
                row.setErrorMessage(result.errorMessage());
                row.setResolvedLocator(result.resolvedLocator());

                // Capture a still-frame the moment a step fails — gives the report a "what
                // did the screen look like" anchor without paying the screenshot cost on
                // every PASSED step. Best-effort: any error here is logged and ignored.
                if (result.status() == StepResultStatus.FAILED || result.status() == StepResultStatus.ERROR) {
                    String url = captureScreenshot(reservation.sessionId(), reservation.sessionToken(), runId, row.getId());
                    if (url != null) row.setScreenshotUrl(url);
                }
                stepResults.save(row);

                if (result.status() != StepResultStatus.PASSED) {
                    anyFailure = true;
                    // Mark remaining steps as SKIPPED so the report is accurate.
                    for (int j = i + 1; j < placeholders.size(); j++) {
                        StepResultEntity skip = placeholders.get(j);
                        skip.setStatus(StepResultStatus.SKIPPED);
                        stepResults.save(skip);
                    }
                    break;
                }

                // Pacing: let the UI settle (animations, network) before the next locator resolve.
                // Skipped after the last step since there's nothing to wait for.
                if (i < plan.size() - 1) {
                    if (adaptive && changesUi(step.getAction())) {
                        if (waitForStable(reservation.sessionId(), reservation.sessionToken(), runId)) {
                            cancelled = true; break;
                        }
                    } else if (!adaptive && interStepDelay > 0) {
                        if (sleepCancellable(interStepDelay, runId)) { cancelled = true; break; }
                    }
                }
            }
        } finally {
            // Order matters: stop+upload the recording BEFORE releasing the session.
            // Releasing closes the device channel; if the recorder is still subscribed
            // it would lose the very last frames. captureAndAttachVideo handles the
            // VIDEO_TAIL_MS pause so the screen's final state lands in the MP4.
            captureAndAttachVideo(reservation.sessionId(), reservation.sessionToken(), runId);
            // ── Faz 4: post-run reset-home ──────────────────────────────────
            // Best-effort — must NOT throw or fail the run that just finished.
            // Done after the recording stops so the final UI state is preserved,
            // and before session release so the session token is still valid.
            if (run.isResetHomeAfter()) {
                try {
                    bridge.resetHome(reservation.sessionId(), reservation.sessionToken(),
                            targetPackageName, run.isKillProcessAfter());
                } catch (Exception e) {
                    log.warn("reset-home for run {} threw: {}", runId, e.toString());
                }
            }
            sessions.release(reservation.sessionId(), userJwt);
            // Free the cancel-flag slot — even if no cancel was requested, removing
            // a non-existent key is a no-op.
            cancels.clear(runId);
        }

        if (cancelled) finalizeCancelled(run);
        else           finalize(run, anyFailure);
    }

    /* ─────────────────── Faz 4: app preparation ─────────────────── */

    /**
     * Decides whether the device needs an APK install/update, performs it, then launches
     * the app. The {@link AppPrepResult} carries enough detail to fill the {@code
     * app_prep_*} columns on the run row.
     *
     * <p>Decision matrix (versionCode comparison):</p>
     * <table>
     *   <tr><th>device state</th><th>target vc</th><th>action</th></tr>
     *   <tr><td>not installed</td><td>any</td><td>INSTALLED</td></tr>
     *   <tr><td>installed, vc &lt; target</td><td>any</td><td>UPDATED</td></tr>
     *   <tr><td>installed, vc == target</td><td>any</td><td>ALREADY_LATEST</td></tr>
     *   <tr><td>installed, vc &gt; target</td><td>any</td><td>ALREADY_LATEST (no downgrade)</td></tr>
     * </table>
     */
    private AppPrepResult runAppPrep(RunEntity run, SessionClient.Reservation reservation) {
        if (run.getTargetAppVersionId() == null) {
            return AppPrepResult.notRequested();
        }
        long startMs = System.currentTimeMillis();
        try {
            AppVersionEntity target = appVersions.findById(run.getTargetAppVersionId())
                    .orElseThrow(() -> ApiException.notFound("app version"));
            AppEntity targetApp = apps.findById(target.getAppId())
                    .orElseThrow(() -> ApiException.notFound("app"));

            log.info("run {} app prep: package={} target vc={}", run.getId(),
                    targetApp.getPackageName(), target.getVersionCode());

            BridgeAppDtos.AppInfo info = bridge.appInfo(reservation.sessionId(),
                    reservation.sessionToken(), targetApp.getPackageName());

            String decision;
            if (info == null || !info.installed()) {
                decision = "INSTALLED";
            } else if (info.versionCode() == null || info.versionCode() < target.getVersionCode()) {
                decision = "UPDATED";
            } else {
                // Equal or newer-installed both mean "use what's there" — we never
                // downgrade because a newer build may carry data migrations.
                decision = "ALREADY_LATEST";
            }

            if ("INSTALLED".equals(decision) || "UPDATED".equals(decision)) {
                String url = storage.publicUrlForApk(target.getStorageKey());
                BridgeAppDtos.InstallResult ir = bridge.installApk(
                        reservation.sessionId(), reservation.sessionToken(),
                        url, target.getSha256(), target.getVersionCode(), targetApp.getPackageName());
                if (ir == null || !ir.ok()) {
                    String detail = (ir != null ? (ir.errorCode() + ": " + ir.errorMessage()) : "no response");
                    return AppPrepResult.failed("install: " + detail,
                            (int) (System.currentTimeMillis() - startMs));
                }
            }

            BridgeAppDtos.LaunchResult lr = bridge.launchApp(reservation.sessionId(),
                    reservation.sessionToken(), targetApp.getPackageName());
            if (lr == null || !lr.ok()) {
                String detail = (lr != null ? lr.errorMessage() : "no response");
                return AppPrepResult.failed("launch: " + detail,
                        (int) (System.currentTimeMillis() - startMs));
            }

            // App is foreground now — give it time to finish its cold-start sequence
            // (splash, lazy-init, first network call). Without this the very first step
            // sometimes fires before the UI tree is settled and misses its locator.
            // Cancellation-aware: if the user pressed Stop during the wait we exit
            // cleanly; the surrounding step loop will see the cancel flag at its first
            // checkpoint and finalize the run as CANCELLED.
            log.info("run {} app warmup: waiting {} ms after launch", run.getId(), APP_WARMUP_MS);
            sleepCancellable(APP_WARMUP_MS, run.getId());

            return AppPrepResult.ok(decision, (int) (System.currentTimeMillis() - startMs));
        } catch (Exception e) {
            log.warn("run {} app prep threw: {}", run.getId(), e.toString());
            return AppPrepResult.failed("exception: " + e.getMessage(),
                    (int) (System.currentTimeMillis() - startMs));
        }
    }

    @Transactional
    protected void persistAppPrep(long runId, AppPrepResult prep) {
        runs.findById(runId).ifPresent(r -> {
            r.setAppPrepStatus(prep.status);
            r.setAppPrepDurationMs(prep.durationMs);
            r.setAppPrepError(prep.error);
            if (prep.failed) {
                // Propagate into errorSummary so the existing reports UI surfaces the
                // failure without needing the app_prep_* columns wired in yet.
                r.setErrorSummary("App preparation failed: " + prep.error);
            }
            runs.save(r);
        });
    }

    /** Outcome of the app prep phase. */
    private record AppPrepResult(String status, Integer durationMs, String error, boolean failed) {
        static AppPrepResult notRequested() { return new AppPrepResult("NOT_REQUESTED", null, null, false); }
        static AppPrepResult ok(String status, int durationMs) { return new AppPrepResult(status, durationMs, null, false); }
        static AppPrepResult failed(String error, int durationMs) { return new AppPrepResult("FAILED", durationMs, error, true); }
    }

    /** Trailing seconds captured after the last step so the final UI state (success toast,
     *  error dialog, screen transition) stays visible in the recording. Without this the
     *  video would cut exactly on the last action and viewers couldn't see the outcome. */
    private static final long VIDEO_TAIL_MS = 2_000;

    /** Stops the bridge recording, uploads the MP4 to MinIO, persists the URL on the run. */
    private void captureAndAttachVideo(long sessionId, String sessionToken, long runId) {
        // Let the screen linger for a moment before we cut — recording is still running.
        try { Thread.sleep(VIDEO_TAIL_MS); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

        try {
            byte[] mp4 = bridge.stopRecording(sessionId, sessionToken);
            if (mp4 == null || mp4.length == 0) {
                log.info("run {} no video produced (recording skipped or empty)", runId);
                return;
            }
            String key = String.format("runs/%d/run.mp4", runId);
            String url = storage.uploadVideo(key, mp4);
            attachVideoUrl(runId, url);
            log.info("run {} video uploaded: {} ({} bytes)", runId, url, mp4.length);
        } catch (Exception e) {
            log.warn("video capture/upload failed for run {}: {}", runId, e.toString());
        }
    }

    @Transactional
    protected void attachVideoUrl(long runId, String url) {
        runs.findById(runId).ifPresent(r -> { r.setVideoUrl(url); runs.save(r); });
    }

    /* ─────────────────────  persistence helpers  ───────────────── */

    @Transactional
    protected void prePopulateResults(RunEntity run, List<StepEntity> plan) {
        for (StepEntity s : plan) {
            stepResults.save(new StepResultEntity(run.getId(), s.getId(), s.getOrderIndex(), s.getAction()));
        }
    }

    @Transactional
    protected void markRunning(RunEntity run, int totalSteps, int scenarioVersion) {
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        run.setTotalSteps(totalSteps);
        run.setScenarioVersion(scenarioVersion);
        runs.save(run);
    }

    @Transactional
    protected void markReserved(RunEntity run, long sessionId) {
        run.setSessionId(sessionId);
        runs.save(run);
    }

    @Transactional
    protected void finalize(RunEntity run, boolean anyFailure) {
        Instant now = Instant.now();
        RunEntity fresh = runs.findById(run.getId()).orElse(run);
        fresh.setFinishedAt(now);
        if (fresh.getStartedAt() != null) {
            fresh.setDurationMs((int) (now.toEpochMilli() - fresh.getStartedAt().toEpochMilli()));
        }
        // Count from step_results so we cover the SKIPPED rows correctly.
        var results = stepResults.findAllByRunIdOrderByOrderIndexAsc(run.getId());
        int passed = 0, failed = 0;
        for (var r : results) {
            if (r.getStatus() == StepResultStatus.PASSED) passed++;
            else if (r.getStatus() == StepResultStatus.FAILED || r.getStatus() == StepResultStatus.ERROR) failed++;
        }
        fresh.setPassedSteps(passed);
        fresh.setFailedSteps(failed);
        fresh.setStatus(anyFailure ? RunStatus.FAILED : RunStatus.PASSED);
        runs.save(fresh);
        log.info("run {} finished: {} ({}p / {}f)", run.getId(), fresh.getStatus(), passed, failed);
    }

    /**
     * Terminal state when the user pressed Stop. Same shape as {@link #finalize}
     * but stamps {@link RunStatus#CANCELLED} and marks any PENDING/RUNNING step
     * results as SKIPPED so the report doesn't show ghost-running rows.
     */
    @Transactional
    protected void finalizeCancelled(RunEntity run) {
        Instant now = Instant.now();
        RunEntity fresh = runs.findById(run.getId()).orElse(run);
        fresh.setFinishedAt(now);
        if (fresh.getStartedAt() != null) {
            fresh.setDurationMs((int) (now.toEpochMilli() - fresh.getStartedAt().toEpochMilli()));
        }

        var results = stepResults.findAllByRunIdOrderByOrderIndexAsc(run.getId());
        int passed = 0, failed = 0;
        for (var r : results) {
            switch (r.getStatus()) {
                case PASSED -> passed++;
                case FAILED, ERROR -> failed++;
                case PENDING, RUNNING -> {
                    r.setStatus(StepResultStatus.SKIPPED);
                    stepResults.save(r);
                }
                default -> { /* SKIPPED already — leave it */ }
            }
        }
        fresh.setPassedSteps(passed);
        fresh.setFailedSteps(failed);
        fresh.setStatus(RunStatus.CANCELLED);
        runs.save(fresh);
        log.info("run {} cancelled by user ({}p / {}f / {} steps total)",
                run.getId(), passed, failed, results.size());
    }

    @Transactional
    protected void fail(long runId, String message) {
        runs.findById(runId).ifPresent(run -> {
            run.setStatus(RunStatus.ERROR);
            run.setErrorSummary(message);
            run.setFinishedAt(Instant.now());
            if (run.getStartedAt() != null) {
                run.setDurationMs((int) (run.getFinishedAt().toEpochMilli() - run.getStartedAt().toEpochMilli()));
            }
            runs.save(run);
        });
    }

    /* ───────────────────────  screenshot capture  ────────────────── */

    /** Returns the public URL of the uploaded PNG, or null on any failure (best-effort). */
    private String captureScreenshot(long sessionId, String sessionToken, long runId, long stepResultId) {
        try {
            byte[] png = bridge.screenshot(sessionId, sessionToken, 10);
            if (png == null || png.length == 0) {
                log.warn("screenshot for run {} step-result {} came back empty", runId, stepResultId);
                return null;
            }
            String key = String.format("runs/%d/step-%d-%d.png", runId, stepResultId, System.currentTimeMillis());
            return storage.uploadScreenshot(key, png);
        } catch (Exception e) {
            log.warn("screenshot upload failed for run {} step-result {}: {}", runId, stepResultId, e.toString());
            return null;
        }
    }

    /* ───────────────────────  adaptive wait  ───────────────────── */

    private static final long ADAPTIVE_INITIAL_DELAY_MS = 200;
    private static final long ADAPTIVE_POLL_INTERVAL_MS = 300;
    private static final long ADAPTIVE_MAX_WAIT_MS      = 5_000;

    /** Only steps that actually mutate the UI deserve a settle-wait. */
    private static boolean changesUi(StepAction a) {
        return switch (a) {
            case CLICK, LONG_PRESS, SWIPE, ENTER_TEXT, CLEAR, PRESS_KEY -> true;
            default -> false;
        };
    }

    /**
     * Cancellation-aware sleep. Slices the wait into small chunks and bails out
     * as soon as the user's stop request arrives. Returns true when cancelled
     * (caller should break the loop), false on a clean elapsed wait.
     */
    private boolean sleepCancellable(long totalMs, long runId) {
        final long sliceMs = 100;
        long remaining = totalMs;
        while (remaining > 0) {
            if (cancels.isCancelled(runId)) return true;
            long chunk = Math.min(sliceMs, remaining);
            try { Thread.sleep(chunk); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); return true; }
            remaining -= chunk;
        }
        return false;
    }

    /**
     * Polls the accessibility tree until two consecutive snapshots are identical (= "settled").
     * Bails out at {@link #ADAPTIVE_MAX_WAIT_MS}. On any inspect failure we just keep polling
     * — the next step's own resolve will surface a real error if the device is truly dead.
     *
     * Returns true if the wait was interrupted by a cancel request (so the caller breaks).
     */
    private boolean waitForStable(long sessionId, String sessionToken, long runId) {
        if (sleepCancellable(ADAPTIVE_INITIAL_DELAY_MS, runId)) return true;

        long deadline = System.currentTimeMillis() + ADAPTIVE_MAX_WAIT_MS;
        String prev = null;
        while (System.currentTimeMillis() < deadline) {
            if (cancels.isCancelled(runId)) return true;
            String sig;
            try {
                JsonNode tree = bridge.inspect(sessionId, sessionToken, 2);
                sig = treeSignature(tree);
            } catch (Exception e) {
                log.debug("adaptive wait inspect failed: {}", e.toString());
                sig = null;
            }
            if (sig != null && sig.equals(prev)) return false; // two matching samples — settled
            prev = sig;
            if (sleepCancellable(ADAPTIVE_POLL_INTERVAL_MS, runId)) return true;
        }
        log.debug("adaptive wait hit cap ({}ms) on session {}", ADAPTIVE_MAX_WAIT_MS, sessionId);
        return false;
    }

    /** Stable string representation of the tree shape — ignores bounds/focus/scroll noise. */
    private static String treeSignature(JsonNode tree) {
        if (tree == null) return "";
        JsonNode root = tree.has("root") ? tree.get("root") : tree;
        StringBuilder sb = new StringBuilder(2048);
        walkSignature(root, sb);
        return sb.toString();
    }

    private static void walkSignature(JsonNode node, StringBuilder sb) {
        if (node == null) return;
        sb.append(node.path("className").asText("")).append('|')
          .append(node.path("resourceId").asText("")).append('|')
          .append(node.path("text").asText("")).append('|')
          .append(node.path("contentDescription").asText("")).append('\n');
        JsonNode children = node.get("children");
        if (children != null && children.isArray()) {
            for (JsonNode c : children) walkSignature(c, sb);
        }
    }
}
