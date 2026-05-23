package com.qaplatform.android.automation.domain;

import com.qaplatform.android.automation.locator.LocatorStrategy;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "elements", schema = "android_automation")
public class ElementEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(nullable = false, length = 160)         private String name;
    @Column(columnDefinition = "TEXT")              private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_strategy", nullable = false, length = 32)
    private LocatorStrategy primaryStrategy;

    @Column(name = "primary_value", nullable = false, columnDefinition = "TEXT")
    private String primaryValue;

    /** JSON array text of {strategy,value} pairs. Service layer converts to/from List<Locator>. */
    @Column(name = "fallback_locators", nullable = false, columnDefinition = "TEXT")
    private String fallbackLocatorsJson = "[]";

    @Column(name = "screenshot_data",     columnDefinition = "TEXT") private String screenshotData;
    @Column(name = "sample_bounds",       length = 64)               private String sampleBounds;
    @Column(name = "sample_class")                                   private String sampleClass;
    @Column(name = "sample_text", columnDefinition = "TEXT")         private String sampleText;
    @Column(name = "sample_resource_id")                             private String sampleResourceId;

    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)                    private Instant updatedAt = Instant.now();

    protected ElementEntity() {}

    public ElementEntity(Long productId, Long projectId, String name, LocatorStrategy primaryStrategy, String primaryValue, Long createdByUserId) {
        this.productId = productId;
        this.projectId = projectId;
        this.name = name;
        this.primaryStrategy = primaryStrategy;
        this.primaryValue = primaryValue;
        this.createdByUserId = createdByUserId;
    }

    @PreUpdate void touch() { this.updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getProjectId() { return projectId; }
    public String getName() { return name; }                public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }   public void setDescription(String v) { this.description = v; }
    public LocatorStrategy getPrimaryStrategy() { return primaryStrategy; } public void setPrimaryStrategy(LocatorStrategy v) { this.primaryStrategy = v; }
    public String getPrimaryValue() { return primaryValue; } public void setPrimaryValue(String v) { this.primaryValue = v; }
    public String getFallbackLocatorsJson() { return fallbackLocatorsJson; } public void setFallbackLocatorsJson(String v) { this.fallbackLocatorsJson = v; }
    public String getScreenshotData() { return screenshotData; } public void setScreenshotData(String v) { this.screenshotData = v; }
    public String getSampleBounds() { return sampleBounds; } public void setSampleBounds(String v) { this.sampleBounds = v; }
    public String getSampleClass() { return sampleClass; }   public void setSampleClass(String v) { this.sampleClass = v; }
    public String getSampleText() { return sampleText; }     public void setSampleText(String v) { this.sampleText = v; }
    public String getSampleResourceId() { return sampleResourceId; } public void setSampleResourceId(String v) { this.sampleResourceId = v; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
