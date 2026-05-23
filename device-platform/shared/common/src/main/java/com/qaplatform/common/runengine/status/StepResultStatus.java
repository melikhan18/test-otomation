package com.qaplatform.common.runengine.status;

/**
 * Lifecycle of a single step within a run.
 *
 * <p>{@code PENDING} is the only non-terminal value created up-front; the
 * web console uses it to render placeholder rows while the executor walks
 * the graph. SKIPPED happens when an earlier step aborts the run before
 * the executor reaches this one.</p>
 */
public enum StepResultStatus {
    /** Created up-front; not yet picked up. */
    PENDING,
    /** Currently being executed. */
    RUNNING,
    /** Action succeeded / assertion held. */
    PASSED,
    /** Action or assertion failed. */
    FAILED,
    /** Run aborted before reaching this step. */
    SKIPPED,
    /** Unexpected exception (bug, transport failure, etc.). */
    ERROR
}
