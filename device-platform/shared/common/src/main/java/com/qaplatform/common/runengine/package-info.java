/**
 * Platform-agnostic run engine — shared kernel reused by every platform
 * stack (Android today; iOS / Backend / Web in the future).
 *
 * <h2>Scope (F6 skeleton)</h2>
 *
 * The package today owns two surfaces:
 *
 * <ul>
 *   <li>{@code status/} — the {@code RunStatus}, {@code SuiteRunStatus},
 *       {@code StepResultStatus} enums. Every platform persists these
 *       value sets so the cross-platform reports aggregator (F7) doesn't
 *       need a translation table.</li>
 *   <li>{@code spi/} — the extension interfaces a platform stack
 *       implements: {@code StepExecutor} (the single dispatch point),
 *       {@code StepContext} / {@code StepOutcome} / {@code RunStep}
 *       (data flowing between orchestrator and executor), and
 *       {@code ArtifactSink} / {@code RunLogStream} /
 *       {@code CancellationToken} (side-effect interfaces).</li>
 * </ul>
 *
 * <p>The orchestrator itself still lives inside
 * {@code android-automation-service} in F6; a later faz will extract it
 * here as a platform-agnostic driver loop that calls
 * {@link com.qaplatform.common.runengine.spi.StepExecutor} for each step.
 * The contract is already in place so that extraction is purely a move,
 * not a redesign.</p>
 */
package com.qaplatform.common.runengine;
