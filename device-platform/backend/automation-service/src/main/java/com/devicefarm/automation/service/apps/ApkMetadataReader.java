package com.devicefarm.automation.service.apps;

import com.devicefarm.common.error.ApiException;
import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.IconFace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

/**
 * Reads the metadata we care about from an uploaded APK file: package name,
 * versionCode, versionName, label, and (optionally) the launcher icon as a
 * base64 data URL for thumbnail display.
 *
 * <p>The parse happens off-disk via {@code net.dongliu:apk-parser}; we never
 * load the whole APK into memory at once.</p>
 */
@Component
public class ApkMetadataReader {

    private static final Logger log = LoggerFactory.getLogger(ApkMetadataReader.class);

    /** Reject icons bigger than this — we render them as base64 inline thumbs in the UI. */
    private static final int MAX_ICON_BYTES = 64 * 1024;

    public ApkMetadata read(File apk) {
        try (ApkFile parser = new ApkFile(apk)) {
            ApkMeta meta = parser.getApkMeta();
            if (meta == null || meta.getPackageName() == null || meta.getPackageName().isBlank()) {
                throw ApiException.badRequest("APK manifest missing packageName");
            }
            String label = meta.getLabel() != null ? meta.getLabel() : meta.getPackageName();
            String iconData = tryReadIcon(parser);
            return new ApkMetadata(
                    meta.getPackageName(),
                    meta.getVersionCode() != null ? meta.getVersionCode() : 0L,
                    meta.getVersionName(),
                    label,
                    iconData
            );
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.warn("apk parse failed: {}", e.toString());
            throw ApiException.badRequest("could not parse APK file: " + e.getMessage());
        }
    }

    private String tryReadIcon(ApkFile parser) {
        try {
            List<IconFace> icons = parser.getAllIcons();
            if (icons == null || icons.isEmpty()) return null;
            IconFace best = icons.get(0);
            for (IconFace i : icons) {
                if (i.getData() == null) continue;
                if (best.getData() == null || i.getData().length > best.getData().length) {
                    if (i.getData().length <= MAX_ICON_BYTES) best = i;
                }
            }
            if (best.getData() == null || best.getData().length == 0) return null;
            if (best.getData().length > MAX_ICON_BYTES) return null;
            String mime = best.getPath() != null && best.getPath().endsWith(".webp") ? "image/webp" : "image/png";
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(best.getData());
        } catch (Exception e) {
            log.debug("icon extract failed (non-fatal): {}", e.toString());
            return null;
        }
    }

    public record ApkMetadata(
            String packageName,
            long versionCode,
            String versionName,
            String label,
            String iconData
    ) {}
}
