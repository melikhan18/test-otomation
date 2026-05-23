package com.devicefarm.automation.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "test_data", schema = "automation")
public class TestDataEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(nullable = false, length = 160)         private String name;
    @Column(nullable = false, length = 32)          private String environment = "default";
    @Column(nullable = false, columnDefinition = "TEXT") private String value;
    @Column(columnDefinition = "TEXT")              private String description;
    @Column(nullable = false)                       private boolean sensitive = false;

    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)                    private Instant updatedAt = Instant.now();

    protected TestDataEntity() {}

    public TestDataEntity(Long productId, Long projectId, String name, String environment, String value, Long createdByUserId) {
        this.productId = productId;
        this.projectId = projectId;
        this.name = name;
        this.environment = environment;
        this.value = value;
        this.createdByUserId = createdByUserId;
    }

    @PreUpdate void touch() { this.updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getProjectId() { return projectId; }
    public String getName() { return name; }                public void setName(String v) { this.name = v; }
    public String getEnvironment() { return environment; }  public void setEnvironment(String v) { this.environment = v; }
    public String getValue() { return value; }              public void setValue(String v) { this.value = v; }
    public String getDescription() { return description; }   public void setDescription(String v) { this.description = v; }
    public boolean isSensitive() { return sensitive; }       public void setSensitive(boolean v) { this.sensitive = v; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
