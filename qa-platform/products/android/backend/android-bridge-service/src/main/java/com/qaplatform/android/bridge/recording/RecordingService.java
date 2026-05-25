package com.qaplatform.android.bridge.recording;

import com.qaplatform.android.bridge.hub.DeviceChannel;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-session video recording registry. The automation orchestrator calls
 * {@link #start} when a run begins and {@link #stop} when it ends; the recorder
 * holds the raw H.264 stream until stop, then ffmpeg remuxes it to MP4.
 */
@Service
public class RecordingService {

    private static final Logger log = LoggerFactory.getLogger(RecordingService.class);

    /** Container-local temp dir. Files are deleted after remux, so no volume required. */
    private final Path baseDir = Path.of("/tmp/dp-recordings");

    private final Map<Long, VideoRecorder> active = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(baseDir);
        log.info("RecordingService temp dir: {}", baseDir);
    }

    /** Idempotent: if a recording is already active for this session, no-op. */
    public void start(long sessionId, DeviceChannel channel) throws IOException {
        VideoRecorder existing = active.get(sessionId);
        if (existing != null && !existing.isStopped()) {
            log.debug("recording {} already active", sessionId);
            return;
        }
        VideoRecorder rec = new VideoRecorder(sessionId, baseDir);
        active.put(sessionId, rec);
        rec.start(channel);
        log.info("recording {} started", sessionId);
    }

    /** Returns the MP4 bytes (null on failure / nothing to record). Removes from the registry. */
    public byte[] stop(long sessionId) {
        VideoRecorder rec = active.remove(sessionId);
        if (rec == null) {
            log.debug("recording {} stop requested but nothing active", sessionId);
            return null;
        }
        return rec.finishAndRemux();
    }
}
