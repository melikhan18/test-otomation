package com.qaplatform.web.automation.domain;

/**
 * Action taxonomy for the v1 web step DSL.
 *
 * <p>Designed for Playwright primitives — every action maps cleanly to a
 * {@code Page} / {@code Locator} method call. Names follow Android's
 * convention (uppercase enum) so the cross-platform reports aggregator can
 * still distinguish "FILL" from "ENTER_TEXT" via the {@code platform}
 * dimension if a dashboard needs to group by action.</p>
 *
 * <p>Categories (mirrors {@code WebStepExecutor.categoryOf} dispatch):</p>
 *
 * <ul>
 *   <li><strong>Control</strong> — branching primitives that wrap child steps (IF)</li>
 *   <li><strong>Navigation</strong> — change the page's URL / history</li>
 *   <li><strong>Interaction</strong> — pointer + keyboard input on a locator</li>
 *   <li><strong>Wait</strong> — bounded delays + readiness checks</li>
 *   <li><strong>Assert</strong> — verify a condition; failure marks the step FAILED</li>
 *   <li><strong>Util</strong> — orchestrator-side helpers (screenshot, comment, JS eval)</li>
 * </ul>
 */
public enum WebStepAction {
    // Control flow. An IF step is a tree node, not a leaf: it carries a
    // {@code condition} JSON and acts as the parent of its child steps,
    // which live under branch_label = "then" or "else". ELSE is NOT a
    // standalone action — it's a branch label on an IF's children.
    IF,

    // Navigation
    GOTO,
    RELOAD,
    GO_BACK,
    GO_FORWARD,

    // Interaction
    CLICK,
    DBL_CLICK,
    FILL,
    PRESS_KEY,
    CHECK,
    UNCHECK,
    SELECT,
    HOVER,

    // Wait
    WAIT_FOR_SELECTOR,
    WAIT_FOR_LOAD_STATE,    // value: "load" | "domcontentloaded" | "networkidle"
    SLEEP,

    // Assert
    ASSERT_VISIBLE,
    ASSERT_HIDDEN,
    ASSERT_TEXT_EQUALS,
    ASSERT_TEXT_CONTAINS,
    ASSERT_URL_EQUALS,
    ASSERT_URL_CONTAINS,
    ASSERT_TITLE_EQUALS,
    ASSERT_TITLE_CONTAINS,
    ASSERT_ATTRIBUTE,       // value: "attrName=expectedValue"

    // Util
    SCREENSHOT,
    COMMENT,
    EVAL_JS
}
