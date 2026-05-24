package com.qaplatform.android.automation.api.dto;

import com.qaplatform.android.automation.locator.Locator;
import com.qaplatform.android.automation.locator.LocatorStrategy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public class ElementDtos {

    /** Locator names use kebab-case ASCII so they survive copy/paste from device IDs. */
    private static final String NAME_REGEX = "^[a-z0-9][a-z0-9-]{0,158}[a-z0-9]$";

    public record CreateRequest(
            @NotBlank @Pattern(regexp = NAME_REGEX, message = "must be lowercase kebab-case (a-z, 0-9, -)")
            String name,
            @Size(max = 4000) String description,
            @NotNull LocatorStrategy primaryStrategy,
            @NotBlank String primaryValue,
            List<Locator> fallbackLocators,
            String screenshotData,        // optional base64 data URL
            String sampleBounds,          // optional "[l,t,r,b]"
            String sampleClass,
            String sampleText,
            String sampleResourceId
    ) {}

    public record UpdateRequest(
            @NotBlank @Pattern(regexp = NAME_REGEX) String name,
            String description,
            @NotNull LocatorStrategy primaryStrategy,
            @NotBlank String primaryValue,
            List<Locator> fallbackLocators
    ) {}

    public record View(
            long id,
            String name,
            String description,
            LocatorStrategy primaryStrategy,
            String primaryValue,
            List<Locator> fallbackLocators,
            String screenshotData,
            String sampleBounds,
            String sampleClass,
            String sampleText,
            String sampleResourceId,
            long createdByUserId,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
