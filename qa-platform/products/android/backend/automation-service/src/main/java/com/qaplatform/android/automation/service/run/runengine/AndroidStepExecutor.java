package com.qaplatform.android.automation.service.run.runengine;

import com.qaplatform.android.automation.domain.StepEntity;
import com.qaplatform.android.automation.domain.StepRepository;
import com.qaplatform.android.automation.service.run.StepRunner;
import com.qaplatform.common.runengine.spi.StepExecutor;
import com.qaplatform.common.runengine.spi.StepOutcome;
import org.springframework.stereotype.Component;

/**
 * Android's bridge to the F6 {@link StepExecutor} SPI.
 *
 * <p>{@link StepRunner} is the synchronous, per-run worker that actually
 * drives the device — it can't be a singleton because it binds the device's
 * sessionId + sessionToken + environment at construction. {@link RunOrchestrator
 * com.qaplatform.android.automation.service.run.RunOrchestrator} creates one
 * StepRunner per run; this factory turns that StepRunner into a stateless
 * {@code StepExecutor} lambda that satisfies the platform-agnostic contract
 * so the orchestrator dispatches steps through the SPI rather than calling
 * StepRunner directly.</p>
 *
 * <p>What the lambda does on each {@code execute}:</p>
 * <ol>
 *   <li>Re-loads the {@link StepEntity} by id — the {@link RunStep
 *       com.qaplatform.common.runengine.spi.RunStep} adapter only carries
 *       the id + action + JSON payload, but Android's executor needs the
 *       full typed entity (timeoutMs, targetElementId, screenshotAfter, …),
 *       so we fetch it here.</li>
 *   <li>Delegates to the existing {@link StepRunner#run(StepEntity)} which
 *       has the locator-resolution + bridge-control logic already.</li>
 *   <li>Maps the internal {@link StepRunner.StepResult} record onto the
 *       cross-platform {@link StepOutcome} record.</li>
 * </ol>
 *
 * <p>Why a factory rather than a singleton bean?</p>
 * <ul>
 *   <li>StepRunner is per-run; if we made AndroidStepExecutor itself the
 *       StepExecutor bean it would need mutable per-run state, which doesn't
 *       compose with @Async or future multi-platform orchestrator extraction.</li>
 *   <li>The factory keeps Spring DI honest (the bean is stateless) and lets
 *       the orchestrator pass in the run-bound StepRunner explicitly.</li>
 * </ul>
 */
@Component
public class AndroidStepExecutor {

    private final StepRepository steps;

    public AndroidStepExecutor(StepRepository steps) {
        this.steps = steps;
    }

    /**
     * Bind a per-run {@link StepRunner} and return a {@link StepExecutor} that
     * dispatches through it. The returned lambda is safe to call for any
     * {@link RunStep} that belongs to this run; the {@link StepEntity} is
     * re-loaded by id each time.
     */
    public StepExecutor forRun(StepRunner runner) {
        return (runStep, ctx) -> {
            StepEntity entity = steps.findById(runStep.id()).orElse(null);
            if (entity == null) {
                ctx.log().warn("step " + runStep.id() + " not found in DB; aborting");
                return StepOutcome.error("step " + runStep.id() + " not found");
            }
            StepRunner.StepResult result = runner.run(entity);
            return new StepOutcome(
                    result.status(),
                    result.errorMessage(),
                    result.resolvedLocator(),
                    null,    // screenshots are captured + uploaded by the orchestrator post-hoc
                    0        // per-step retries not tracked yet — see roadmap D
            );
        };
    }
}
