package com.qaplatform.web.automation.service.run.runengine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaplatform.common.runengine.spi.RunStep;
import com.qaplatform.web.automation.domain.WebStepEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapts {@link WebStepEntity} → F6 {@link RunStep}. Same pattern as the
 * Android adapter — the entity stays JPA-managed, the wrapper just defers.
 */
public final class StepEntityRunStep {

    private static final ObjectMapper JSON = new ObjectMapper();

    private StepEntityRunStep() {}

    public static RunStep of(WebStepEntity step) {
        return new RunStep() {
            @Override public long id() { return step.getId(); }
            @Override public int orderIndex() { return step.getOrderIndex(); }
            @Override public String action() { return step.getAction().name(); }
            @Override public String payload() {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("selector", step.getSelector());
                p.put("value", step.getValue());
                p.put("screenshotAfter", step.isScreenshotAfter());
                try { return JSON.writeValueAsString(p); }
                catch (JsonProcessingException e) { return "{}"; }
            }
            @Override public Integer timeoutMs() { return step.getTimeoutMs(); }
            @Override public String toString() { return "RunStep[id=" + id() + ", action=" + action() + "]"; }
        };
    }
}
