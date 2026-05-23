package com.qaplatform.common.runengine.spi;

import com.qaplatform.common.runengine.status.StepResultStatus;

/**
 * Result of executing a single step. The orchestrator turns this into a
 * row on the platform's step-results table and uses {@link #status()} to
 * decide whether to keep going, abort the run, or surface a screenshot.
 *
 * @param status            terminal status (never {@code PENDING} or {@code RUNNING})
 * @param errorMessage      null on success; populated on FAILED / ERROR
 * @param resolvedLocator   which locator actually matched (for self-healing
 *                          telemetry); null if the step didn't resolve an element
 * @param screenshotPng     PNG bytes captured on FAILED / ERROR; null otherwise.
 *                          Orchestrator uploads via {@link ArtifactSink}
 *                          rather than the executor doing it inline.
 * @param retriesUsed       how many retries the executor consumed reaching this
 *                          outcome (0 if no retry policy is active)
 */
public record StepOutcome(
        StepResultStatus status,
        String errorMessage,
        String resolvedLocator,
        byte[] screenshotPng,
        int retriesUsed
) {

    public static StepOutcome passed() {
        return new StepOutcome(StepResultStatus.PASSED, null, null, null, 0);
    }

    public static StepOutcome passed(String resolvedLocator) {
        return new StepOutcome(StepResultStatus.PASSED, null, resolvedLocator, null, 0);
    }

    public static StepOutcome failed(String reason) {
        return new StepOutcome(StepResultStatus.FAILED, reason, null, null, 0);
    }

    public static StepOutcome failed(String reason, byte[] screenshotPng) {
        return new StepOutcome(StepResultStatus.FAILED, reason, null, screenshotPng, 0);
    }

    public static StepOutcome error(String reason) {
        return new StepOutcome(StepResultStatus.ERROR, reason, null, null, 0);
    }

    public static StepOutcome skipped() {
        return new StepOutcome(StepResultStatus.SKIPPED, null, null, null, 0);
    }
}
