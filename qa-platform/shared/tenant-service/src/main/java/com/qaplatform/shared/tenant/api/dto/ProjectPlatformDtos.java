package com.qaplatform.shared.tenant.api.dto;

import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

public class ProjectPlatformDtos {

    /** One enabled platform on a project. */
    public record PlatformView(
            String platform,
            Instant enabledAt,
            Long enabledBy
    ) {}

    /** Aggregated response for "what's enabled on this project". */
    public record ProjectPlatformsView(
            long projectId,
            List<PlatformView> platforms
    ) {}

    /**
     * Enable a platform on a project. Validation: must be one of the known
     * {@code Platform} enum values. Server is the source of truth — clients
     * sending unknown platforms get a 400.
     */
    public record EnableRequest(
            @Pattern(regexp = "^(ANDROID|IOS|BACKEND|WEB)$", message = "platform must be one of ANDROID|IOS|BACKEND|WEB")
            String platform
    ) {}
}
