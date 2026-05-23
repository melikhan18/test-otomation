package com.qaplatform.common.runengine.spi;

/**
 * Where the engine drops binary artifacts produced during a run —
 * screenshots from failed steps, the run video from the device bridge,
 * any future per-step recordings, etc.
 *
 * <p>Concrete bindings push to MinIO / S3 today and return the public URL
 * (anonymous-download bucket). Returning {@code null} from any upload is
 * allowed and means "drop the artifact" — useful for tests or storage
 * outages where we'd rather lose the artifact than fail the run.</p>
 */
public interface ArtifactSink {

    /**
     * @param runId          owning run
     * @param stepResultId   owning step result, for path uniqueness
     * @param png            raw PNG bytes
     * @return public URL of the uploaded object, or {@code null} if dropped
     */
    String uploadScreenshot(long runId, long stepResultId, byte[] png);

    /**
     * @param runId  owning run
     * @param mp4    raw MP4 bytes (single chunk; chunked upload is the
     *               implementation's concern, not the contract's)
     * @return public URL of the uploaded object, or {@code null} if dropped
     */
    String uploadVideo(long runId, byte[] mp4);
}
