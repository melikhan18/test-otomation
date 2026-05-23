package com.qaplatform.common.runengine.spi;

/**
 * The single extension point that platform stacks implement.
 *
 * <p>Each platform's automation-service registers exactly one executor
 * bean; the platform-agnostic orchestrator (still in-tree in F6, will be
 * extracted in a later faz) hands each {@link RunStep} to {@link #execute}
 * along with the per-run {@link StepContext}, and turns the returned
 * {@link StepOutcome} into a persisted row.</p>
 *
 * <p>Implementations are responsible for:
 * <ul>
 *   <li>Parsing the step's {@link RunStep#payload()} JSON,</li>
 *   <li>Resolving any platform-side resources (locators, test-data, …),</li>
 *   <li>Dispatching the action against the device / API / browser,</li>
 *   <li>Honouring {@link StepContext#cancel()} on long waits,</li>
 *   <li>Producing a non-null {@link StepOutcome} — never throwing for
 *       expected failures (assertion miss → {@code failed}, not exception).</li>
 * </ul>
 *
 * <p>Executors should not write to the database or push artifacts directly;
 * they signal those through {@link StepOutcome} and the side-effect
 * interfaces on {@link StepContext}. This keeps platforms swappable and
 * the engine testable.</p>
 */
@FunctionalInterface
public interface StepExecutor {

    StepOutcome execute(RunStep step, StepContext ctx);
}
