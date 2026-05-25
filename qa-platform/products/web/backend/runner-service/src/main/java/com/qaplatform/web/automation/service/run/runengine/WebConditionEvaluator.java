package com.qaplatform.web.automation.service.run.runengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.qaplatform.web.automation.domain.WebElementEntity;
import com.qaplatform.web.automation.domain.WebElementRepository;
import com.qaplatform.web.automation.domain.WebStepCondition;
import com.qaplatform.web.automation.domain.WebTestDataEntity;
import com.qaplatform.web.automation.domain.WebTestDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Evaluates the JSON predicate carried by an {@code IF} step against the
 * live Playwright {@link Page} (for element state) or the test-data
 * catalog (for value comparisons).
 *
 * <p>Returns a plain boolean; the orchestrator wraps any thrown exception
 * as a step ERROR so the run stops cleanly rather than crashing the
 * Playwright session.</p>
 */
@Component
public class WebConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(WebConditionEvaluator.class);
    /** Element-state probes get a tight timeout — we want a fast "is it
     *  there right now?" answer, not Playwright's default 30s wait. */
    private static final double PROBE_TIMEOUT_MS = 2_000d;

    private final WebElementRepository elements;
    private final WebTestDataRepository testData;
    private final ObjectMapper json;

    public WebConditionEvaluator(WebElementRepository elements,
                                 WebTestDataRepository testData,
                                 ObjectMapper json) {
        this.elements = elements;
        this.testData = testData;
        this.json = json;
    }

    /**
     * @param conditionJson raw JSON from {@code web_automation.steps.condition}
     * @param page          Playwright page bound to the current run
     * @param environment   active run environment (for test-data lookup precedence)
     */
    public boolean evaluate(String conditionJson, Page page, String environment) {
        WebStepCondition c;
        try { c = json.readValue(conditionJson, WebStepCondition.class); }
        catch (Exception e) { throw new IllegalArgumentException("malformed condition JSON: " + e.getMessage()); }
        if (c == null || c.type() == null || c.operator() == null) {
            throw new IllegalArgumentException("condition missing type or operator");
        }
        return switch (c.type()) {
            case "element_state"      -> evalElementState(c, page);
            case "test_data_compare"  -> evalTestDataCompare(c, environment);
            default -> throw new IllegalArgumentException("unknown condition type: " + c.type());
        };
    }

    /* ─────────────────────  element_state  ───────────────────────────── */

    private boolean evalElementState(WebStepCondition c, Page page) {
        WebElementEntity el = elements.findById(c.subjectId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "condition references missing element id=" + c.subjectId()));
        Locator loc = page.locator(el.getPrimaryValue());
        return switch (c.operator()) {
            case "is_visible"  -> loc.isVisible(new Locator.IsVisibleOptions().setTimeout(PROBE_TIMEOUT_MS));
            case "is_hidden"   -> !loc.isVisible(new Locator.IsVisibleOptions().setTimeout(PROBE_TIMEOUT_MS));
            case "exists"      -> loc.count() > 0;
            case "text_contains" -> {
                String actual = safeText(loc);
                yield actual != null && c.value() != null && actual.contains(c.value());
            }
            case "text_equals" -> {
                String actual = safeText(loc);
                yield actual != null && actual.equals(c.value());
            }
            default -> throw new IllegalArgumentException(
                    "element_state: unsupported operator '" + c.operator() + "'");
        };
    }

    private static String safeText(Locator loc) {
        try { return loc.first().textContent(new Locator.TextContentOptions().setTimeout(PROBE_TIMEOUT_MS)); }
        catch (Exception e) { return null; }
    }

    /* ───────────────────  test_data_compare  ─────────────────────────── */

    private boolean evalTestDataCompare(WebStepCondition c, String environment) {
        WebTestDataEntity td = testData.findById(c.subjectId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "condition references missing test_data id=" + c.subjectId()));

        // Test data is environment-scoped: prefer the row matching the
        // current run's environment if one exists for this name; fall
        // back to the default-env row otherwise.
        String actual = td.getValue();
        if (environment != null && !environment.isBlank() && !environment.equals(td.getEnvironment())) {
            Optional<WebTestDataEntity> envSpecific = testData
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
            // Fall back to lexical compare if either side isn't numeric. Users
            // often store mixed-type values; a string-vs-string comparison
            // is at least deterministic.
            log.debug("greater_than/less_than fell back to lexical compare for '{}' vs '{}'", a, b);
            return (a == null ? "" : a).compareTo(b == null ? "" : b);
        }
    }
}
