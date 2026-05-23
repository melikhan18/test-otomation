package com.devicefarm.automation.domain;

public enum RunStatus {
    QUEUED,      // accepted, waiting for executor
    RUNNING,     // execution underway
    PASSED,      // all steps passed
    FAILED,      // at least one step failed
    ERROR,       // infrastructure error (session reservation, agent offline, etc.)
    CANCELLED    // user cancelled
}
