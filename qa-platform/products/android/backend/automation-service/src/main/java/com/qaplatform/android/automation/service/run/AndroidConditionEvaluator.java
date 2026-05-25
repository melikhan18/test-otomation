package com.qaplatform.android.automation.service.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaplatform.android.automation.domain.ElementEntity;
import com.qaplatform.android.automation.domain.ElementRepository;
import com.qaplatform.android.automation.domain.StepAction;
import com.qaplatform.android.automation.domain.StepCondition;
import com.qaplatform.android.automation.domain.TestDataEntity;
import com.qaplatform.android.automation.domain.TestDataRepository;
// BridgeClient lives in the same package, no import needed
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Evaluates an IF step's predicate against the live device session (for
 * element state) or against the test-data catalog (for value comparisons).
 *
 * <p>Element-state evaluation reuses {@link LocatorResolver} + the same
 * UIAutomator inspect tree the orchestrator already pulls for every
 * regular step, and maps each {@link StepCondition} operator onto an
 * equivalent {@link StepAction} assertion so {@link AssertionRunner}
 * does the actual semantic work. That keeps "is_visible" identical to
 * {@code ASSERT_VISIBLE} — same probe, same edge cases, same answer.</p>
 *
 * <p>Test-data comparisons honour the run's environment override the
 * same way StepRunner does — env-specific row wins; falls back to the
 * stored row if no env match.</p>
 */
public class AndroidConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AndroidConditionEvaluator.class);

    private final BridgeClient bridge;
    private final ElementRepository elements;
    private final TestDataRepository testData;
    private final ObjectMapper json;
    private final Long sessionId;
    private final String sessionToken;
    private final String environment;

    public AndroidConditionEvaluator(BridgeClient bridge,
                                     ElementRepository elements,
                                     TestDataRepository testData,
                                     ObjectMapper json,
                                     Long sessionId,
                                     String sessionToken,
                                     String environment) {
        this.bridge = bridge;
        this.elements = elements;
        this.testData = testData;
        this.json = json;
        this.sessionId = sessionId;
        this.sessionToken = sessionToken;
        this.environment = environment;
    }

    /**
     * @param conditionJson raw JSON from {@code android_automation.steps.condition}
     * @throws IllegalArgumentException for malformed JSON or unsupported operator
     */
    public boolean evaluate(String conditionJson) {
        StepCondition c;
        try { c = json.readValue(conditionJson, StepCondition.class); }
        catch (Exception e) { throw new IllegalArgumentException("malformed condition JSON: " + e.getMessage()); }
        if (c == null || c.type() == null || c.operator() == null) {
            throw new IllegalArgumentException("condition missing type or operator");
        }
        return switch (c.type()) {
            case "element_state"     -> evalElementState(c);
            case "test_data_compare" -> evalTestDataCompare(c);
            default -> throw new IllegalArgumentException("unknown condition type: " + c.type());
        };
    }

    /* ─────────────────────  element_state  ───────────────────────────── */

    private boolean evalElementState(StepCondition c) {
        ElementEntity el = elements.findById(c.subjectId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "condition references missing element id=" + c.subjectId()));

        JsonNode tree = bridge.inspect(sessionId, sessionToken, 8);
        LocatorResolver.Hit hit = LocatorResolver.resolve(tree, el);
        JsonNode node = hit != null ? hit.node() : null;

        // Map condition operator → assertion action. The assertion runner
        // already encodes Android's idea of "visible" (non-zero bounds),
        // "exists" (resolverHit not null), text matching, etc.
        StepAction proxy = switch (c.operator()) {
            case "is_visible"    -> StepAction.ASSERT_VISIBLE;
            case "is_hidden"     -> StepAction.ASSERT_NOT_VISIBLE;
            case "exists"        -> StepAction.ASSERT_VISIBLE;   // see below — special handling
            case "text_contains" -> StepAction.ASSERT_TEXT_CONTAINS;
            case "text_equals"   -> StepAction.ASSERT_TEXT_EQUALS;
            default -> throw new IllegalArgumentException(
                    "element_state: unsupported operator '" + c.operator() + "'");
        };

        // "exists" semantics = node is present in the tree at all, regardless
        // of visibility. AssertionRunner doesn't have a direct opposite of
        // ASSERT_NOT_PRESENT, so we check resolver hit directly.
        if ("exists".equals(c.operator())) return hit != null;

        AssertionRunner.Result r = AssertionRunner.check(hit, node, proxy, c.value());
        return r.ok();
    }

    /* ───────────────────  test_data_compare  ─────────────────────────── */

    private boolean evalTestDataCompare(StepCondition c) {
        TestDataEntity td = testData.findById(c.subjectId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "condition references missing test_data id=" + c.subjectId()));

        // Env-aware lookup (same precedence StepRunner uses for value
        // resolution): prefer current-env row, fall back to stored row.
        String actual = td.getValue();
        if (environment != null && !environment.isBlank() && !environment.equals(td.getEnvironment())) {
            Optional<TestDataEntity> envSpecific = testData
                    .findByProjectIdAndNameAndEnvironment(td.getProjectId(), td.getName(), environment);
            if (envSpecific.isPresent()) actual = envSpecific.get().getValue();
        }

        return switch (c.operator()) {
            case "equals"        -> equalsSafe(actual, c.value());
            case "not_equals"    -> !equalsSafe(actual, c.value());
            case "contains"      -> actual != null && c.value() != null && actual.contains(c.value());
            case "greater_than"  -> compareNumeric(actual, c.value()) > 0;
            case "less_than"     -> compareNumeric(actual, c.value()) < 0;
            default -> throw new IllegalArgumentException(
                    "test_data_compare: unsupported operator '" + c.operator() + "'");
        };
    }

    private static boolean equalsSafe(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    private static int compareNumeric(String a, String b) {
        try { return Double.compare(Double.parseDouble(a), Double.parseDouble(b)); }
        catch (NumberFormatException nfe) {
            log.debug("greater_than/less_than fell back to lexical compare for '{}' vs '{}'", a, b);
            return (a == null ? "" : a).compareTo(b == null ? "" : b);
        }
    }
}
