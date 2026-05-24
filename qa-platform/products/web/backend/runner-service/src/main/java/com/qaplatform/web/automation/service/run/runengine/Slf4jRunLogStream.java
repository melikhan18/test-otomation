package com.qaplatform.web.automation.service.run.runengine;

import com.qaplatform.common.runengine.spi.RunLogStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jRunLogStream implements RunLogStream {

    private static final Logger LOG = LoggerFactory.getLogger("WebRunLog");

    private final long runId;

    public Slf4jRunLogStream(long runId) { this.runId = runId; }

    @Override public void info(String message)  { LOG.info("run {} | {}", runId, message); }
    @Override public void warn(String message)  { LOG.warn("run {} | {}", runId, message); }
    @Override public void error(String message, Throwable cause) { LOG.error("run {} | {}", runId, message, cause); }
}
