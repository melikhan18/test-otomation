package com.qaplatform.common.runengine.spi;

/**
 * Minimal step contract the platform-agnostic orchestrator needs to iterate
 * a run. Each platform stack provides its own concrete step representation
 * (Android's {@code StepEntity}, iOS's {@code IosStepEntity}, etc.) and
 * adapts it to this interface when handing steps to the engine.
 *
 * <p>The orchestrator never interprets {@link #action()} or {@link #payload()} —
 * those are opaque strings dispatched to a platform-specific
 * {@link StepExecutor}.</p>
 */
public interface RunStep {

    /** Persistence id of this step within the platform's own schema. */
    long id();

    /** Position within the run; orchestrator iterates ascending. */
    int orderIndex();

    /**
     * Platform-specific action discriminator (e.g. Android's {@code CLICK},
     * {@code ASSERT_TEXT_EQUALS}, …). The executor maps this to a handler.
     */
    String action();

    /**
     * Opaque per-step config — typically JSON. Platform's executor parses
     * it according to the action.
     */
    String payload();

    /**
     * Per-step timeout in milliseconds. {@code null} means "use the
     * engine default" — the orchestrator decides.
     */
    Integer timeoutMs();
}
