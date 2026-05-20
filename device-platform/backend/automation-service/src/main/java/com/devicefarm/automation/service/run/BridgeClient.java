package com.devicefarm.automation.service.run;

import com.devicefarm.common.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Synchronous client for the bridge REST control surface. The execution engine uses this
 * to:
 *   - dispatch a control command (tap/swipe/text/key)
 *   - request an inspect snapshot and wait for the response
 *   - force a keyframe before grabbing a screenshot (Faz G)
 */
@Component
public class BridgeClient {

    private static final Logger log = LoggerFactory.getLogger(BridgeClient.class);

    private final RestClient http;

    public BridgeClient(@Value("${app.services.bridge.url:http://localhost:8084}") String baseUrl) {
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public void control(long sessionId, String sessionToken, Map<String, Object> command) {
        try {
            http.post()
                    .uri("/api/bridge/sessions/{id}/control", sessionId)
                    .header("Authorization", "Bearer " + sessionToken)
                    .body(command)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpStatusCodeException e) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "bridge control failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        }
    }

    /** Returns the JSON node of the inspect response (root + children, screen dims, etc.). */
    public JsonNode inspect(long sessionId, String sessionToken, long timeoutSeconds) {
        try {
            return http.post()
                    .uri(uri -> uri.path("/api/bridge/sessions/{id}/inspect")
                                  .queryParam("timeoutSeconds", timeoutSeconds)
                                  .build(sessionId))
                    .header("Authorization", "Bearer " + sessionToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpStatusCodeException e) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "inspect failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        }
    }

    public void forceKeyframe(long sessionId, String sessionToken) {
        try {
            http.post()
                    .uri("/api/bridge/sessions/{id}/keyframe", sessionId)
                    .header("Authorization", "Bearer " + sessionToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignored) { /* best-effort */ }
    }

    /**
     * Captures a still-frame PNG of the device. Returns the raw PNG bytes on success or
     * {@code null} on any failure (we never want a screenshot blip to fail a step — the
     * step itself already succeeded by the time we call this).
     */
    /** Best-effort recording start. Logs and swallows errors so a recording hiccup never kills a run. */
    public void startRecording(long sessionId, String sessionToken) {
        try {
            http.post()
                    .uri("/api/bridge/sessions/{id}/record/start", sessionId)
                    .header("Authorization", "Bearer " + sessionToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("startRecording session={} failed: {}", sessionId, e.toString());
        }
    }

    /** Returns the MP4 bytes (null on any failure). Caller uploads. */
    public byte[] stopRecording(long sessionId, String sessionToken) {
        try {
            return http.post()
                    .uri("/api/bridge/sessions/{id}/record/stop", sessionId)
                    .header("Authorization", "Bearer " + sessionToken)
                    .retrieve()
                    .body(byte[].class);
        } catch (HttpStatusCodeException e) {
            log.warn("stopRecording session={} HTTP {} body={}", sessionId, e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("stopRecording session={} threw: {}", sessionId, e.toString());
            return null;
        }
    }

    public byte[] screenshot(long sessionId, String sessionToken, long timeoutSeconds) {
        try {
            return http.post()
                    .uri(uri -> uri.path("/api/bridge/sessions/{id}/screenshot")
                                  .queryParam("timeoutSeconds", timeoutSeconds)
                                  .build(sessionId))
                    .header("Authorization", "Bearer " + sessionToken)
                    .retrieve()
                    .body(byte[].class);
        } catch (HttpStatusCodeException e) {
            // Surface the bridge's error body — agent might be on Android <11, accessibility
            // service disabled, or the request timed out waiting for the response frame.
            log.warn("screenshot HTTP {} from bridge: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("screenshot call to bridge threw: {}", e.toString());
            return null;
        }
    }
}
