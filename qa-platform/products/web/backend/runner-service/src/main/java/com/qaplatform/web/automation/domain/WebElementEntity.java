package com.qaplatform.web.automation.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Named locator the user has curated. Mirrors Android's
 * {@code ElementEntity} but uses Playwright's locator vocabulary instead
 * of Appium's. Fallback locators (alternate strategies for the same UI
 * element) are stored as a JSON array of {@code {strategy, value}} pairs;
 * the service layer (de)serialises through Jackson.
 *
 * <p>UNIQUE constraint on {@code (project_id, name)} forces names to be
 * unambiguous within a project so steps can reference by name in audit
 * logs and humans aren't confused by two elements called "login-button".</p>
 */
@Entity
@Table(name = "elements", schema = "web_automation")
public class WebElementEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(nullable = false, length = 160)        private String name;
    @Column(columnDefinition = "TEXT")             private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_strategy", nullable = false, length = 32)
    private WebLocatorStrategy primaryStrategy;

    @Column(name = "primary_value", nullable = false, columnDefinition = "TEXT")
    private String primaryValue;

    /** JSON array of {strategy, value}; service layer maps to List<Locator>. */
    @Column(name = "fallback_locators", nullable = false, columnDefinition = "TEXT")
    private String fallbackLocatorsJson = "[]";

    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)                    private Instant updatedAt = Instant.now();

    protected WebElementEntity() {}

    public WebElementEntity(Long projectId, String name, WebLocatorStrategy primaryStrategy,
                            String primaryValue, Long createdByUserId) {
        this.projectId = projectId;
        this.name = name;
        this.primaryStrategy = primaryStrategy;
        this.primaryValue = primaryValue;
        this.createdByUserId = createdByUserId;
    }

    @PreUpdate void touch() { this.updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getName() { return name; }                public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }   public void setDescription(String v) { this.description = v; }
    public WebLocatorStrategy getPrimaryStrategy() { return primaryStrategy; } public void setPrimaryStrategy(WebLocatorStrategy v) { this.primaryStrategy = v; }
    public String getPrimaryValue() { return primaryValue; } public void setPrimaryValue(String v) { this.primaryValue = v; }
    public String getFallbackLocatorsJson() { return fallbackLocatorsJson; } public void setFallbackLocatorsJson(String v) { this.fallbackLocatorsJson = v; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
