package com.qaplatform.common.runengine.spi;

import java.util.Map;

/**
 * Per-run scope handed to every {@link StepExecutor#execute} call.
 *
 * <p>Holds the tenant identifiers the executor needs to look up
 * platform-side resources (test-data rows, elements, app metadata), the
 * mutable variable bag captured during the run, and the side-effect
 * sinks (log + artifact + cancellation).</p>
 *
 * <p>The {@code vars} map is intentionally mutable so a step that
 * captures a value (e.g. {@code ASSIGN}, {@code CAPTURE_TEXT}) can put
 * it back for later steps to read. Concurrent runs each get their own
 * map — implementations are not required to be thread-safe.</p>
 *
 * @param runId        owning run
 * @param companyId    tenant company
 * @param projectId    tenant project
 * @param platform     "ANDROID" | "IOS" | "BACKEND" | "WEB" — for log tagging
 * @param environment  test-data environment selector ("default", "staging", …)
 * @param vars         mutable variable bag, captured/replaced across steps
 * @param cancel       cooperative cancellation token (poll on long waits)
 * @param log          per-run log sink (orchestrator + executor share it)
 * @param artifacts    artifact sink (screenshots, videos)
 */
public record StepContext(
        long runId,
        long companyId,
        long projectId,
        String platform,
        String environment,
        Map<String, String> vars,
        CancellationToken cancel,
        RunLogStream log,
        ArtifactSink artifacts
) {}
