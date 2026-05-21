package com.devicefarm.device.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public class DeviceDtos {
    public enum DeviceStatus { ONLINE, OFFLINE, IN_USE }

    public record DeviceView(long id, long productId, String serial, String manufacturer, String model,
                             String androidVersion, int screenWidth, int screenHeight,
                             String agentVersion, Instant enrolledAt, Instant lastSeenAt,
                             DeviceStatus status, Long currentSessionId) {}

    /**
     * Cross-tenant view used by the platform-admin devices page. Adds {@code companyId}
     * and {@code restricted} so the UI can render a tenant column and the per-row
     * access toggle without an extra round-trip.
     */
    public record AdminDeviceView(long id, long productId, Long companyId, String serial,
                                   String manufacturer, String model,
                                   String androidVersion, int screenWidth, int screenHeight,
                                   String agentVersion, Instant enrolledAt, Instant lastSeenAt,
                                   DeviceStatus status, Long currentSessionId,
                                   boolean restricted, int accessProjectCount) {}

    /** Admin reassign payload — move a device to a different tenant. */
    public record AdminReassignRequest(@jakarta.validation.constraints.NotNull Long companyId) {}

    public record EnrollmentTokenView(String token, Instant expiresAt) {}

    public record EnrollRequest(@NotBlank String enrollmentToken,
                                @NotBlank String serial,
                                @NotBlank String manufacturer,
                                @NotBlank String model,
                                @NotBlank String androidVersion,
                                @Min(1) int screenWidth,
                                @Min(1) int screenHeight,
                                String agentVersion) {}

    public record EnrollResponse(long deviceId, long productId, String agentToken, String wsUrl) {}

    public record HeartbeatRequest(String agentVersion) {}
}
