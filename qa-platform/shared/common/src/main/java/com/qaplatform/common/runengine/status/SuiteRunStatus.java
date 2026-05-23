package com.qaplatform.common.runengine.status;

/**
 * Suite-level aggregate of {@link RunStatus} — mirrors the same terminal set
 * so the run-event stream the reports aggregator consumes (F7) can treat a
 * suite the same way it treats an individual run.
 */
public enum SuiteRunStatus {
    /** Accepted, no child run has started yet. */
    QUEUED,
    /** At least one child run is in-flight. */
    RUNNING,
    /** All child scenarios PASSED. */
    PASSED,
    /** At least one child scenario FAILED. */
    FAILED,
    /** Infra problem aborted the suite before normal completion. */
    ERROR,
    /** User cancelled the suite (children get CANCELLED too). */
    CANCELLED
}
