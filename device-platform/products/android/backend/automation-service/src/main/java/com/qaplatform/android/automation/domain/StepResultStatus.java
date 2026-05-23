package com.qaplatform.android.automation.domain;

public enum StepResultStatus {
    PENDING,    // not started yet (used by UI to render a placeholder row)
    RUNNING,    // currently dispatching/waiting
    PASSED,
    FAILED,     // the assertion / action failed
    SKIPPED,    // run aborted before reaching this step
    ERROR       // unexpected exception
}
