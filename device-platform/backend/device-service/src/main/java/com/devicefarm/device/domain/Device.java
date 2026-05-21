package com.devicefarm.device.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "devices", schema = "device")
public class Device {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** When true, only projects listed in device_project_access can see/use this device. */
    @Column(nullable = false)
    private boolean restricted = false;

    @Column(nullable = false, length = 128)
    private String serial;

    @Column(nullable = false, length = 64)
    private String manufacturer;

    @Column(nullable = false, length = 128)
    private String model;

    @Column(name = "android_version", nullable = false, length = 32)
    private String androidVersion;

    @Column(name = "screen_width", nullable = false)
    private int screenWidth;

    @Column(name = "screen_height", nullable = false)
    private int screenHeight;

    @Column(name = "agent_version", length = 32)
    private String agentVersion;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt = Instant.now();

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    protected Device() {}

    public Device(Long productId, Long companyId, String serial, String manufacturer, String model,
                  String androidVersion, int screenWidth, int screenHeight, String agentVersion) {
        this.productId = productId;
        this.companyId = companyId;
        this.serial = serial;
        this.manufacturer = manufacturer;
        this.model = model;
        this.androidVersion = androidVersion;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.agentVersion = agentVersion;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { this.companyId = v; }
    public boolean isRestricted() { return restricted; }
    public void setRestricted(boolean v) { this.restricted = v; }
    public String getSerial() { return serial; }
    public String getManufacturer() { return manufacturer; }
    public String getModel() { return model; }
    public String getAndroidVersion() { return androidVersion; }
    public int getScreenWidth() { return screenWidth; }
    public int getScreenHeight() { return screenHeight; }
    public String getAgentVersion() { return agentVersion; }
    public Instant getEnrolledAt() { return enrolledAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void touchLastSeen() { this.lastSeenAt = Instant.now(); }
}
