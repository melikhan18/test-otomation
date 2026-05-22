package com.devicefarm.bridge.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request/response shapes for the bridge's app-control REST surface. These mirror the
 * JSON payloads the agent sees on the WebSocket wire (frame types 0x0B–0x12). Keeping
 * them as Java records — rather than {@code Map<String, Object>} — lets validation
 * fire on bad input and gives {@code automation-service} typed reply parsing.
 */
public class AppControlDtos {

    private static final String PKG_PATTERN = "^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$";

    /* ── app/info ─────────────────────────────────────────────────────────── */

    public record AppInfoRequest(
            @NotBlank @Pattern(regexp = PKG_PATTERN, message = "invalid package name")
            String packageName
    ) {}

    /**
     * {@code installed=false} → versionCode/versionName are null. Otherwise both are
     * populated. The agent fills these from {@code PackageManager.getPackageInfo()}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AppInfoResponse(
            String requestId,
            boolean installed,
            Long versionCode,
            String versionName
    ) {}

    /* ── app/install ──────────────────────────────────────────────────────── */

    public record InstallApkRequest(
            @NotBlank String downloadUrl,
            @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "sha256 must be 64 hex chars")
            String sha256,
            @Min(0) long expectedVersionCode,
            @NotBlank @Pattern(regexp = PKG_PATTERN, message = "invalid package name")
            String packageName
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InstallApkResponse(
            String requestId,
            /** "ok" | "failed" */
            String status,
            Long installedVersionCode,
            /** Android PackageManager error code (e.g. "INSTALL_FAILED_UPDATE_INCOMPATIBLE"). */
            String errorCode,
            String errorMessage
    ) {}

    /* ── app/launch ───────────────────────────────────────────────────────── */

    public record LaunchAppRequest(
            @NotBlank @Pattern(regexp = PKG_PATTERN, message = "invalid package name")
            String packageName
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LaunchAppResponse(
            String requestId,
            /** "ok" | "failed" */
            String status,
            String errorMessage
    ) {}

    /* ── reset-home ───────────────────────────────────────────────────────── */

    /**
     * Reset the device to the home screen between runs.
     * {@code packageName} + {@code killProcess=true} additionally force-stops that app —
     * only effective on Device Owner cihazlarda; standard cihazda agent yine de HOME basar.
     */
    public record ResetHomeRequest(
            @Pattern(regexp = PKG_PATTERN, message = "invalid package name")
            String packageName,
            Boolean killProcess
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResetHomeResponse(
            String requestId,
            /** "ok" | "failed" */
            String status,
            String errorMessage
    ) {}
}
