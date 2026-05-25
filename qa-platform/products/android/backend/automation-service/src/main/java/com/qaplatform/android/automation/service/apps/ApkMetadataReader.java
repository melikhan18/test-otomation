package com.qaplatform.android.automation.service.apps;

import com.qaplatform.common.error.ApiException;
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

            // Filter to icons that are actually raster images. apk-parser
            // returns adaptive-icon XML drawables (Android 8+) as bytes too —
            // we have to skip those because the browser can't render Android
            // binary XML, and trusting the file path's extension is unreliable
            // (some APKs ship .png paths that are actually XML).
            //
            // Pick the LARGEST valid raster under MAX_ICON_BYTES — bigger
            // usually = higher DPI = nicer thumb.
            IconFace best = null;
            String bestMime = null;
            for (IconFace i : icons) {
                byte[] data = i.getData();
                if (data == null || data.length == 0)        continue;
                if (data.length > MAX_ICON_BYTES)            continue;
                String mime = detectImageMime(data);
                if (mime == null)                            continue;  // not PNG/JPG/WebP/GIF — likely adaptive XML
                if (best == null || data.length > best.getData().length) {
                    best = i;
                    bestMime = mime;
                }
            }
            if (best == null) return null;
            return "data:" + bestMime + ";base64," + Base64.getEncoder().encodeToString(best.getData());
        } catch (Exception e) {
            log.debug("icon extract failed (non-fatal): {}", e.toString());
            return null;
        }
    }

    /**
     * Detects the image format from magic bytes. Returns null if the buffer
     * isn't a recognised raster format — that's the signal to skip an
     * adaptive-icon XML, an SVG, or any other oddity the parser returned.
     */
    private static String detectImageMime(byte[] data) {
        if (data == null || data.length < 4) return null;
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        // JPEG: FF D8 FF
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // WebP: RIFF....WEBP
        if (data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        // GIF: GIF87a or GIF89a
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8') {
            return "image/gif";
        }
        return null;
    }

    public record ApkMetadata(
            String packageName,
            long versionCode,
            String versionName,
            String label,
            String iconData
    ) {}
}
