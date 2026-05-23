package com.devicefarm.automation.service.storage;

import com.devicefarm.automation.config.ObjectStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;

/**
 * Thin wrapper around the S3 client. The two upload methods exist mainly so callers
 * don't repeat bucket-name plumbing; the returned URL is browser-reachable.
 */
@Service
public class ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorage.class);

    private final S3Client s3;
    private final ObjectStorageProperties props;

    public ObjectStorage(S3Client s3, ObjectStorageProperties props) {
        this.s3 = s3;
        this.props = props;
    }

    /** Upload a PNG screenshot. Returns the public URL the browser should use. */
    public String uploadScreenshot(String key, byte[] data) {
        return put(props.getScreenshotsBucket(), key, data, "image/png");
    }

    /** Upload an MP4 video (used by Faz H). */
    public String uploadVideo(String key, byte[] data) {
        return put(props.getVideosBucket(), key, data, "video/mp4");
    }

    /**
     * Upload an APK file. Takes a {@link File} (rather than {@code byte[]}) so callers
     * can stream large uploads (50–250 MB is common) directly from disk without
     * loading the whole binary into the heap.
     */
    public String uploadApk(String key, File file) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(props.getApksBucket())
                        .key(key)
                        .contentType("application/vnd.android.package-archive")
                        .build(),
                RequestBody.fromFile(file));
        String url = publicUrl(props.getApksBucket(), key);
        log.debug("uploaded apk {}/{} ({} bytes) → {}", props.getApksBucket(), key, file.length(), url);
        return url;
    }

    /** Delete an APK by its storage key. Returns true on success, false otherwise. */
    public boolean deleteApk(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(props.getApksBucket()).key(key).build());
            return true;
        } catch (Exception e) {
            log.warn("deleteApk '{}' failed: {}", key, e.toString());
            return false;
        }
    }

    /** Compose the public-facing URL for an APK key (used when the runner asks the agent to install). */
    public String publicUrlForApk(String key) {
        return publicUrl(props.getApksBucket(), key);
    }

    private String put(String bucket, String key, byte[] data, String contentType) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data));
        String url = publicUrl(bucket, key);
        log.debug("uploaded {}/{} ({} bytes) → {}", bucket, key, data.length, url);
        return url;
    }

    private String publicUrl(String bucket, String key) {
        String base = props.getPublicUrl();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/" + bucket + "/" + key;
    }

    /**
     * Delete an object identified by the public URL we previously returned from
     * {@link #uploadScreenshot}/{@link #uploadVideo}. Best-effort: a failure is logged
     * and swallowed so the retention job can keep going.
     */
    public boolean deleteByUrl(String url) {
        ObjectRef ref = parseUrl(url);
        if (ref == null) {
            log.debug("deleteByUrl: cannot parse '{}'", url);
            return false;
        }
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(ref.bucket()).key(ref.key()).build());
            return true;
        } catch (Exception e) {
            log.warn("deleteByUrl '{}' failed: {}", url, e.toString());
            return false;
        }
    }

    /** Split a previously-issued URL back into bucket + key. Returns null on unknown URL. */
    public ObjectRef parseUrl(String url) {
        if (url == null || url.isBlank()) return null;
        String base = props.getPublicUrl();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String prefix = base + "/";
        if (!url.startsWith(prefix)) return null;
        String rest = url.substring(prefix.length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) return null;
        return new ObjectRef(rest.substring(0, slash), rest.substring(slash + 1));
    }

    public record ObjectRef(String bucket, String key) {}
}
