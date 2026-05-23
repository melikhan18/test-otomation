package com.qaplatform.android.automation.service.run;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cancel-flag registry shared between {@link RunService} (caller-facing
 * request entry point) and {@link RunOrchestrator} (the worker that polls it).
 *
 * Lifecycle
 * ─────────
 * The orchestrator never reads from the DB to learn about cancels — DB writes
 * happen at run boundaries (RUNNING / PASSED / FAILED / CANCELLED). A user
 * pressing "Stop" flips this flag instead, and the worker thread picks it up at
 * the next safe checkpoint (between steps or in the adaptive-wait polling
 * loop). The flag stays set until {@link #clear(long)} is invoked when the run
 * finishes, so a stale flag can't accidentally cancel a later run that reuses
 * the same id (shouldn't happen with BIGSERIAL but cheap to guard against).
 *
 * Cluster note: process-local. If automation-service is ever scaled to >1
 * instance, replace with a Redis pub/sub or a DB-driven flag column. For now
 * we trust the run to live on a single host.
 */
@Component
public class RunCancellationRegistry {

    private final Set<Long> cancelled = ConcurrentHashMap.newKeySet();

    /** User requested a stop. Idempotent — calling twice is fine. */
    public void requestCancel(long runId) { cancelled.add(runId); }

    /** Polled by the orchestrator between safe checkpoints. */
    public boolean isCancelled(long runId) { return cancelled.contains(runId); }

    /** Called by the orchestrator's finally block once the run is fully resolved. */
    public void clear(long runId) { cancelled.remove(runId); }
}
