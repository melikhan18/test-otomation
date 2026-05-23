package com.qaplatform.android.bridge.recording;

import com.qaplatform.android.bridge.hub.DeviceChannel;
import com.qaplatform.android.bridge.protocol.Frame;
import com.qaplatform.android.bridge.protocol.FrameType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Records the agent's H.264 stream to a temp file and, on stop, remuxes it into MP4 via
 * {@code ffmpeg -c copy}. We never re-encode — the agent already emitted browser-playable
 * H.264 Baseline; the wrapper just adds an MP4 container with timestamps so HTML5 video
 * can seek.
 *
 * Lifecycle: one instance per recording. {@link #start} subscribes to the device channel's
 * video stream; {@link #finishAndRemux} disposes the subscription, invokes ffmpeg, and
 * returns the MP4 bytes. Temp files are best-effort cleaned afterwards.
 */
public class VideoRecorder {

    private static final Logger log = LoggerFactory.getLogger(VideoRecorder.class);
    private static final ObjectMapper M = new ObjectMapper();

    /** Hard upper bound — if ffmpeg hangs we'd rather lose the video than the run. */
    private static final int FFMPEG_TIMEOUT_SECONDS = 90;

    /** Sensible default if STREAM_METADATA never arrives (older agents, dropped frame). */
    private static final int DEFAULT_FPS = 30;

    private final long sessionId;
    private final Path tempH264;
    private final Path tempMp4;
    private final FileChannel out;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private Disposable subscription;
    private int framesWritten = 0;
    private int keyframesSeen = 0;
    private int deltasSeen = 0;
    private int targetFps = DEFAULT_FPS;
    /**
     * Wall-clock anchor — captured on the first frame we *actually write* (not on
     * subscription start). Used together with {@link #lastFrameNanos} to compute
     * the effective FPS so dropped frames don't fast-forward playback.
     */
    private long firstFrameNanos = 0;
    private long lastFrameNanos  = 0;
    /**
     * H.264 demands the bytestream start with SPS+PPS+IDR. If we accept delta frames
     * before the first keyframe, ffmpeg sees "non-existing PPS 0 referenced" and bails
     * with "dimensions not set". So we drop everything until we've anchored on a keyframe.
     */
    private boolean anchored = false;

    public VideoRecorder(long sessionId, Path baseDir) throws IOException {
        this.sessionId = sessionId;
        Files.createDirectories(baseDir);
        String stem = "rec-" + sessionId + "-" + System.nanoTime();
        this.tempH264 = baseDir.resolve(stem + ".h264");
        this.tempMp4  = baseDir.resolve(stem + ".mp4");
        this.out = FileChannel.open(tempH264, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    /** Idempotent: a second call after start is a no-op. */
    public synchronized void start(DeviceChannel channel) {
        if (subscription != null) return;
        // Use a raw listener instead of the web multicast sink. Web subscribers
        // (screenshot endpoint, inspect endpoint, extra browser sessions) and the recorder
        // were previously competing for the same per-subscriber backpressure buffer; a
        // big SCREENSHOT_RESPONSE could starve the recorder right after it anchored on a
        // single keyframe. Raw listeners are fanned out synchronously from publishFromAgent
        // and don't share that buffer at all.
        subscription = channel.addRawListener(this::onFrame);
        // Belt-and-suspenders: ask the agent for a fresh keyframe right now. The primer's
        // cached keyframe may be stale (or absent if we connected just now); a freshly
        // requested one arrives within ~50ms and gives us a guaranteed SPS+PPS+IDR anchor.
        channel.requestKeyframe();
    }

    private void onFrame(Frame f) {
        try {
            if (f.type() == FrameType.STREAM_METADATA) {
                try {
                    JsonNode node = M.readTree(f.payloadAsString());
                    int newFps = node.path("fps").asInt(0);
                    if (newFps > 0 && newFps <= 120) targetFps = newFps;
                } catch (Exception ignored) { /* leave default */ }
                return;
            }
            if (!FrameType.isVideo(f.type())) return;

            if (f.type() == FrameType.VIDEO_KEYFRAME) keyframesSeen++;
            else if (f.type() == FrameType.VIDEO_DELTA) deltasSeen++;

            // Wait for the first keyframe — writing P-frames before SPS/PPS makes the file
            // un-muxable (ffmpeg: "non-existing PPS 0 referenced", "dimensions not set").
            if (!anchored) {
                if (f.type() != FrameType.VIDEO_KEYFRAME) return;
                anchored = true;
                log.debug("recording {} anchored on first keyframe ({} bytes)",
                        sessionId, f.payload().remaining());
            }

            ByteBuffer dup = f.payload().duplicate();
            while (dup.hasRemaining()) out.write(dup);
            framesWritten++;
            // Wall-clock stamps drive the actual-FPS computation in finishAndRemux.
            // We use the time the frame was *written*, not received, because that's the
            // moment from the player's perspective on every other end of the pipeline.
            long now = System.nanoTime();
            if (firstFrameNanos == 0) firstFrameNanos = now;
            lastFrameNanos = now;
        } catch (IOException e) {
            log.error("recording {} write failed", sessionId, e);
            // Don't dispose here — let finishAndRemux do the cleanup, we just stop ingesting.
            framesWritten = -1;
        }
    }

    /**
     * Dispose the subscription, close the raw file, remux to MP4, return MP4 bytes.
     * Returns null if there's nothing to remux (no frames, ffmpeg failed, etc.).
     * Safe to call once; subsequent calls return null.
     */
    public byte[] finishAndRemux() {
        if (!stopped.compareAndSet(false, true)) return null;
        if (subscription != null) subscription.dispose();
        try { out.close(); } catch (IOException ignored) {}

        if (framesWritten <= 0) {
            log.info("recording {} had {} frames — nothing to remux", sessionId, framesWritten);
            cleanup();
            return null;
        }

        // Effective FPS = frames we wrote divided by the wall-clock span between the
        // first and last write. This corrects for encoder/network drops that would
        // otherwise compress the video into a sped-up clip:
        //   target=30fps, actual=20fps → without this fix, 30s of action becomes a
        //   20s playback running at ~1.5×. Computing actual FPS keeps duration honest.
        double playbackFps = computePlaybackFps();
        log.info("recording {} stream stats: keyframesSeen={} deltasSeen={} written={} targetFps={} playbackFps={}",
                sessionId, keyframesSeen, deltasSeen, framesWritten, targetFps, String.format("%.2f", playbackFps));

        try {
            runFfmpeg(playbackFps);
            byte[] mp4 = Files.readAllBytes(tempMp4);
            log.info("recording {} remuxed: frames={} mp4bytes={} playbackFps={}",
                    sessionId, framesWritten, mp4.length, String.format("%.2f", playbackFps));
            return mp4;
        } catch (Exception e) {
            log.warn("ffmpeg failed for session {}: {}", sessionId, e.toString());
            return null;
        } finally {
            cleanup();
        }
    }

    /**
     * Effective playback FPS based on the wall-clock duration of the actual write
     * stream. Falls back to {@link #targetFps} (from STREAM_METADATA) when we have
     * fewer than 2 frames or the span is implausibly short — those edge cases
     * can't produce a meaningful rate.
     */
    private double computePlaybackFps() {
        if (framesWritten < 2 || lastFrameNanos <= firstFrameNanos) {
            return targetFps;
        }
        double seconds = (lastFrameNanos - firstFrameNanos) / 1_000_000_000.0;
        if (seconds < 0.25) return targetFps;
        // framesWritten counts both the first and last frame, so dividing by the
        // interval rather than (frames - 1) slightly under-estimates FPS; we keep
        // it that way because over-estimating shortens playback (the very bug we
        // are fixing).
        double measured = framesWritten / seconds;
        // Clamp to sane bounds — extreme values usually point to clock skew or
        // a single-frame burst. 0.5 protects against accidental still-image runs.
        if (measured < 0.5)  return 0.5;
        if (measured > 120)  return 120;
        return measured;
    }

    /**
     * {@code -f h264} tells ffmpeg the input is a raw Annex-B byte-stream (no container),
     * {@code -r <fps>} fixes the playback rate (raw H.264 has no timestamps), and
     * {@code -c copy} remuxes without re-encoding — fast & lossless.
     * {@code +faststart} moves the moov atom to the front so the browser can start
     * playback before the whole MP4 is downloaded.
     *
     * Note: we pass the *measured* FPS (not the agent's target), so dropped frames
     * stretch playback to match real time instead of fast-forwarding.
     */
    private void runFfmpeg(double playbackFps) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-hide_banner",
                "-loglevel", "warning",
                "-f", "h264",
                "-r", String.format(java.util.Locale.ROOT, "%.3f", playbackFps),
                "-i", tempH264.toString(),
                "-c", "copy",
                "-movflags", "+faststart",
                tempMp4.toString()
        ).redirectErrorStream(true);

        Process p = pb.start();
        // Drain stdout+stderr so the OS pipe buffer doesn't block ffmpeg.
        StringBuilder out = new StringBuilder();
        try (InputStream is = p.getInputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) out.append(new String(buf, 0, n));
        }
        boolean done = p.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
            throw new IOException("ffmpeg timeout after " + FFMPEG_TIMEOUT_SECONDS + "s");
        }
        if (p.exitValue() != 0) {
            throw new IOException("ffmpeg exit=" + p.exitValue() + " output: " + out);
        }
    }

    private void cleanup() {
        try { Files.deleteIfExists(tempH264); } catch (IOException ignored) {}
        try { Files.deleteIfExists(tempMp4); }  catch (IOException ignored) {}
    }

    public long sessionId() { return sessionId; }
    public boolean isStopped() { return stopped.get(); }
    public int framesWritten() { return framesWritten; }
}
