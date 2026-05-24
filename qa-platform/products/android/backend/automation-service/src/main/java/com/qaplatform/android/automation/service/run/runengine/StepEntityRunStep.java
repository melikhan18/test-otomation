package com.qaplatform.android.automation.service.run.runengine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaplatform.android.automation.domain.StepEntity;
import com.qaplatform.common.runengine.spi.RunStep;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapts the Android {@link StepEntity} to the platform-agnostic
 * {@link RunStep} contract from {@code com.qaplatform.common.runengine.spi}.
 *
 * <p>{@link #of(StepEntity)} returns a tiny anonymous {@code RunStep} that
 * just defers to the entity's getters. We don't copy fields — the entity is
 * already managed by JPA and lives for the run's duration, so the wrapper is
 * cheap and always reads through to the current state.</p>
 *
 * <p>{@link RunStep#payload()} returns a JSON view of the step's literal
 * fields (target element id, data id, literal value, screenshotAfter flag).
 * The Android executor doesn't actually parse this — it re-fetches the
 * entity by id and reads typed fields directly — but the JSON satisfies the
 * cross-platform contract and is useful for trace logs / debug dumps.</p>
 */
public final class StepEntityRunStep {

    private static final ObjectMapper JSON = new ObjectMapper();

    private StepEntityRunStep() {}

    public static RunStep of(StepEntity step) {
        return new RunStep() {
            @Override public long id() { return step.getId(); }
            @Override public int orderIndex() { return step.getOrderIndex(); }
            @Override public String action() { return step.getAction().name(); }
            @Override public String payload() {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("targetElementId", step.getTargetElementId());
                p.put("dataId", step.getDataId());
                p.put("literalValue", step.getLiteralValue());
                p.put("screenshotAfter", step.isScreenshotAfter());
                p.put("retryCount", step.getRetryCount());
                try { return JSON.writeValueAsString(p); }
                catch (JsonProcessingException e) { return "{}"; }
            }
            @Override public Integer timeoutMs() { return step.getTimeoutMs(); }
            @Override public String toString() { return "RunStep[id=" + id() + ", action=" + action() + "]"; }
        };
    }
}
