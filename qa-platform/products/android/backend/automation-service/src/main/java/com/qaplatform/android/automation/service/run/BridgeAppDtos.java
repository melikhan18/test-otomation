package com.qaplatform.android.automation.service.run;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Decoded reply shapes returned by the bridge's app-control endpoints. Kept here
 * (rather than imported from the bridge module) so android-automation-service stays a
 * peer of the bridge over HTTP, not a code-level coupling. {@code @JsonIgnoreProperties(ignoreUnknown=true)}
 * leaves room for the bridge to add fields without breaking this client.
 */
public class BridgeAppDtos {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AppInfo(
            String requestId,
            boolean installed,
            Long versionCode,
            String versionName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstallResult(
            String requestId,
            String status,            // "ok" | "failed"
            Long installedVersionCode,
            String errorCode,
            String errorMessage
    ) {
        public boolean ok() { return "ok".equalsIgnoreCase(status); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LaunchResult(
            String requestId,
            String status,
            String errorMessage
    ) {
        public boolean ok() { return "ok".equalsIgnoreCase(status); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResetResult(
            String requestId,
            String status,
            String errorMessage
    ) {
        public boolean ok() { return "ok".equalsIgnoreCase(status); }
    }
}
