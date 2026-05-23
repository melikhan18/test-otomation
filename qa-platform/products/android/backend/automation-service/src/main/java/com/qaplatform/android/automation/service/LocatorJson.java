package com.qaplatform.android.automation.service;

import com.qaplatform.android.automation.locator.Locator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/** Tiny helper around Jackson for the fallback-locators array stored as TEXT. */
public final class LocatorJson {
    private static final ObjectMapper M = new ObjectMapper();
    private static final TypeReference<List<Locator>> LIST = new TypeReference<>() {};

    private LocatorJson() {}

    public static List<Locator> read(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return M.readValue(json, LIST); }
        catch (Exception e) { return List.of(); }
    }

    public static String write(List<Locator> list) {
        try { return M.writeValueAsString(list == null ? List.of() : list); }
        catch (Exception e) { return "[]"; }
    }
}
