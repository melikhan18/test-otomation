package com.devicefarm.automation.service.run;

import com.devicefarm.automation.domain.StepAction;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Checks the boolean condition implied by an assertion {@link StepAction} against the
 * resolved node + the full inspect tree. The actual element resolution is done by
 * {@link LocatorResolver}; this class only translates an action into a yes/no on the
 * data the resolver returned.
 *
 * Returns a {@link Result} that the {@link StepRunner} converts into PASSED / FAILED.
 */
public class AssertionRunner {

    public record Result(boolean ok, String reason) {
        public static Result pass()                  { return new Result(true,  null); }
        public static Result fail(String reason)     { return new Result(false, reason); }
    }

    /**
     * @param resolverHit  resolver's hit on the element, or null if the element wasn't found
     * @param node         the matching JSON node (when hit != null) — to read attributes
     * @param action       the assertion action
     * @param expected     the expected literal/data value (for text/value/attribute asserts)
     */
    public static Result check(LocatorResolver.Hit resolverHit, JsonNode node,
                               StepAction action, String expected) {
        boolean elementFound = resolverHit != null && node != null;

        return switch (action) {
            case ASSERT_VISIBLE        -> elementFound && hasNonZeroBounds(resolverHit.bounds())
                    ? Result.pass()
                    : Result.fail("element not visible");
            case ASSERT_NOT_VISIBLE    -> !elementFound || !hasNonZeroBounds(resolverHit.bounds())
                    ? Result.pass()
                    : Result.fail("element is visible (bounds present)");
            case ASSERT_NOT_PRESENT    -> !elementFound
                    ? Result.pass()
                    : Result.fail("element is present in the tree");
            case ASSERT_ENABLED        -> requireBool(elementFound, node, "enabled", true);
            case ASSERT_DISABLED       -> requireBool(elementFound, node, "enabled", false);
            case ASSERT_CHECKED        -> requireBool(elementFound, node, "checked", true);
            case ASSERT_UNCHECKED      -> requireBool(elementFound, node, "checked", false);
            case ASSERT_SELECTED       -> requireBool(elementFound, node, "selected", true);
            case ASSERT_FOCUSED        -> requireBool(elementFound, node, "focused", true);
            case ASSERT_TEXT_EQUALS    -> compareText(elementFound, node, "text", expected, Mode.EQUALS);
            case ASSERT_TEXT_CONTAINS  -> compareText(elementFound, node, "text", expected, Mode.CONTAINS);
            case ASSERT_TEXT_MATCHES   -> compareText(elementFound, node, "text", expected, Mode.MATCHES);
            case ASSERT_VALUE_EQUALS   -> compareValue(elementFound, node, expected);
            case ASSERT_ATTRIBUTE      -> compareAttribute(elementFound, node, expected);
            default -> Result.fail("unsupported assertion: " + action);
        };
    }

    private enum Mode { EQUALS, CONTAINS, MATCHES }

    private static Result requireBool(boolean elementFound, JsonNode node, String field, boolean expected) {
        if (!elementFound) return Result.fail("element not present (cannot read '" + field + "')");
        JsonNode v = node.get(field);
        boolean actual = v != null && !v.isNull() && v.asBoolean();
        return actual == expected ? Result.pass()
                : Result.fail("'" + field + "' is " + actual + ", expected " + expected);
    }

    private static Result compareText(boolean elementFound, JsonNode node, String field,
                                      String expected, Mode mode) {
        if (!elementFound) return Result.fail("element not present");
        JsonNode v = node.get(field);
        String actual = v == null || v.isNull() ? "" : v.asText();
        boolean ok = switch (mode) {
            case EQUALS   -> actual.equals(expected);
            case CONTAINS -> expected != null && actual.contains(expected);
            case MATCHES  -> safeMatches(actual, expected);
        };
        return ok ? Result.pass()
                : Result.fail("text " + mode.name().toLowerCase() + " failed: actual=\""
                        + truncate(actual) + "\", expected=\"" + truncate(expected) + "\"");
    }

    private static Result compareValue(boolean elementFound, JsonNode node, String expected) {
        if (!elementFound) return Result.fail("element not present");
        // Android: EditText current value comes through the `text` attribute too — there
        // is no separate `value` field in AccessibilityNodeInfo, so we treat them the same.
        return compareText(true, node, "text", expected, Mode.EQUALS);
    }

    private static Result compareAttribute(boolean elementFound, JsonNode node, String spec) {
        if (!elementFound) return Result.fail("element not present");
        if (spec == null || !spec.contains("=")) return Result.fail("literalValue not in 'key=value' form");
        int eq = spec.indexOf('=');
        String key = spec.substring(0, eq).trim();
        String expected = spec.substring(eq + 1).trim();
        JsonNode v = node.get(key);
        String actual = v == null || v.isNull() ? "" : v.asText();
        return actual.equals(expected)
                ? Result.pass()
                : Result.fail("attribute '" + key + "' is '" + truncate(actual) + "', expected '" + truncate(expected) + "'");
    }

    private static boolean safeMatches(String actual, String regex) {
        try { return Pattern.matches(regex, actual); }
        catch (PatternSyntaxException e) { return false; }
    }

    private static boolean hasNonZeroBounds(int[] b) {
        return b != null && b.length == 4 && (b[2] - b[0]) > 0 && (b[3] - b[1]) > 0;
    }

    private static String truncate(String s) {
        if (s == null) return "null";
        return s.length() > 80 ? s.substring(0, 77) + "…" : s;
    }
}
