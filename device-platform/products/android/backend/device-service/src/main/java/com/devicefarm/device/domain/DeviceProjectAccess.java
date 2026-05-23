package com.devicefarm.device.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Whitelist row connecting a {@link Device} (restricted = true) to a project that
 * may use it. When {@code restricted = false} this table is irrelevant — every
 * project in the device's company can see and reserve it.
 */
@Entity
@Table(name = "device_project_access", schema = "device")
@IdClass(DeviceProjectAccess.Key.class)
public class DeviceProjectAccess {

    @Id @Column(name = "device_id")  private Long deviceId;
    @Id @Column(name = "project_id") private Long projectId;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt = Instant.now();

    @Column(name = "granted_by")
    private Long grantedBy;

    protected DeviceProjectAccess() {}

    public DeviceProjectAccess(Long deviceId, Long projectId, Long grantedBy) {
        this.deviceId = deviceId;
        this.projectId = projectId;
        this.grantedBy = grantedBy;
    }

    public Long getDeviceId()  { return deviceId; }
    public Long getProjectId() { return projectId; }
    public Instant getGrantedAt() { return grantedAt; }
    public Long getGrantedBy() { return grantedBy; }

    public static class Key implements Serializable {
        private Long deviceId;
        private Long projectId;
        public Key() {}
        public Key(Long deviceId, Long projectId) { this.deviceId = deviceId; this.projectId = projectId; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return Objects.equals(deviceId, k.deviceId) && Objects.equals(projectId, k.projectId);
        }
        @Override public int hashCode() { return Objects.hash(deviceId, projectId); }
    }
}
