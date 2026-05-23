package com.qaplatform.android.automation.service.retention;

import com.qaplatform.android.automation.domain.*;
import com.qaplatform.android.automation.service.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Periodically deletes runs (and their MinIO objects) older than the configured retention
 * window. Suite-runs follow the same window — the FK on runs is {@code ON DELETE SET NULL},
 * so an early child deletion temporarily orphans the parent aggregate; the next pass cleans
 * up the parent. Acceptable for a daily job.
 *
 * Driven by {@code app.retention.*} config:
 * <pre>
 *   app.retention.enabled  (default true)
 *   app.retention.days     (default 30)
 *   app.retention.cron     (default "0 0 3 * * *" — daily at 03:00 UTC)
 * </pre>
 * Disable per-environment with {@code APP_RETENTION_ENABLED=false}.
 */
@Service
@ConditionalOnProperty(name = "app.retention.enabled", havingValue = "true", matchIfMissing = true)
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final RunRepository runs;
    private final StepResultRepository stepResults;
    private final SuiteRunRepository suiteRuns;
    private final ObjectStorage storage;

    @Value("${app.retention.days:30}")
    private int retentionDays;

    public RetentionService(RunRepository runs, StepResultRepository stepResults,
                            SuiteRunRepository suiteRuns, ObjectStorage storage) {
        this.runs = runs;
        this.stepResults = stepResults;
        this.suiteRuns = suiteRuns;
        this.storage = storage;
    }

    @Scheduled(cron = "${app.retention.cron:0 0 3 * * *}", zone = "UTC")
    public void purgeOldRuns() {
        if (retentionDays <= 0) {
            log.warn("retention: days <= 0 ({}), skipping to avoid wiping everything", retentionDays);
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        log.info("retention sweep starting: cutoff={} ({} days)", cutoff, retentionDays);

        List<RunEntity> oldRuns = runs.findAllByCreatedAtBefore(cutoff);
        int deletedRuns = 0;
        int deletedObjects = 0;
        for (RunEntity run : oldRuns) {
            deletedObjects += purgeRun(run);
            deletedRuns++;
        }

        long deletedSuiteRuns = suiteRuns.deleteAllByCreatedAtBefore(cutoff);

        log.info("retention sweep done: deleted runs={} suite_runs={} object_store_entries={}",
                deletedRuns, deletedSuiteRuns, deletedObjects);
    }

    /**
     * Per-run cleanup in its own transaction so a failure mid-sweep only loses one run's
     * cleanup, not the whole batch. Returns the count of S3 objects we successfully removed.
     */
    @Transactional
    protected int purgeRun(RunEntity run) {
        int objs = 0;
        // Step screenshots
        var steps = stepResults.findAllByRunIdOrderByOrderIndexAsc(run.getId());
        for (StepResultEntity sr : steps) {
            if (sr.getScreenshotUrl() != null && storage.deleteByUrl(sr.getScreenshotUrl())) objs++;
        }
        // Run video
        if (run.getVideoUrl() != null && storage.deleteByUrl(run.getVideoUrl())) objs++;

        stepResults.deleteAllByRunId(run.getId());
        runs.delete(run);
        return objs;
    }
}
