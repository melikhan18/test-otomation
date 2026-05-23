package com.qaplatform.common.runengine.spi;

/**
 * Cooperative cancellation signal. The orchestrator polls this between
 * steps and inside long sleeps; executors may also check it during
 * blocking waits (e.g. polling the inspect tree) so cancellation latency
 * stays bounded.
 *
 * <p>Implementations are typically backed by a registry / Redis flag /
 * atomic boolean — they must be cheap to call (no I/O on the hot path).</p>
 */
@FunctionalInterface
public interface CancellationToken {

    boolean isCancelled();

    /** Convenience for tests / unconditional runs. */
    CancellationToken NEVER = () -> false;
}
