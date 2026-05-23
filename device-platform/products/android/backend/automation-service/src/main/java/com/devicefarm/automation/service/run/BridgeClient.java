package com.devicefarm.automation.service.run;

import com.devicefarm.automation.service.run.BridgeAppDtos.AppInfo;
import com.devicefarm.automation.service.run.BridgeAppDtos.InstallResult;
import com.devicefarm.automation.service.run.BridgeAppDtos.LaunchResult;
import com.devicefarm.automation.service.run.BridgeAppDtos.ResetResult;
import com.devicefarm.common.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
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
        // The default RestClient reads the whole body before exposing it, which is fine
        // for our small JSON / inline-PNG responses. APK install endpoint can wait up to
        // five minutes server-side; the underlying JDK HttpClient has no default read
        // timeout, so the bridge's own request-response timeout is the bound that fires
        // first. We rely on that rather than a client-side timeout so the bridge always
        // gets a chance to write a structured error body.
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

    /* ────────────────── Faz 2: app-control endpoints ────────────────────── */

    /**
     * Ask the agent whether a package is installed and, if so, at which versionCode.
     * Throws {@link ApiException} on non-2xx — RunOrchestrator treats this as a fatal
     * prep failure (the run can't continue without knowing the install state).
     */
    public AppInfo appInfo(long sessionId, String sessionToken, String packageName) {
        try {
            return http.post()
                    .uri("/api/bridge/sessions/{id}/app/info", sessionId)
                    .header("Authorization", "Bearer " + sessionToken)
                    .body(Map.of("packageName", packageName))
                    .retrieve()
                    .body(AppInfo.class);
        } catch (HttpStatusCodeException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "app-info failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        }
    }

    /**
     * Trigger an APK install on the device. The agent downloads from {@code downloadUrl}
     * (typically a MinIO public URL), verifies SHA-256, and installs via
     * {@code PackageInstaller}.
     */
    public InstallResult installApk(long sessionId, String sessionToken,
                                    String downloadUrl, String sha256,
                                    long expectedVersionCode, String packageName) {
        Map<String, Object> body = Map.of(
                "downloadUrl", downloadUrl,
                "sha256", sha256,
                "expectedVersionCode", expectedVersionCode,
                "packageName", packageName
        );
        try {
            return http.post()
                    .uri("/api/bridge/sessions/{id}/app/install", sessionId)
                    .header("Authorization", "Bearer " + sessionToken)
                    .body(body)
                    .retrieve()
                    .body(InstallResult.class);
        } catch (HttpStatusCodeException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "app-install failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        }
    }

    /** Bring the target app to the foreground via {@code Intent.ACTION_MAIN}. */
    public LaunchResult launchApp(long sessionId, String sessionToken, String packageName) {
        try {
            return http.post()
                    .uri("/api/bridge/sessions/{id}/app/launch", sessionId)
                    .header("Authorization", "Bearer " + sessionToken)
                    .body(Map.of("packageName", packageName))
                    .retrieve()
                    .body(LaunchResult.class);
        } catch (HttpStatusCodeException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "app-launch failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        }
    }

    /**
     * Return the device to the home screen. Best-effort — a reset hiccup must NOT fail
     * the run that just finished, so we log and swallow errors and return null on failure.
     * RunOrchestrator records the failure on the run row but doesn't roll back.
     *
     * @param packageName optional — when set together with {@code killProcess=true} on a
     *                    Device Owner agent, also force-stops the target app.
     */
    public ResetResult resetHome(long sessionId, String sessionToken,
                                 String packageName, Boolean killProcess) {
        Map<String, Object> body = new HashMap<>();
        if (packageName != null) body.put("packageName", packageName);
        if (killProcess != null) body.put("killProcess", killProcess);
        try {
            return http.post()
                    .uri("/api/bridge/sessions/{id}/reset-home", sessionId)
                    .header("Authorization", "Bearer " + sessionToken)
                    .body(body)
                    .retrieve()
                    .body(ResetResult.class);
        } catch (HttpStatusCodeException e) {
            log.warn("reset-home session={} HTTP {} body={}", sessionId, e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("reset-home session={} threw: {}", sessionId, e.toString());
            return null;
        }
    }
}
