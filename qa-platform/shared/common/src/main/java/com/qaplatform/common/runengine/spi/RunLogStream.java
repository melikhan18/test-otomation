package com.qaplatform.common.runengine.spi;

/**
 * Sink for per-run log lines. The orchestrator emits high-level lifecycle
 * messages here (step started / finished / failed); platform executors may
 * also emit action-level detail.
 *
 * <p>Default implementations forward to SLF4J — production wiring may
 * tee additionally to an artifact log file or a websocket fan-out so the
 * web console can stream live.</p>
 */
public interface RunLogStream {

    void info(String message);

    void warn(String message);

    void error(String message, Throwable cause);

    /** Convenience for tests / dry runs — discards everything. */
    RunLogStream DISCARD = new RunLogStream() {
        @Override public void info(String message) {}
        @Override public void warn(String message) {}
        @Override public void error(String message, Throwable cause) {}
    };
}
