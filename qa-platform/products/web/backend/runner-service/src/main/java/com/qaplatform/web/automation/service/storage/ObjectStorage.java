package com.qaplatform.web.automation.service.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

/**
 * S3 SDK wrapper for MinIO uploads — clone of Android's ObjectStorage but
 * with the extra {@link #uploadTrace} entry point (Playwright trace.zip
 * is a web-only artifact).
 *
 * <p>Returns public URLs stamped with {@code app.storage.public-url} so the
 * web console can open them directly without S3 presigning.</p>
 */
@Component
public class ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorage.class);

    private final String endpoint;
    private final String publicUrl;
    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final String screenshotsBucket;
    private final String videosBucket;
    private final String tracesBucket;
    private S3Client s3;

    public ObjectStorage(@Value("${app.storage.endpoint}") String endpoint,
                         @Value("${app.storage.public-url}") String publicUrl,
                         @Value("${app.storage.access-key}") String accessKey,
                         @Value("${app.storage.secret-key}") String secretKey,
                         @Value("${app.storage.region}") String region,
                         @Value("${app.storage.screenshots-bucket}") String screenshotsBucket,
                         @Value("${app.storage.videos-bucket}") String videosBucket,
                         @Value("${app.storage.traces-bucket}") String tracesBucket) {
        this.endpoint = endpoint;
        this.publicUrl = publicUrl;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
        this.screenshotsBucket = screenshotsBucket;
        this.videosBucket = videosBucket;
        this.tracesBucket = tracesBucket;
    }

    @PostConstruct
    void init() {
        // Path-style access required for MinIO; virtual-host style only works
        // against AWS S3.
        S3Configuration cfg = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(cfg)
                .build();
        log.info("ObjectStorage initialised — endpoint={} public-url={}", endpoint, publicUrl);
    }

    public String uploadScreenshot(String key, byte[] data) {
        return put(screenshotsBucket, key, data, "image/png");
    }

    public String uploadVideo(String key, byte[] data) {
        return put(videosBucket, key, data, "video/webm");
    }

    public String uploadTrace(String key, byte[] data) {
        // Playwright trace.zip — `application/zip` so the browser offers it as
        // a download rather than trying to render it.
        return put(tracesBucket, key, data, "application/zip");
    }

    private String put(String bucket, String key, byte[] data, String contentType) {
        if (data == null || data.length == 0) return null;
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data));
        return publicUrl + "/" + bucket + "/" + key;
    }
}
