package com.qaplatform.web.automation.api.dto;

import com.qaplatform.web.automation.domain.WebLocatorStrategy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public class WebElementDtos {

    /** Locator names: lowercase kebab-case so they survive copy/paste from selectors. */
    private static final String NAME_REGEX = "^[a-z0-9][a-z0-9-]{0,158}[a-z0-9]$";

    public record Locator(@NotNull WebLocatorStrategy strategy, @NotBlank String value) {}

    public record CreateRequest(
            @NotBlank @Pattern(regexp = NAME_REGEX, message = "must be lowercase kebab-case (a-z, 0-9, -)")
            String name,
            @Size(max = 4000) String description,
            @NotNull WebLocatorStrategy primaryStrategy,
            @NotBlank String primaryValue,
            List<Locator> fallbackLocators
    ) {}

    public record UpdateRequest(
            @NotBlank @Pattern(regexp = NAME_REGEX) String name,
            String description,
            @NotNull WebLocatorStrategy primaryStrategy,
            @NotBlank String primaryValue,
            List<Locator> fallbackLocators
    ) {}

    public record View(
            long id,
            String name,
            String description,
            WebLocatorStrategy primaryStrategy,
            String primaryValue,
            List<Locator> fallbackLocators,
            long createdByUserId,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
