package com.qaplatform.android.automation.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Tag normalisation rules — kept here so RunService and SuiteRunService can't drift.
 *
 * <ul>
 *   <li>Trim + collapse whitespace, lowercase</li>
 *   <li>Drop empties</li>
 *   <li>De-dup (preserve first-seen order)</li>
 *   <li>Cap label length to 32 chars; cap total tags to 16</li>
 * </ul>
 */
public final class Tags {
    private Tags() {}

    public static final int MAX_LABEL_LEN = 32;
    public static final int MAX_TAGS      = 16;

    public static String[] normalize(List<String> raw) {
        if (raw == null || raw.isEmpty()) return new String[0];
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String t : raw) {
            if (t == null) continue;
            String norm = t.trim().toLowerCase().replaceAll("\\s+", "-");
            if (norm.isEmpty()) continue;
            if (norm.length() > MAX_LABEL_LEN) norm = norm.substring(0, MAX_LABEL_LEN);
            seen.add(norm);
            if (seen.size() >= MAX_TAGS) break;
        }
        return seen.toArray(new String[0]);
    }

    public static List<String> asList(String[] arr) {
        if (arr == null) return List.of();
        return Arrays.asList(arr);
    }
}
