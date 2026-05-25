package com.qaplatform.android.automation.service.run;

import com.qaplatform.android.automation.domain.*;
import com.qaplatform.common.runengine.status.StepResultStatus;
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
            // SCROLL_TO_ELEMENT has its own probe + swipe loop; route it
            // before the category switch so runTouch doesn't have to special-
            // case it (would need a different element-resolution flow).
            if (step.getAction() == StepAction.SCROLL_TO_ELEMENT) {
                return runScrollTo(step, t0);
            }
            return switch (categoryOf(step.getAction())) {
                case TOUCH        -> runTouch(step, t0);
                case INPUT        -> runInput(step, t0);
                case WAIT         -> runWait(step, t0);
                case ASSERT       -> runAssert(step, t0);
                case UTIL         -> runUtil(step, t0);
                // Control-flow rows (IF) are handled by the orchestrator's
                // tree walker, never by StepRunner. If one does slip through
                // (programmer bug) treat as a no-op PASS so the run doesn't
                // crash.
                case CONTROL      -> StepResult.pass(null, elapsed(t0));
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

    /* ─────────────────────────  SCROLL TO  ─────────────────────────── */

    /**
     * Probes for the target element; if not visible, swipes the viewport
     * one full screen in the requested direction and probes again. Repeats
     * up to {@code MAX_SCROLL_SWIPES} times. The first iteration is a
     * "free" probe — if the element is already visible, no swipe happens.
     *
     * <p>Direction semantics are user-facing, not finger-facing:</p>
     * <ul>
     *   <li>{@code down} (default) — look for content below; finger swipes UP</li>
     *   <li>{@code up} — look for content above; finger swipes DOWN</li>
     *   <li>{@code right} — look for content to the right; finger swipes LEFT</li>
     *   <li>{@code left} — look for content to the left; finger swipes RIGHT</li>
     * </ul>
     *
     * <p>This mirrors how users describe the task ("scroll down to find
     * Turkey in the country list") rather than how their finger moves.</p>
     */
    private StepResult runScrollTo(StepEntity step, long t0) {
        String dir = step.getLiteralValue() == null || step.getLiteralValue().isBlank()
                ? "down"
                : step.getLiteralValue().toLowerCase().trim();
        if (!"up".equals(dir) && !"down".equals(dir) && !"left".equals(dir) && !"right".equals(dir)) {
            return StepResult.fail(
                    "SCROLL_TO_ELEMENT direction must be up/down/left/right (was: " + dir + ")",
                    null, elapsed(t0));
        }

        ElementEntity el = elements.findById(step.getTargetElementId()).orElse(null);
        if (el == null) {
            return StepResult.fail("element catalog entry missing", null, elapsed(t0));
        }

        final int maxSwipes = 20;
        final int settleMs = 350;
        // End-of-list detection: if the inspect tree's signature doesn't
        // change for this many consecutive post-swipe probes, the list has
        // stopped scrolling (we've hit the top/bottom edge) so further
        // swipes won't reveal anything new. Short-circuit to fail-fast.
        final int noProgressBudget = 5;
        int noProgressCount = 0;
        Integer prevSignature = null;

        for (int i = 0; i <= maxSwipes; i++) {
            // Single inspect per iteration — reused for element resolution,
            // viewport bounds, AND end-of-list detection.
            JsonNode tree = bridge.inspect(sessionId, sessionToken, 4);

            LocatorResolver.Hit hit = LocatorResolver.resolve(tree, el);
            if (hit != null && hasNonZeroBounds(hit.bounds())) {
                return StepResult.pass(
                        "scrolled to " + locatorLabel(hit) + " after " + i + " swipes",
                        elapsed(t0));
            }

            // Compare to previous iteration's tree. Same hash N times in a
            // row = the swipes aren't moving anything = bail. The signature
            // is JsonNode.toString().hashCode() — cheap, and collisions
            // false-positive ~once per 4 billion comparisons (we need 5 in
            // a row, so effectively zero).
            int signature = tree.toString().hashCode();
            if (prevSignature != null && signature == prevSignature) {
                noProgressCount++;
                if (noProgressCount >= noProgressBudget) {
                    return StepResult.fail(
                            "element not visible — list stopped scrolling after " + (i + 1)
                                    + " swipes (UI unchanged for " + noProgressBudget
                                    + " consecutive attempts, likely reached end of list)",
                            null, elapsed(t0));
                }
            } else {
                noProgressCount = 0;
            }
            prevSignature = signature;

            if (i == maxSwipes) break;   // final probe done, no more swipes

            int[] vp = viewportBounds(tree);
            if (vp == null || (vp[2] - vp[0]) <= 0 || (vp[3] - vp[1]) <= 0) {
                return StepResult.fail("could not read viewport bounds from inspect tree", null, elapsed(t0));
            }

            float w = vp[2] - vp[0], h = vp[3] - vp[1];
            float cx = vp[0] + w / 2f, cy = vp[1] + h / 2f;
            float sx = cx, sy = cy, ex = cx, ey = cy;
            switch (dir) {
                case "down"  -> { sy = vp[1] + h * 0.75f; ey = vp[1] + h * 0.25f; }
                case "up"    -> { sy = vp[1] + h * 0.25f; ey = vp[1] + h * 0.75f; }
                case "right" -> { sx = vp[0] + w * 0.75f; ex = vp[0] + w * 0.25f; }
                case "left"  -> { sx = vp[0] + w * 0.25f; ex = vp[0] + w * 0.75f; }
            }
            Map<String, Object> cmd = new LinkedHashMap<>();
            cmd.put("type", "swipe");
            cmd.put("startX", sx); cmd.put("startY", sy);
            cmd.put("endX",   ex); cmd.put("endY",   ey);
            cmd.put("durationMs", 300L);
            bridge.control(sessionId, sessionToken, cmd);

            try { Thread.sleep(settleMs); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
        }
        return StepResult.fail(
                "element not visible after " + maxSwipes + " swipes (direction: " + dir + ")",
                null, elapsed(t0));
    }

    private static int[] viewportBounds(JsonNode tree) {
        if (tree == null || !tree.has("root")) return null;
        JsonNode b = tree.get("root").get("bounds");
        if (b == null || !b.isArray() || b.size() < 4) return null;
        return new int[]{ b.get(0).asInt(), b.get(1).asInt(), b.get(2).asInt(), b.get(3).asInt() };
    }

    private static boolean hasNonZeroBounds(int[] b) {
        return b != null && (b[2] - b[0]) > 0 && (b[3] - b[1]) > 0;
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

    enum Category { TOUCH, INPUT, WAIT, ASSERT, UTIL, CONTROL }

    private static Category categoryOf(StepAction a) {
        return switch (a) {
            // IF is the orchestrator's responsibility (tree walker picks the
            // branch); it never reaches StepRunner.dispatch as a leaf, so
            // we bucket it under CONTROL and treat as a no-op if the
            // executor does see one (programmer error guard).
            case IF                                             -> Category.CONTROL;
            case CLICK, LONG_PRESS, SWIPE, SCROLL_TO_ELEMENT    -> Category.TOUCH;
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
