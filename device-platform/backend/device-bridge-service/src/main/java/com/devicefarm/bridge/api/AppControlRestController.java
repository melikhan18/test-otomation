package com.devicefarm.bridge.api;

import com.devicefarm.bridge.api.dto.AppControlDtos.*;
import com.devicefarm.bridge.hub.DeviceChannel;
import com.devicefarm.bridge.hub.DeviceChannelRegistry;
import com.devicefarm.bridge.protocol.Frame;
import com.devicefarm.bridge.protocol.FrameType;
import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
import com.devicefarm.common.jwt.JwtTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * App-control surface used by {@code automation-service}'s RunOrchestrator to:
 *   - probe what's installed on the device,
 *   - install / update an APK (downloaded by the agent from MinIO),
 *   - launch the target app, and
 *   - reset the device to the home screen between runs.
 *
 * Each endpoint correlates a request frame to its response frame by a freshly-minted
 * {@code requestId}, using the same multicast sink + filter-by-id pattern as {@code inspect}
 * / {@code screenshot} in {@link ControlRestController}.
 *
 * Timeouts are tuned to the operation: info is cheap, install can take minutes for large
 * APKs over Wi-Fi, launch is short but allows for a cold start, reset is near-instant.
 */
@RestController
@RequestMapping("/api/bridge/sessions/{sessionId}")
public class AppControlRestController {

    private static final Logger log = LoggerFactory.getLogger(AppControlRestController.class);
    private static final ObjectMapper M = new ObjectMapper();

    private static final long TIMEOUT_INFO_SECONDS    = 10;
    private static final long TIMEOUT_INSTALL_SECONDS = 300;
    private static final long TIMEOUT_LAUNCH_SECONDS  = 15;
    private static final long TIMEOUT_RESET_SECONDS   = 5;

    private final DeviceChannelRegistry registry;
    private final JwtTokenService tokens;

    public AppControlRestController(DeviceChannelRegistry registry, JwtTokenService tokens) {
        this.registry = registry;
        this.tokens = tokens;
    }

    /* ───────────────────────── endpoints ─────────────────────────────────── */

    @PostMapping("/app/info")
    public Mono<ResponseEntity<Object>> appInfo(@PathVariable long sessionId,
                                                ServerHttpRequest request,
                                                @RequestBody @Valid AppInfoRequest body) {
        DeviceChannel channel = authChannel(sessionId, request);
        Map<String, Object> payload = Map.of("packageName", body.packageName());
        return requestResponse(channel,
                FrameType.APP_INFO_REQUEST, FrameType.APP_INFO_RESPONSE,
                payload, AppInfoResponse.class, TIMEOUT_INFO_SECONDS, "app/info");
    }

    @PostMapping("/app/install")
    public Mono<ResponseEntity<Object>> installApk(@PathVariable long sessionId,
                                                   ServerHttpRequest request,
                                                   @RequestBody @Valid InstallApkRequest body) {
        DeviceChannel channel = authChannel(sessionId, request);
        Map<String, Object> payload = Map.of(
                "downloadUrl", body.downloadUrl(),
                "sha256", body.sha256(),
                "expectedVersionCode", body.expectedVersionCode(),
                "packageName", body.packageName()
        );
        return requestResponse(channel,
                FrameType.INSTALL_APK_REQUEST, FrameType.INSTALL_APK_RESPONSE,
                payload, InstallApkResponse.class, TIMEOUT_INSTALL_SECONDS, "app/install");
    }

    @PostMapping("/app/launch")
    public Mono<ResponseEntity<Object>> launchApp(@PathVariable long sessionId,
                                                  ServerHttpRequest request,
                                                  @RequestBody @Valid LaunchAppRequest body) {
        DeviceChannel channel = authChannel(sessionId, request);
        Map<String, Object> payload = Map.of("packageName", body.packageName());
        return requestResponse(channel,
                FrameType.LAUNCH_APP_REQUEST, FrameType.LAUNCH_APP_RESPONSE,
                payload, LaunchAppResponse.class, TIMEOUT_LAUNCH_SECONDS, "app/launch");
    }

    @PostMapping("/reset-home")
    public Mono<ResponseEntity<Object>> resetHome(@PathVariable long sessionId,
                                                  ServerHttpRequest request,
                                                  @RequestBody(required = false) @Valid ResetHomeRequest body) {
        DeviceChannel channel = authChannel(sessionId, request);
        Map<String, Object> payload = new HashMap<>();
        if (body != null) {
            if (body.packageName() != null) payload.put("packageName", body.packageName());
            if (body.killProcess() != null) payload.put("killProcess", body.killProcess());
        }
        return requestResponse(channel,
                FrameType.RESET_HOME_REQUEST, FrameType.RESET_HOME_RESPONSE,
                payload, ResetHomeResponse.class, TIMEOUT_RESET_SECONDS, "reset-home");
    }

    /* ───────────────────────── correlated request/response ───────────────── */

    /**
     * Send a request frame to the agent and wait for the response frame whose
     * {@code requestId} matches. On timeout returns HTTP 504 with a JSON error body.
     *
     * <p>The subscription is set up BEFORE the request frame is sent so we never race
     * a fast agent reply. The {@code Mono.deferContextual} isn't needed here — the
     * multicast sink in {@link DeviceChannel} is hot and replays nothing, so subscribing
     * just-in-time is correct.</p>
     */
    private <T> Mono<ResponseEntity<Object>> requestResponse(DeviceChannel channel,
                                                             byte reqType, byte respType,
                                                             Map<String, Object> bodyMap,
                                                             Class<T> respClass,
                                                             long timeoutSeconds,
                                                             String op) {
        String requestId = UUID.randomUUID().toString();

        Mono<ResponseEntity<Object>> waiter = channel.subscribeWeb(false)
                .filter(f -> f.type() == respType)
                .map(f -> parseTree(f.payloadAsString()))
                .filter(j -> j != null && requestId.equals(j.path("requestId").asText(null)))
                .next()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .<ResponseEntity<Object>>map(j -> {
                    try {
                        return ResponseEntity.ok().body((Object) M.treeToValue(j, respClass));
                    } catch (Exception e) {
                        log.warn("{} session={} bad response shape: {}", op, channel.deviceId(), e.toString());
                        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                                (Object) Map.of("error", "malformed agent response: " + e.getMessage(),
                                                "requestId", requestId));
                    }
                })
                .onErrorResume(TimeoutException.class, e -> {
                    log.warn("{} session={} requestId={} timeout after {}s — agent never replied",
                            op, channel.deviceId(), requestId, timeoutSeconds);
                    return Mono.just(ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(
                            (Object) Map.of("error", op + " timeout", "requestId", requestId)));
                });

        Map<String, Object> envelope = new HashMap<>(bodyMap);
        envelope.put("requestId", requestId);
        try {
            channel.sendToAgent(Frame.ofJson(reqType, M.writeValueAsString(envelope)));
        } catch (Exception e) {
            throw ApiException.internal("could not serialize " + op + " request: " + e.getMessage());
        }
        return waiter;
    }

    /* ───────────────────────── helpers ───────────────────────────────────── */

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

    private static JsonNode parseTree(String s) {
        try { return M.readTree(s); }
        catch (Exception e) { return null; }
    }
}
