package com.qaplatform.web.automation.domain;

/**
 * Predicate carried by an {@link WebStepAction#IF} step. Serialised as
 * JSON into {@code web_automation.steps.condition} (JSONB column) and
 * evaluated at run-time by {@code ConditionEvaluator}.
 *
 * <p>Two subject types in v1:</p>
 * <ul>
 *   <li>{@code element_state} — checks an element from the catalog against
 *       a Playwright visibility / text predicate (e.g. "OTP modal visible").
 *       {@link #subjectId} is the {@code web_automation.elements.id};
 *       {@link #value} is null for visibility ops, the expected text for
 *       text-match ops.</li>
 *   <li>{@code test_data_compare} — checks a value in the test-data
 *       catalog against a literal (e.g. "env user role equals admin").
 *       {@link #subjectId} is the {@code web_automation.test_data.id};
 *       {@link #value} is the comparison operand.</li>
 * </ul>
 *
 * <p>Operator vocabulary by type:</p>
 * <ul>
 *   <li>element_state: {@code is_visible, is_hidden, exists,
 *       text_contains, text_equals}</li>
 *   <li>test_data_compare: {@code equals, not_equals, contains,
 *       greater_than, less_than}</li>
 * </ul>
 *
 * <p>Raw JS escape hatch is deliberately omitted in v1 — keeps conditions
 * statically analysable and the editor's triple-dropdown UX honest.</p>
 */
public record WebStepCondition(
        String type,
        Long   subjectId,
        String operator,
        String value
) {}
