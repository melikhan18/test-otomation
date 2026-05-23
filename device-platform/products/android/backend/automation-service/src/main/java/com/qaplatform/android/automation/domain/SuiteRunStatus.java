package com.qaplatform.android.automation.domain;

/** Mirrors {@link RunStatus} semantics but at the suite-run aggregation level. */
public enum SuiteRunStatus {
    QUEUED,      // accepted, not yet started
    RUNNING,     // at least one child run is in-flight
    PASSED,      // all child scenarios PASSED
    FAILED,      // at least one child scenario FAILED
    ERROR,       // infra problem (couldn't reserve, etc.) — aborted early
    CANCELLED    // user-cancelled
}
