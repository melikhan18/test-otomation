package com.qaplatform.android.automation.service.run.runengine;

import com.qaplatform.common.runengine.spi.RunLogStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * F6 {@link RunLogStream} that forwards to SLF4J with a per-run prefix.
 *
 * <p>This is the bottom rung of the per-run log fan-out: messages land in
 * the standard container log (visible via {@code docker logs}). Future
 * faz can stack additional sinks (artifact log file in MinIO, WS push to
 * the web console's live console panel) by wrapping this one — the
 * orchestrator still hands a single {@link RunLogStream} to each
 * {@code StepContext}, the underlying implementation can fan out as it
 * pleases.</p>
 */
public class Slf4jRunLogStream implements RunLogStream {

    private static final Logger LOG = LoggerFactory.getLogger("RunLog");

    private final long runId;

    public Slf4jRunLogStream(long runId) { this.runId = runId; }

    @Override
    public void info(String message) {
        LOG.info("run {} | {}", runId, message);
    }

    @Override
    public void warn(String message) {
        LOG.warn("run {} | {}", runId, message);
    }

    @Override
    public void error(String message, Throwable cause) {
        LOG.error("run {} | {}", runId, message, cause);
    }
}
