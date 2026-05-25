package com.qaplatform.web.automation.service.run;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.options.RecordVideoSize;
import com.microsoft.playwright.options.ViewportSize;
import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.runengine.spi.ArtifactSink;
import com.qaplatform.common.runengine.spi.CancellationToken;
import com.qaplatform.common.runengine.spi.RunLogStream;
import com.qaplatform.common.runengine.spi.StepContext;
import com.qaplatform.common.runengine.spi.StepExecutor;
import com.qaplatform.common.runengine.spi.StepOutcome;
import com.qaplatform.common.runengine.status.RunStatus;
import com.qaplatform.common.runengine.status.StepResultStatus;
import com.qaplatform.web.automation.browser.BrowserCatalog;
import com.qaplatform.web.automation.browser.BrowserProfile;
import com.qaplatform.web.automation.domain.WebRunEntity;
import com.qaplatform.web.automation.domain.WebRunRepository;
import com.qaplatform.web.automation.domain.WebScenarioEntity;
import com.qaplatform.web.automation.domain.WebScenarioRepository;
import com.qaplatform.web.automation.domain.WebStepEntity;
import com.qaplatform.web.automation.domain.WebStepRepository;
import com.qaplatform.web.automation.domain.WebStepResultEntity;
import com.qaplatform.web.automation.domain.WebStepResultRepository;
import com.qaplatform.web.automation.service.run.runengine.ObjectStorageArtifactSink;
import com.qaplatform.web.automation.service.run.runengine.Slf4jRunLogStream;
import com.qaplatform.web.automation.service.run.runengine.StepEntityRunStep;
import com.qaplatform.web.automation.service.run.runengine.WebStepExecutor;
import com.qaplatform.web.automation.service.storage.ObjectStorage;
import com.qaplatform.web.automation.tenancy.ProjectLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Per-run lifecycle for the web stack — the analog of Android's
 * {@code RunOrchestrator}, but much shorter because there's no device
 * reservation, no bridge fan-out, and no app prep phase. Steps run inside
 * the orchestrator process: one fresh Playwright + Browser + BrowserContext +
 * Page tuple per run, dispatched through F6's {@link StepExecutor} SPI.
 *
 * <p>Pipeline:</p>
 * <ol>
 *   <li>Look up the scenario + steps; snapshot step_results placeholders.</li>
 *   <li>Resolve the browser profile from the catalog.</li>
 *   <li>{@code Playwright.create()} → launch the engine → newContext with
 *       viewport / locale / UA from the profile → start tracing + video
 *       recording → newPage().</li>
 *   <li>Iterate steps through {@code StepExecutor.execute(...)} returning
 *       {@link StepOutcome}.</li>
 *   <li>On FAILED / ERROR: capture page.screenshot() and upload.</li>
 *   <li>Tear down — stop tracing (writes trace.zip), close context (finalises
 *       video.webm), close browser, close Playwright. Upload artifacts and
 *       persist their public URLs on the run row.</li>
 *   <li>Mark terminal status and fire-and-forget push to reports-aggregator.</li>
 * </ol>
 */
@Service
public class WebRunOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(WebRunOrchestrator.class);

    /** Small pool — most runs are I/O bound (browser → target site). */
    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "web-run-orchestrator");
        t.setDaemon(true);
        return t;
    });

    private final WebRunRepository runs;
    private final WebScenarioRepository scenarios;
    private final WebStepRepository steps;
    private final WebStepResultRepository stepResults;
    private final BrowserCatalog browsers;
    private final ObjectStorage storage;
    private final ProjectLookup projectLookup;
    private final WebReportsPublisher reportsPublisher;
    private final WebStepExecutor stepExecutorFactory;
    private final boolean headless;
    private final int defaultStepTimeoutMs;
    private final Path runsTmpDir;

    public WebRunOrchestrator(WebRunRepository runs,
                              WebScenarioRepository scenarios,
                              WebStepRepository steps,
                              WebStepResultRepository stepResults,
                              BrowserCatalog browsers,
                              ObjectStorage storage,
                              ProjectLookup projectLookup,
                              WebReportsPublisher reportsPublisher,
                              WebStepExecutor stepExecutorFactory,
                              @Value("${app.browser.headless:true}") boolean headless,
                              @Value("${app.browser.step-timeout-ms:30000}") int defaultStepTimeoutMs,
                              @Value("${app.runs-tmp-dir:/tmp/qa-platform-web-runs}") String runsTmpDir) {
        this.runs = runs;
        this.scenarios = scenarios;
        this.steps = steps;
        this.stepResults = stepResults;
        this.browsers = browsers;
        this.storage = storage;
        this.projectLookup = projectLookup;
        this.reportsPublisher = reportsPublisher;
        this.stepExecutorFactory = stepExecutorFactory;
        this.headless = headless;
        this.defaultStepTimeoutMs = defaultStepTimeoutMs;
        this.runsTmpDir = Paths.get(runsTmpDir);
    }

    public void submit(long runId) {
        pool.submit(() -> {
            // Bind runId to the SLF4J MDC for the lifetime of this run so
            // every log line emitted by execute() (and anything it calls)
            // carries it as a structured field. Loki can then filter on
            // `| json | runId="N"` instead of regex-scanning the message.
            try (org.slf4j.MDC.MDCCloseable rc = org.slf4j.MDC.putCloseable("runId", String.valueOf(runId))) {
                try { execute(runId); }
                catch (Throwable t) {
                    log.error("web run {} crashed", runId, t);
                    fail(runId, "orchestrator crash: " + t.getMessage());
                }
            }
        });
    }

    private void execute(long runId) {
        WebRunEntity run = runs.findById(runId).orElse(null);
        if (run == null) { log.warn("web run {} disappeared before execute()", runId); return; }

        WebScenarioEntity scenario = run.getScenarioId() == null ? null
                : scenarios.findById(run.getScenarioId()).orElse(null);
        if (scenario == null) {
            fail(runId, "scenario not found");
            return;
        }

        List<WebStepEntity> plan = steps.findAllByScenarioIdOrderByOrderIndexAsc(scenario.getId());
        if (plan.isEmpty()) {
            fail(runId, "scenario has no steps");
            return;
        }

        BrowserProfile profile = browsers.find(run.getBrowserProfileId()).orElse(null);
        if (profile == null) {
            fail(runId, "browser profile '" + run.getBrowserProfileId() + "' not in catalog");
            return;
        }

        markRunning(run, plan.size(), scenario.getVersion());
        prePopulateResults(run, plan);

        Long companyId = projectLookup.find(run.getProjectId()).map(ProjectLookup.Info::companyId).orElse(null);
        CancellationToken cancelToken = CancellationToken.NEVER;        // v1: no cancel button yet
        ArtifactSink artifactSink = new ObjectStorageArtifactSink(storage);
        RunLogStream runLog = new Slf4jRunLogStream(runId);
        StepContext stepContext = new StepContext(
                runId,
                companyId == null ? 0L : companyId,
                run.getProjectId(),
                "WEB",
                run.getEnvironment(),
                new HashMap<>(),
                cancelToken,
                runLog,
                artifactSink);

        Path videoDir = runsTmpDir.resolve(String.valueOf(runId));
        Path tracePath = videoDir.resolve("trace.zip");
        try { Files.createDirectories(videoDir); } catch (IOException e) {
            fail(runId, "cannot create scratch dir: " + e.getMessage());
            return;
        }

        boolean anyFailure = false;

        try (Playwright pw = Playwright.create()) {
            BrowserType type = browserType(pw, profile.engine());
            try (Browser browser = type.launch(new BrowserType.LaunchOptions().setHeadless(headless))) {

                // Playwright otherwise downscales the recording to ~800px-wide
                // for performance; pin the video size to the viewport so the
                // captured MP4 matches what the test actually saw (e.g. a
                // 1920×1080 desktop profile records at 1920×1080, not 800×450).
                Browser.NewContextOptions ctxOpts = new Browser.NewContextOptions()
                        .setViewportSize(new ViewportSize(profile.width(), profile.height()))
                        .setDeviceScaleFactor(profile.deviceScaleFactor())
                        .setIsMobile(profile.isMobile())
                        .setLocale(profile.locale())
                        .setTimezoneId(profile.timezone())
                        .setRecordVideoDir(videoDir)
                        .setRecordVideoSize(new RecordVideoSize(profile.width(), profile.height()));
                if (profile.userAgent() != null && !profile.userAgent().isBlank()) {
                    ctxOpts.setUserAgent(profile.userAgent());
                }

                BrowserContext ctx = browser.newContext(ctxOpts);
                ctx.tracing().start(new Tracing.StartOptions()
                        .setScreenshots(true)
                        .setSnapshots(true)
                        .setSources(false));

                Page page = ctx.newPage();
                StepExecutor executor = stepExecutorFactory.forRun(page, defaultStepTimeoutMs);

                List<WebStepResultEntity> placeholders = stepResults.findAllByRunIdOrderByOrderIndexAsc(runId);
                for (int i = 0; i < plan.size(); i++) {
                    WebStepEntity step = plan.get(i);
                    WebStepResultEntity row = placeholders.get(i);

                    row.setStatus(StepResultStatus.RUNNING);
                    row.setStartedAt(Instant.now());
                    stepResults.save(row);

                    long startedAtMs = System.currentTimeMillis();
                    StepOutcome outcome = executor.execute(StepEntityRunStep.of(step), stepContext);
                    int durationMs = (int) (System.currentTimeMillis() - startedAtMs);

                    row.setStatus(outcome.status());
                    row.setFinishedAt(Instant.now());
                    row.setDurationMs(durationMs);
                    row.setErrorMessage(outcome.errorMessage());

                    if (outcome.status() == StepResultStatus.FAILED || outcome.status() == StepResultStatus.ERROR) {
                        try {
                            byte[] png = page.screenshot();
                            String url = storage.uploadScreenshot(
                                    "runs/" + runId + "/step-" + row.getId() + "-" + System.currentTimeMillis() + ".png",
                                    png);
                            if (url != null) row.setScreenshotUrl(url);
                        } catch (Exception sx) {
                            log.warn("run {} step {} screenshot capture failed: {}", runId, row.getId(), sx.getMessage());
                        }
                    }
                    stepResults.save(row);

                    if (outcome.status() != StepResultStatus.PASSED) {
                        anyFailure = true;
                        // mark remaining as SKIPPED so the report is accurate
                        for (int j = i + 1; j < placeholders.size(); j++) {
                            WebStepResultEntity skip = placeholders.get(j);
                            skip.setStatus(StepResultStatus.SKIPPED);
                            stepResults.save(skip);
                        }
                        break;
                    }
                }

                // Tear down — order matters: stop tracing first (writes trace.zip),
                // then close context (finalises video), then close browser.
                try { ctx.tracing().stop(new Tracing.StopOptions().setPath(tracePath)); }
                catch (Exception te) { log.warn("run {} tracing stop failed: {}", runId, te.getMessage()); }

                Path videoFile = null;
                try { if (page.video() != null) videoFile = page.video().path(); }
                catch (Exception ve) { log.warn("run {} video path lookup failed: {}", runId, ve.getMessage()); }

                ctx.close();

                // Upload trace + video AFTER context close so they're finalised on disk.
                String traceUrl = tryUpload(tracePath, bytes -> storage.uploadTrace("runs/" + runId + "/trace.zip", bytes), runId, "trace");
                String videoUrl = videoFile == null ? null
                        : tryUpload(videoFile, bytes -> storage.uploadVideo("runs/" + runId + "/run.webm", bytes), runId, "video");

                if (videoUrl != null || traceUrl != null) attachArtifactUrls(runId, videoUrl, traceUrl);
            }
        } catch (Exception e) {
            log.error("web run {} playwright pipeline failed", runId, e);
            fail(runId, "playwright: " + e.getMessage());
            return;
        } finally {
            // Best-effort scratch cleanup. Don't propagate errors here — the
            // artifacts are already in MinIO by this point.
            try { deleteRecursive(videoDir); } catch (Exception ignore) {}
        }

        finalize(run, anyFailure);
        reportsPublisher.publishAsync(runId);
    }

    /* ─────────────────────── helpers ───────────────────────────────────── */

    private static BrowserType browserType(Playwright pw, String engine) {
        return switch (engine) {
            case "chromium" -> pw.chromium();
            case "firefox"  -> pw.firefox();
            case "webkit"   -> pw.webkit();
            default -> throw ApiException.badRequest("unsupported browser engine: " + engine);
        };
    }

    @FunctionalInterface
    private interface UploadFn { String apply(byte[] bytes) throws Exception; }

    private static String tryUpload(Path file, UploadFn upload, long runId, String label) {
        try {
            if (!Files.exists(file)) {
                LoggerFactory.getLogger(WebRunOrchestrator.class)
                        .warn("run {} {} file missing: {}", runId, label, file);
                return null;
            }
            byte[] bytes = Files.readAllBytes(file);
            return upload.apply(bytes);
        } catch (Exception e) {
            LoggerFactory.getLogger(WebRunOrchestrator.class)
                    .warn("run {} {} upload failed: {}", runId, label, e.getMessage());
            return null;
        }
    }

    private static void deleteRecursive(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (var s = Files.walk(p)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(child -> {
                try { Files.deleteIfExists(child); } catch (IOException ignore) {}
            });
        }
    }

    /* ─────────────────────── persistence ───────────────────────────────── */

    @Transactional
    protected void markRunning(WebRunEntity run, int totalSteps, int scenarioVersion) {
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        run.setTotalSteps(totalSteps);
        run.setScenarioVersion(scenarioVersion);
        runs.save(run);
    }

    @Transactional
    protected void prePopulateResults(WebRunEntity run, List<WebStepEntity> plan) {
        for (WebStepEntity s : plan) {
            stepResults.save(new WebStepResultEntity(run.getId(), s.getId(), s.getOrderIndex(), s.getAction()));
        }
    }

    @Transactional
    protected void attachArtifactUrls(long runId, String videoUrl, String traceUrl) {
        runs.findById(runId).ifPresent(r -> {
            if (videoUrl != null) r.setVideoUrl(videoUrl);
            if (traceUrl != null) r.setTraceUrl(traceUrl);
            runs.save(r);
        });
    }

    @Transactional
    protected void finalize(WebRunEntity run, boolean anyFailure) {
        Instant now = Instant.now();
        WebRunEntity fresh = runs.findById(run.getId()).orElse(run);
        fresh.setFinishedAt(now);
        if (fresh.getStartedAt() != null) {
            fresh.setDurationMs((int) (now.toEpochMilli() - fresh.getStartedAt().toEpochMilli()));
        }
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
        log.info("web run {} finished: {} ({}p / {}f)", run.getId(), fresh.getStatus(), passed, failed);
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
        reportsPublisher.publishAsync(runId);
    }
}
