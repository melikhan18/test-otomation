package com.qaplatform.android.automation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * APK Repository DTOs. An "App" is the metadata record (package_name + display_name),
 * and each App owns one or more "AppVersion" rows — one per uploaded APK.
 *
 * <p>Run / SuiteRun pick a specific {@code app_version_id} at launch time; the runner
 * compares it against the version installed on the device and decides whether to
 * skip / install / update before kicking off the scenario steps.</p>
 */
public class AppDtos {

    /* ── App CRUD ──────────────────────────────────────────────── */

    /**
     * The package_name is supplied by the user when creating the app shell so we can
     * register it before the first APK is uploaded. The first upload validates that
     * the APK's manifest packageName matches.
     */
    public record CreateRequest(
            @NotBlank @Size(max = 255) String packageName,
            @NotBlank @Size(max = 255) String displayName,
            String description
    ) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 255) String displayName,
            String description
    ) {}

    /** List row. {@code latestVersion} is null when no APK has been uploaded yet. */
    public record Summary(
            long id,
            String packageName,
            String displayName,
            String description,
            String iconData,
            Long latestVersionCode,
            String latestVersionName,
            int versionCount,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /** Full app view including the version history (most-recent first). */
    public record View(
            long id,
            String packageName,
            String displayName,
            String description,
            String iconData,
            Instant createdAt,
            Instant updatedAt,
            List<VersionView> versions
    ) {}

    /* ── Versions ──────────────────────────────────────────────── */

    /** Multipart form field next to the APK file (`file`). All fields optional. */
    public record VersionUploadRequest(String notes) {}

    public record VersionView(
            long id,
            long appId,
            long versionCode,
            String versionName,
            long fileSizeBytes,
            String sha256,
            String downloadUrl,
            String notes,
            long uploadedByUserId,
            Instant uploadedAt
    ) {}
}
