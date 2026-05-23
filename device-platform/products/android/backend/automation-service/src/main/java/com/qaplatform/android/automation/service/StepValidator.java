package com.qaplatform.android.automation.service;

import com.qaplatform.android.automation.domain.StepAction;
import com.qaplatform.common.error.ApiException;

/**
 * Centralised business rules for what fields each {@link StepAction} requires.
 * Mirrors the capability matrix documented on {@code StepAction}.
 *
 * Called from {@code ScenarioService} on every create/update so the persistence layer can
 * trust that {@code targetElementId} is present where the runner expects it, {@code dataId
 * OR literalValue} is set for parameterised actions, etc.
 */
public final class StepValidator {

    private StepValidator() {}

    public static void validate(
            StepAction action,
            Long targetElementId,
            Long dataId,
            String literalValue
    ) {
        switch (action) {
            // ── element-only (touch + waits + boolean-state asserts) ──
            case CLICK, LONG_PRESS, SWIPE, CLEAR,
                 WAIT_FOR_VISIBLE, WAIT_FOR_INVISIBLE,
                 ASSERT_VISIBLE, ASSERT_NOT_VISIBLE, ASSERT_NOT_PRESENT,
                 ASSERT_ENABLED, ASSERT_DISABLED,
                 ASSERT_CHECKED, ASSERT_UNCHECKED,
                 ASSERT_SELECTED, ASSERT_FOCUSED -> {
                requireElement(action, targetElementId);
                forbidData(action, dataId);
            }

            // ── element + (data | literal) — content assertions + text input
            case ENTER_TEXT,
                 ASSERT_TEXT_EQUALS, ASSERT_TEXT_CONTAINS,
                 ASSERT_TEXT_MATCHES, ASSERT_VALUE_EQUALS -> {
                requireElement(action, targetElementId);
                requireDataOrLiteral(action, dataId, literalValue);
            }

            // ── element + literal-only (attribute key=value) ──────
            case ASSERT_ATTRIBUTE -> {
                requireElement(action, targetElementId);
                forbidData(action, dataId);
                if (literalValue == null || !literalValue.contains("=")) {
                    throw ApiException.badRequest(
                        action.name() + " requires literalValue in 'attribute=expected' form (e.g. 'enabled=true')");
                }
            }

            // ── literal only ──────────────────────────────────────
            case PRESS_KEY, SLEEP, SCREENSHOT, COMMENT -> {
                forbidElement(action, targetElementId);
                forbidData(action, dataId);
                if (literalValue == null || literalValue.isBlank()) {
                    throw ApiException.badRequest(action.name() + " requires a literalValue");
                }
            }
        }
    }

    private static void requireElement(StepAction a, Long id) {
        if (id == null) throw ApiException.badRequest(a.name() + " requires a targetElementId");
    }
    private static void forbidElement(StepAction a, Long id) {
        if (id != null) throw ApiException.badRequest(a.name() + " does not accept a targetElementId");
    }
    private static void requireDataOrLiteral(StepAction a, Long dataId, String literal) {
        boolean hasLiteral = literal != null && !literal.isBlank();
        if ((dataId == null) == !hasLiteral) {
            // ok: exactly one is set
            if (dataId == null && !hasLiteral) {
                throw ApiException.badRequest(a.name() + " requires either dataId or literalValue");
            }
            return;
        }
        throw ApiException.badRequest(a.name() + " accepts exactly one of dataId or literalValue, not both");
    }
    private static void forbidData(StepAction a, Long dataId) {
        if (dataId != null) throw ApiException.badRequest(a.name() + " does not accept a dataId");
    }
}
