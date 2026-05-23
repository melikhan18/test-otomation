package com.devicefarm.bridge.api;

import com.devicefarm.bridge.hub.DeviceChannel;
import com.devicefarm.bridge.hub.DeviceChannelRegistry;
import com.devicefarm.bridge.protocol.Frame;
import com.devicefarm.bridge.protocol.FrameType;
import com.devicefarm.bridge.recording.RecordingService;
import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
import com.devicefarm.common.jwt.JwtTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.scheduler.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Synchronous control surface used by {@code automation-service} to drive a device.
 *
 * The original WebSocket protocol stays in place for live web sessions; this REST layer
 * just translates "send a frame and wait" into HTTP request/response so the execution
 * worker can call it from a synchronous step loop without managing a long-lived socket.
 *
 * Auth: session JWT (issued by session-service when a reservation is created) is required
 * in the {@code Authorization: Bearer} header. The session token carries {@code deviceId}
 * and {@code productId} claims, which are then matched against the {@link DeviceChannel}.
 */
@RestController
@RequestMapping("/api/bridge/sessions/{sessionId}")
public class ControlRestController {

    private static final Logger log = LoggerFactory.getLogger(ControlRestController.class);
    private static final ObjectMapper M = new ObjectMapper();

    private final DeviceChannelRegistry registry;
    private final JwtTokenService tokens;
    private final RecordingService recordings;

    public ControlRestController(DeviceChannelRegistry registry, JwtTokenService tokens,
                                 RecordingService recordings) {
        this.registry = registry;
        this.tokens = tokens;
        this.recordings = recordings;
    }

    /* ─────────────────── Fire-and-forget control commands ──────────────── */

    /** Body is a control JSON payload (tap/swipe/key/text/etc.) — forwarded verbatim. */
    @PostMapping(path = "/control", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> control(@PathVariable long sessionId,
                              ServerHttpRequest request,
                              @RequestBody Map<String, Object> command) {
        DeviceChannel channel = authChannel(sessionId, request);
        try {
            String json = M.writeValueAsString(command);
            channel.sendToAgent(Frame.ofJson(FrameType.CONTROL_COMMAND, json));
            log.debug("control session={} type={}", sessionId, command.get("type"));
            return Mono.empty();
        } catch (Exception e) {
            throw ApiException.badRequest("invalid control payload: " + e.getMessage());
        }
    }

    /** Force a fresh keyframe — useful when the runner wants a clean snapshot. */
    @PostMapping("/keyframe")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> forceKeyframe(@PathVariable long sessionId, ServerHttpRequest request) {
        DeviceChannel channel = authChannel(sessionId, request);
        channel.requestKeyframe();
        return Mono.empty();
    }

    /* ─────────────────── Request / wait-for-response (inspect) ──────────── */

    /**
     * Triggers an inspect on the agent and waits up to {@code timeoutSeconds} for the
     * matching {@code INSPECT_RESPONSE}. The match is by {@code requestId} so concurrent
     * runners don't see each other's responses.
     */
    @PostMapping("/inspect")
    public Mono<ResponseEntity<Object>> inspect(@PathVariable long sessionId,
                                                ServerHttpRequest request,
                                                @RequestParam(value = "timeoutSeconds", defaultValue = "8")
                                                long timeoutSeconds) {
        DeviceChannel channel = authChannel(sessionId, request);
        String requestId = UUID.randomUUID().toString();

        // Subscribe BEFORE sending so we don't race the response.
        // Casts to `Object` keep both branches at `ResponseEntity<Object>` so the chain
        // type-unifies (otherwise success → ResponseEntity<Object>, fallback → ResponseEntity<Map<…>>).
        Mono<ResponseEntity<Object>> waiter = channel.subscribeWeb(false)
                .filter(f -> f.type() == FrameType.INSPECT_RESPONSE)
                .map(f -> parse(f.payloadAsString()))
                .filter(j -> j instanceof Map && requestId.equals(((Map<?, ?>) j).get("requestId")))
                .next()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .<ResponseEntity<Object>>map(json -> ResponseEntity.ok().body((Object) json))
                .onErrorResume(java.util.concurrent.TimeoutException.class,
                        e -> Mono.just(ResponseEntity.status(504).body((Object) Map.of(
                                "error", "inspect timeout",
                                "requestId", requestId
                        ))));

        channel.sendToAgent(Frame.ofJson(FrameType.INSPECT_REQUEST,
                "{\"requestId\":\"" + requestId + "\"}"));
        return waiter;
    }

    /* ─────────────────── Screenshot (request / wait-for-response) ────────── */

    /**
     * Triggers a still-frame screenshot on the agent and returns the PNG bytes inline.
     * Same correlation pattern as inspect (requestId in the request body, agent echoes it
     * in the SCREENSHOT_RESPONSE payload header so concurrent runners don't cross-talk).
     *
     * Response is `image/png` on success, `application/json` with an `error` field on
     * failure (HTTP 502 or 504 on timeout).
     */
    @PostMapping("/screenshot")
    public Mono<ResponseEntity<byte[]>> screenshot(@PathVariable long sessionId,
                                                   ServerHttpRequest request,
                                                   @RequestParam(value = "timeoutSeconds", defaultValue = "10")
                                                   long timeoutSeconds) {
        DeviceChannel channel = authChannel(sessionId, request);
        String requestId = UUID.randomUUID().toString();

        Mono<ResponseEntity<byte[]>> waiter = channel.subscribeWeb(false)
                .filter(f -> f.type() == FrameType.SCREENSHOT_RESPONSE)
                .map(f -> parseScreenshot(f.payload()))
                .filter(p -> requestId.equals(p.requestId))
                .next()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .<ResponseEntity<byte[]>>map(p -> {
                    if (p.error != null) {
                        log.warn("screenshot session={} agent error: {}", sessionId, p.error);
                        return ResponseEntity.status(502)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(("{\"error\":\"" + p.error.replace("\"", "\\\"") + "\"}").getBytes(StandardCharsets.UTF_8));
                    }
                    log.info("screenshot session={} bytes={}", sessionId, p.png == null ? 0 : p.png.length);
                    return ResponseEntity.ok()
                            .contentType(MediaType.IMAGE_PNG)
                            .header(HttpHeaders.CACHE_CONTROL, "no-store")
                            .body(p.png);
                })
                .onErrorResume(java.util.concurrent.TimeoutException.class,
                        e -> {
                            log.warn("screenshot session={} timeout after {}s (no SCREENSHOT_RESPONSE from agent)", sessionId, timeoutSeconds);
                            return Mono.just(ResponseEntity.status(504)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(("{\"error\":\"screenshot timeout\",\"requestId\":\"" + requestId + "\"}").getBytes(StandardCharsets.UTF_8)));
                        });

        log.info("screenshot session={} requestId={} sending SCREENSHOT_REQUEST to agent", sessionId, requestId);
        channel.sendToAgent(Frame.ofJson(FrameType.SCREENSHOT_REQUEST,
                "{\"requestId\":\"" + requestId + "\"}"));
        return waiter;
    }

    /** [4-byte BE metaLen][JSON meta][PNG bytes] → parsed payload */
    private static ScreenshotPayload parseScreenshot(ByteBuffer payload) {
        ByteBuffer dup = payload.duplicate();
        if (dup.remaining() < 4) return new ScreenshotPayload(null, "malformed payload (too short)", null);
        int metaLen = dup.getInt();
        if (metaLen < 0 || metaLen > dup.remaining()) {
            return new ScreenshotPayload(null, "malformed payload (bad metaLen=" + metaLen + ")", null);
        }
        byte[] metaBytes = new byte[metaLen];
        dup.get(metaBytes);
        String json = new String(metaBytes, StandardCharsets.UTF_8);
        String requestId;
        String error;
        try {
            var node = M.readTree(json);
            requestId = node.path("requestId").asText(null);
            error     = node.has("error") ? node.path("error").asText() : null;
        } catch (Exception e) {
            return new ScreenshotPayload(null, "metadata parse failed: " + e.getMessage(), null);
        }
        if (error != null) return new ScreenshotPayload(requestId, error, null);
        byte[] png = new byte[dup.remaining()];
        dup.get(png);
        return new ScreenshotPayload(requestId, null, png);
    }

    private record ScreenshotPayload(String requestId, String error, byte[] png) {}

    /* ─────────────────── Recording (start / stop & remux) ──────────────────── */

    /**
     * Begin recording the device's H.264 stream. Idempotent: a second call while a
     * recording is active is a no-op. The recorder lives on the bridge — automation-service
     * doesn't carry the byte-stream, only requests start/stop and downloads the final MP4.
     */
    @PostMapping("/record/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Map<String, Object>> startRecording(@PathVariable long sessionId, ServerHttpRequest request) {
        DeviceChannel channel = authChannel(sessionId, request);
        return Mono.<Map<String, Object>>fromCallable(() -> {
                    recordings.start(sessionId, channel);
                    return Map.<String, Object>of("sessionId", sessionId, "recording", true);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Stop the in-flight recording, run ffmpeg, and return the MP4 inline.
     * {@code video/mp4} on success, JSON error on failure. The remux can take several
     * seconds — we offload to a bounded-elastic scheduler so we don't block reactor threads.
     */
    @PostMapping("/record/stop")
    public Mono<ResponseEntity<byte[]>> stopRecording(@PathVariable long sessionId, ServerHttpRequest request) {
        authChannel(sessionId, request);
        return Mono.fromCallable(() -> recordings.stop(sessionId))
                .subscribeOn(Schedulers.boundedElastic())
                .<ResponseEntity<byte[]>>map(mp4 -> {
                    if (mp4 == null || mp4.length == 0) {
                        log.warn("recording session={} stop returned no data", sessionId);
                        return ResponseEntity.status(502)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"error\":\"no recording active or remux failed\"}".getBytes(StandardCharsets.UTF_8));
                    }
                    log.info("recording session={} delivered mp4 bytes={}", sessionId, mp4.length);
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType("video/mp4"))
                            .header(HttpHeaders.CACHE_CONTROL, "no-store")
                            .body(mp4);
                });
    }

    /* ─────────────────────── helpers ─────────────────────── */

    /** Validates the session JWT and resolves the live {@link DeviceChannel}. */
    private DeviceChannel authChannel(long sessionId, ServerHttpRequest request) {
        String auth = request.getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw ApiException.unauthorized("missing Bearer token");
        }
        JwtPrincipal principal;
        try {
            principal = tokens.parse(auth.substring(7));
        } catch (Exception e) {
            throw ApiException.unauthorized("invalid token");
        }
        if (principal.deviceId() == null) {
            throw ApiException.forbidden("token must carry deviceId (session token)");
        }
        // Session id in path must match the JWT — keeps a stolen-session-token from being
        // re-used for a different session that happens to be running on the same product.
        if (principal.sessionId() != null && !principal.sessionId().equals(sessionId)) {
            throw ApiException.forbidden("session id mismatch");
        }
        DeviceChannel channel = registry.get(principal.deviceId())
                .orElseThrow(() -> ApiException.notFound("agent offline"));
        if (channel.productId() != principal.productId()) {
            throw ApiException.forbidden("product mismatch");
        }
        return channel;
    }

    private static Object parse(String s) {
        try { return M.readValue(s, Object.class); }
        catch (Exception e) { return s; }
    }
}
