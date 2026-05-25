package com.qaplatform.android.automation.domain;

/**
 * Predicate carried by an {@link StepAction#IF} step. Serialised as JSON
 * into {@code android_automation.steps.condition} and evaluated at run
 * time by {@code AndroidConditionEvaluator}.
 *
 * <p>Shape mirrors the WEB stack's {@code WebStepCondition} so the
 * frontend's ConditionBuilder UI can be a near-direct port. Two subject
 * types in v1:</p>
 * <ul>
 *   <li>{@code element_state} — checks an element from the catalog
 *       against an UIAutomator visibility / text predicate (e.g. "OTP
 *       dialog visible"). {@link #subjectId} is the
 *       {@code android_automation.elements.id}; {@link #value} is null
 *       for visibility ops, the expected text for text-match ops.</li>
 *   <li>{@code test_data_compare} — checks a value in the test-data
 *       catalog against a literal. {@link #subjectId} is the
 *       {@code android_automation.test_data.id}; {@link #value} is the
 *       comparison operand.</li>
 * </ul>
 *
 * <p>Operator vocabulary by type:</p>
 * <ul>
 *   <li>element_state: {@code is_visible, is_hidden, exists,
 *       text_contains, text_equals}</li>
 *   <li>test_data_compare: {@code equals, not_equals, contains,
 *       greater_than, less_than}</li>
 * </ul>
 */
public record StepCondition(
        String type,
        Long   subjectId,
        String operator,
        String value
) {}
