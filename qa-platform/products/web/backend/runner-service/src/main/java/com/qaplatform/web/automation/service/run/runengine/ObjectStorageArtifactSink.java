package com.qaplatform.web.automation.service.run.runengine;

import com.qaplatform.common.runengine.spi.ArtifactSink;
import com.qaplatform.web.automation.service.storage.ObjectStorage;

/**
 * Maps F6 {@link ArtifactSink} to the web stack's MinIO wrapper. The
 * orchestrator builds one per run and hands it on the {@code StepContext}
 * so any future executor can drop artifacts through the SPI instead of
 * coupling to {@code ObjectStorage} directly.
 *
 * <p>Key conventions match {@code WebRunOrchestrator}'s own upload paths:
 * {@code runs/{runId}/step-{stepResultId}-{timestamp}.png} for screenshots
 * and {@code runs/{runId}/run.webm} for the run video.</p>
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
        return storage.uploadVideo("runs/" + runId + "/run.webm", mp4);
    }
}
