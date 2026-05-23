package com.qaplatform.android.automation.service.run;

import com.qaplatform.android.automation.domain.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Executes a single step against a live device session and produces a {@link StepResult}.
 *
 * The runner is intentionally synchronous and stateless — it relies on the orchestrator
 * for retries / abort policy. For each step:
 *   1. If the action targets an element, pull a fresh inspect tree and resolve it
 *      (with self-healing fallback to alternate locators)
 *   2. Convert the action into a control-command payload + dispatch via the bridge
 *   3. For assertions, re-inspect and check the condition
 */
public class StepRunner {

    private static final Logger log = LoggerFactory.getLogger(StepRunner.class);

    private final BridgeClient bridge;
    private final ElementRepository elements;
    private final TestDataRepository testData;
    private final long sessionId;
    private final String sessionToken;
    private final String environment;

    public StepRunner(BridgeClient bridge, ElementRepository elements, TestDataRepository testData,
                      long sessionId, String sessionToken, String environment) {
        this.bridge = bridge;
        this.elements = elements;
        this.testData = testData;
        this.sessionId = sessionId;
        this.sessionToken = sessionToken;
        this.environment = environment;
    }

    public StepResult run(StepEntity step) {
        long t0 = System.currentTimeMillis();
        try {
            return switch (categoryOf(step.getAction())) {
                case TOUCH        -> runTouch(step, t0);
                case INPUT        -> runInput(step, t0);
                case WAIT         -> runWait(step, t0);
                case ASSERT       -> runAssert(step, t0);
                case UTIL         -> runUtil(step, t0);
            };
        } catch (Exception e) {
            log.warn("step {} threw: {}", step.getId(), e.toString());
            return StepResult.error(e.getMessage(), elapsed(t0));
        }
    }

    /* ─────────────────────────  TOUCH  ─────────────────────────── */

    private StepResult runTouch(StepEntity step, long t0) {
        LocatorResolver.Hit hit = resolveElement(step);
        if (hit == null) return StepResult.fail("element not found", null, elapsed(t0));

        Map<String, Object> cmd = new LinkedHashMap<>();
        switch (step.getAction()) {
            case CLICK -> {
                cmd.put("type", "tap");
                cmd.put("x", hit.centerX()); cmd.put("y", hit.centerY());
            }
            case LONG_PRESS -> {
                cmd.put("type", "tap");
                cmd.put("x", hit.centerX()); cmd.put("y", hit.centerY());
                cmd.put("durationMs", parseLongOr(step.getLiteralValue(), 1000L));
            }
            case SWIPE -> {
                // Direction in literalValue: up | down | left | right (around the element center)
                String dir = (step.getLiteralValue() == null ? "up" : step.getLiteralValue()).toLowerCase();
                int[] b = hit.bounds();
                int w = b[2] - b[0]; int h = b[3] - b[1];
                float cx = hit.centerX(); float cy = hit.centerY();
                float sx = cx, sy = cy, ex = cx, ey = cy;
                switch (dir) {
                    case "left"  -> { sx = b[2] - w * 0.1f; ex = b[0] + w * 0.1f; }
                    case "right" -> { sx = b[0] + w * 0.1f; ex = b[2] - w * 0.1f; }
                    case "up"    -> { sy = b[3] - h * 0.1f; ey = b[1] + h * 0.1f; }
                    case "down"  -> { sy = b[1] + h * 0.1f; ey = b[3] - h * 0.1f; }
                }
                cmd.put("type", "swipe");
                cmd.put("startX", sx); cmd.put("startY", sy);
                cmd.put("endX",   ex); cmd.put("endY",   ey);
                cmd.put("durationMs", 300L);
            }
            default -> throw new IllegalStateException("not a touch action: " + step.getAction());
        }
        bridge.control(sessionId, sessionToken, cmd);
        return StepResult.pass(locatorLabel(hit), elapsed(t0));
    }

    /* ─────────────────────────  INPUT  ─────────────────────────── */

    private StepResult runInput(StepEntity step, long t0) {
        if (step.getAction() == StepAction.PRESS_KEY) {
            // No element — global action
            int keyCode = parseKey(step.getLiteralValue());
            bridge.control(sessionId, sessionToken, Map.of("type", "key", "keyCode", keyCode));
            return StepResult.pass("PRESS_KEY:" + keyCode, elapsed(t0));
        }

        LocatorResolver.Hit hit = resolveElement(step);
        if (hit == null) return StepResult.fail("element not found", null, elapsed(t0));

        if (step.getAction() == StepAction.CLEAR) {
            // Focus by tap, then send an empty text — agent's typeText replaces.
            bridge.control(sessionId, sessionToken, Map.of(
                    "type", "tap", "x", hit.centerX(), "y", hit.centerY()));
            sleep(150);
            bridge.control(sessionId, sessionToken, Map.of("type", "text", "value", ""));
            return StepResult.pass(locatorLabel(hit), elapsed(t0));
        }

        // ENTER_TEXT
        String value = resolveValue(step);
        if (value == null) return StepResult.fail("no value to type", locatorLabel(hit), elapsed(t0));

        // Focus + type
        bridge.control(sessionId, sessionToken, Map.of(
                "type", "tap", "x", hit.centerX(), "y", hit.centerY()));
        sleep(200);
        bridge.control(sessionId, sessionToken, Map.of("type", "text", "value", value));
        return StepResult.pass(locatorLabel(hit), elapsed(t0));
    }

    /* ─────────────────────────  WAIT  ──────────────────────────── */

    private StepResult runWait(StepEntity step, long t0) {
        if (step.getAction() == StepAction.SLEEP) {
            long ms = parseLongOr(step.getLiteralValue(), 1000L);
            sleep(ms);
            return StepResult.pass("sleep " + ms + "ms", elapsed(t0));
        }
        long timeout = step.getTimeoutMs();
        long deadline = System.currentTimeMillis() + timeout;
        boolean wantVisible = step.getAction() == StepAction.WAIT_FOR_VISIBLE;

        while (System.currentTimeMillis() < deadline) {
            LocatorResolver.Hit hit = resolveElement(step);
            boolean present = hit != null;
            if (wantVisible == present) return StepResult.pass(hit != null ? locatorLabel(hit) : "n/a", elapsed(t0));
            sleep(400);
        }
        return StepResult.fail("wait timeout (" + timeout + "ms)", null, elapsed(t0));
    }

    /* ────────────────────────  ASSERT  ─────────────────────────── */

    private StepResult runAssert(StepEntity step, long t0) {
        LocatorResolver.Hit hit = resolveElement(step);
        JsonNode node = hit != null ? hit.node() : null;
        String expected = resolveValue(step);
        AssertionRunner.Result r = AssertionRunner.check(hit, node, step.getAction(), expected);
        return r.ok()
                ? StepResult.pass(hit != null ? locatorLabel(hit) : "n/a", elapsed(t0))
                : StepResult.fail(r.reason(), hit != null ? locatorLabel(hit) : null, elapsed(t0));
    }

    /* ────────────────────────  UTIL  ───────────────────────────── */

    private StepResult runUtil(StepEntity step, long t0) {
        // SCREENSHOT is handled by the orchestrator (it can capture before/after each step).
        // COMMENT is documentation; just succeed.
        return StepResult.pass(null, elapsed(t0));
    }

    /* ─────────────────────  Locator / data resolution ───────────── */

    private LocatorResolver.Hit resolveElement(StepEntity step) {
        if (step.getTargetElementId() == null) return null;
        ElementEntity el = elements.findById(step.getTargetElementId()).orElse(null);
        if (el == null) return null;
        JsonNode tree = bridge.inspect(sessionId, sessionToken, 8);
        return LocatorResolver.resolve(tree, el);
    }

    private String resolveValue(StepEntity step) {
        if (step.getDataId() != null) {
            // Prefer the env-specific value; fall back to "default" if missing.
            TestDataEntity td = testData.findById(step.getDataId()).orElse(null);
            if (td == null) return null;
            if (environment != null && !td.getEnvironment().equals(environment)) {
                var byEnv = testData.findByProjectIdAndNameAndEnvironment(td.getProjectId(), td.getName(), environment);
                if (byEnv.isPresent()) return byEnv.get().getValue();
            }
            return td.getValue();
        }
        return step.getLiteralValue();
    }

    /* ─────────────────────────  helpers  ───────────────────────── */

    enum Category { TOUCH, INPUT, WAIT, ASSERT, UTIL }

    private static Category categoryOf(StepAction a) {
        return switch (a) {
            case CLICK, LONG_PRESS, SWIPE                       -> Category.TOUCH;
            case ENTER_TEXT, CLEAR, PRESS_KEY                   -> Category.INPUT;
            case WAIT_FOR_VISIBLE, WAIT_FOR_INVISIBLE, SLEEP    -> Category.WAIT;
            case ASSERT_VISIBLE, ASSERT_NOT_VISIBLE, ASSERT_NOT_PRESENT,
                 ASSERT_ENABLED, ASSERT_DISABLED,
                 ASSERT_CHECKED, ASSERT_UNCHECKED,
                 ASSERT_SELECTED, ASSERT_FOCUSED,
                 ASSERT_TEXT_EQUALS, ASSERT_TEXT_CONTAINS, ASSERT_TEXT_MATCHES,
                 ASSERT_VALUE_EQUALS, ASSERT_ATTRIBUTE          -> Category.ASSERT;
            case SCREENSHOT, COMMENT                            -> Category.UTIL;
        };
    }

    private static int parseKey(String v) {
        if (v == null) return 4;
        return switch (v.trim().toUpperCase()) {
            case "BACK"    -> 4;
            case "HOME"    -> 3;
            case "RECENTS" -> 187;
            default -> {
                try { yield Integer.parseInt(v.trim()); }
                catch (NumberFormatException e) { yield 4; }
            }
        };
    }

    private static long parseLongOr(String s, long d) {
        if (s == null || s.isBlank()) return d;
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException e) { return d; }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static int elapsed(long t0) { return (int) (System.currentTimeMillis() - t0); }

    private static String locatorLabel(LocatorResolver.Hit hit) {
        return hit.resolvedBy().strategy() + (hit.fallbackIndex() > 0 ? " (fallback #" + hit.fallbackIndex() + ")" : "")
                + " = " + hit.resolvedBy().value();
    }

    /* ─────────────────────────  result type  ───────────────────── */

    public record StepResult(StepResultStatus status, String errorMessage, String resolvedLocator, int durationMs) {
        public static StepResult pass(String locator, int ms) {
            return new StepResult(StepResultStatus.PASSED, null, locator, ms);
        }
        public static StepResult fail(String reason, String locator, int ms) {
            return new StepResult(StepResultStatus.FAILED, reason, locator, ms);
        }
        public static StepResult error(String reason, int ms) {
            return new StepResult(StepResultStatus.ERROR, reason, null, ms);
        }
    }
}
