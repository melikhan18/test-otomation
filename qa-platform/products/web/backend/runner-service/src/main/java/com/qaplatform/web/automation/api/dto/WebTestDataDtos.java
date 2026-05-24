package com.qaplatform.web.automation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public class WebTestDataDtos {

    private static final String NAME_REGEX = "^[a-z0-9][a-z0-9-]{0,158}[a-z0-9]$";
    private static final String ENV_REGEX  = "^[a-z][a-z0-9-]{0,30}$";

    public record CreateRequest(
            @NotBlank @Pattern(regexp = NAME_REGEX, message = "lowercase kebab-case") String name,
            @NotBlank @Pattern(regexp = ENV_REGEX,  message = "lowercase identifier")  String environment,
            @NotBlank String value,
            String description,
            boolean sensitive
    ) {}

    public record UpdateRequest(
            @NotBlank @Pattern(regexp = NAME_REGEX) String name,
            @NotBlank @Pattern(regexp = ENV_REGEX)  String environment,
            @NotBlank String value,
            String description,
            boolean sensitive
    ) {}

    /** Masked when {@code sensitive} unless caller asks for reveal. */
    public record View(
            long id,
            String name,
            String environment,
            String value,        // "••••••••" when sensitive && !revealed
            String description,
            boolean sensitive,
            boolean masked,
            long createdByUserId,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
