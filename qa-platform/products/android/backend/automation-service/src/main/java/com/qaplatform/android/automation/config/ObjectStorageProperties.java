package com.qaplatform.android.automation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound to the {@code app.storage} block in application.yml. Two URLs because the
 * service-side and browser-side reach MinIO through different addresses (Docker DNS
 * vs. host port mapping).
 */
@ConfigurationProperties(prefix = "app.storage")
public class ObjectStorageProperties {
    private String endpoint;
    private String publicUrl;
    private String accessKey;
    private String secretKey;
    private String region = "us-east-1";
    private String screenshotsBucket = "screenshots";
    private String videosBucket      = "videos";
    private String apksBucket        = "apks";

    public String getEndpoint() { return endpoint; }                     public void setEndpoint(String v) { this.endpoint = v; }
    public String getPublicUrl() { return publicUrl; }                   public void setPublicUrl(String v) { this.publicUrl = v; }
    public String getAccessKey() { return accessKey; }                   public void setAccessKey(String v) { this.accessKey = v; }
    public String getSecretKey() { return secretKey; }                   public void setSecretKey(String v) { this.secretKey = v; }
    public String getRegion() { return region; }                         public void setRegion(String v) { this.region = v; }
    public String getScreenshotsBucket() { return screenshotsBucket; }   public void setScreenshotsBucket(String v) { this.screenshotsBucket = v; }
    public String getVideosBucket() { return videosBucket; }             public void setVideosBucket(String v) { this.videosBucket = v; }
    public String getApksBucket() { return apksBucket; }                 public void setApksBucket(String v) { this.apksBucket = v; }
}
