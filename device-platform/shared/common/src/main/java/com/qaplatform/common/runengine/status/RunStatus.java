package com.qaplatform.common.runengine.status;

/**
 * Lifecycle of a single test run, platform-agnostic.
 *
 * <p>The same state machine drives Android scenario runs today and will drive
 * iOS / Backend / Web runs once those stacks land — every platform reuses
 * these terminal values so {@code reports-aggregator-service} (F7) can roll
 * runs up across stacks without per-platform translation.</p>
 *
 * <pre>
 *   QUEUED ──► RUNNING ──► PASSED
 *                       ├─► FAILED
 *                       ├─► ERROR
 *                       └─► CANCELLED
 * </pre>
 */
public enum RunStatus {
    /** Accepted, waiting for an executor to pick it up. */
    QUEUED,
    /** Executor is iterating the step graph. */
    RUNNING,
    /** All steps passed. */
    PASSED,
    /** At least one step failed an assertion or action. */
    FAILED,
    /** Infrastructure error (couldn't reserve a device, agent offline, etc.). */
    ERROR,
    /** User cancelled before completion. */
    CANCELLED
}
