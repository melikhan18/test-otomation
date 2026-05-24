package com.qaplatform.web.automation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaplatform.web.automation.api.dto.WebElementDtos.Locator;

import java.util.List;

/**
 * Stateless (de)serialiser for the fallback-locator JSON column on
 * {@code WebElementEntity}. Centralised so the format (a tiny array of
 * {@code {strategy, value}} pairs) is documented in one place.
 */
public final class WebLocatorJson {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Locator>> TYPE = new TypeReference<>() {};

    private WebLocatorJson() {}

    public static String write(List<Locator> locators) {
        try { return JSON.writeValueAsString(locators == null ? List.of() : locators); }
        catch (Exception e) { return "[]"; }
    }

    public static List<Locator> read(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return JSON.readValue(json, TYPE); }
        catch (Exception e) { return List.of(); }
    }
}
