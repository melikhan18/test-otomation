package com.qaplatform.android.automation.service.run.runengine;

import com.qaplatform.android.automation.service.storage.ObjectStorage;
import com.qaplatform.common.runengine.spi.ArtifactSink;

/**
 * F6 {@link ArtifactSink} backed by Android's existing {@link ObjectStorage}
 * (MinIO/S3 wrapper).
 *
 * <p>The orchestrator constructs one of these per run and passes it on the
 * {@link com.qaplatform.common.runengine.spi.StepContext}. Today the
 * orchestrator still calls {@code ObjectStorage} directly for video upload
 * and post-hoc step-failure screenshots; the sink exists so that
 * <strong>executors</strong> can also drop artifacts through the SPI
 * (returning {@link com.qaplatform.common.runengine.spi.StepOutcome} with
 * a {@code screenshotPng} byte array, for instance), without coupling to
 * the underlying storage client.</p>
 *
 * <p>Key conventions match the legacy paths in {@code RunOrchestrator}:
 * {@code runs/{runId}/run.mp4} for video, {@code runs/{runId}/step-{stepResultId}-{timestamp}.png}
 * for screenshots.</p>
 */
public class ObjectStorageArtifactSink implements ArtifactSink {

    private final ObjectStorage storage;

    public ObjectStorageArtifactSink(ObjectStorage storage) {
        this.storage = storage;
    }

    @Override
    public String uploadScreenshot(long runId, long stepResultId, byte[] png) {
        if (png == null || png.length == 0) return null;
        String key = "runs/" + runId + "/step-" + stepResultId + "-" + System.currentTimeMillis() + ".png";
        return storage.uploadScreenshot(key, png);
    }

    @Override
    public String uploadVideo(long runId, byte[] mp4) {
        if (mp4 == null || mp4.length == 0) return null;
        return storage.uploadVideo("runs/" + runId + "/run.mp4", mp4);
    }
}
