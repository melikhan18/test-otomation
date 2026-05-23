package com.devicefarm.automation.domain;

/**
 * The full action vocabulary supported by the step editor. Each value matches the
 * {@code action} CHECK constraint stored in {@code automation.steps}.
 *
 * Capability matrix (what fields each action consumes):
 *
 * <pre>
 *   ACTION                  | element | data/literal              | timeout | extra
 *   ------------------------+---------+---------------------------+---------+--------
 *   CLICK                   |  yes    | -                         |  -      | -
 *   LONG_PRESS              |  yes    | -                         |  -      | duration in literalValue (ms)
 *   SWIPE                   |  yes    | direction in literalValue |  -      | -
 *   ENTER_TEXT              |  yes    | data ref OR literal       |  -      | -
 *   CLEAR                   |  yes    | -                         |  -      | -
 *   PRESS_KEY               |   -     | keyCode in literalValue   |  -      | -
 *   WAIT_FOR_VISIBLE        |  yes    | -                         | yes     | -
 *   WAIT_FOR_INVISIBLE      |  yes    | -                         | yes     | -
 *   SLEEP                   |   -     | ms in literalValue        |  -      | -
 *   ASSERT_VISIBLE          |  yes    | -                         |  -      | -
 *   ASSERT_NOT_PRESENT      |  yes    | -                         |  -      | -
 *   ASSERT_TEXT_EQUALS      |  yes    | data ref OR literal       |  -      | -
 *   ASSERT_TEXT_CONTAINS    |  yes    | data ref OR literal       |  -      | -
 *   SCREENSHOT              |   -     | label in literalValue     |  -      | -
 *   COMMENT                 |   -     | text in literalValue      |  -      | (no-op)
 * </pre>
 */
public enum StepAction {
    // ── touch / input ────────────────────────────────────────────────
    CLICK,
    LONG_PRESS,
    SWIPE,
    ENTER_TEXT,
    CLEAR,
    PRESS_KEY,

    // ── wait ─────────────────────────────────────────────────────────
    WAIT_FOR_VISIBLE,
    WAIT_FOR_INVISIBLE,
    SLEEP,

    // ── visibility & presence ────────────────────────────────────────
    ASSERT_VISIBLE,
    ASSERT_NOT_VISIBLE,       // element exists in tree but is hidden / off-screen
    ASSERT_NOT_PRESENT,       // element absent from the tree entirely

    // ── interactive state ────────────────────────────────────────────
    ASSERT_ENABLED,
    ASSERT_DISABLED,
    ASSERT_CHECKED,
    ASSERT_UNCHECKED,
    ASSERT_SELECTED,
    ASSERT_FOCUSED,

    // ── text + value content ─────────────────────────────────────────
    ASSERT_TEXT_EQUALS,
    ASSERT_TEXT_CONTAINS,
    ASSERT_TEXT_MATCHES,      // regex
    ASSERT_VALUE_EQUALS,      // EditText value (vs visible text)
    ASSERT_ATTRIBUTE,         // generic "<attrName>=<expected>" pair in literal

    // ── util ─────────────────────────────────────────────────────────
    SCREENSHOT,
    COMMENT
}
