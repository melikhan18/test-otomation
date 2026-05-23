package com.qaplatform.android.automation.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_versions", schema = "android_automation")
public class AppVersionEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_id", nullable = false)        private Long appId;
    @Column(name = "version_code", nullable = false)  private Long versionCode;
    @Column(name = "version_name", length = 255)      private String versionName;
    @Column(name = "file_size_bytes", nullable = false) private Long fileSizeBytes;
    @Column(nullable = false, length = 64)            private String sha256;
    @Column(name = "storage_key", nullable = false, columnDefinition = "TEXT") private String storageKey;
    @Column(columnDefinition = "TEXT")                private String notes;
    @Column(name = "uploaded_by_user_id", nullable = false)             private Long uploadedByUserId;
    @Column(name = "uploaded_at", nullable = false, updatable = false) private Instant uploadedAt = Instant.now();

    protected AppVersionEntity() {}

    public AppVersionEntity(Long appId, Long versionCode, String versionName,
                            Long fileSizeBytes, String sha256, String storageKey,
                            Long uploadedByUserId) {
        this.appId = appId;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.fileSizeBytes = fileSizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.uploadedByUserId = uploadedByUserId;
    }

    public Long getId() { return id; }
    public Long getAppId() { return appId; }
    public Long getVersionCode() { return versionCode; }
    public String getVersionName() { return versionName; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public String getSha256() { return sha256; }
    public String getStorageKey() { return storageKey; }
    public String getNotes() { return notes; }              public void setNotes(String v) { this.notes = v; }
    public Long getUploadedByUserId() { return uploadedByUserId; }
    public Instant getUploadedAt() { return uploadedAt; }
}
