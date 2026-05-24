package com.qaplatform.web.automation.service.run.runengine;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.assertions.PageAssertions;
import com.microsoft.playwright.options.LoadState;
import com.qaplatform.common.runengine.spi.RunStep;
import com.qaplatform.common.runengine.spi.StepContext;
import com.qaplatform.common.runengine.spi.StepExecutor;
import com.qaplatform.common.runengine.spi.StepOutcome;
import com.qaplatform.web.automation.domain.WebStepAction;
import com.qaplatform.web.automation.domain.WebElementEntity;
import com.qaplatform.web.automation.domain.WebElementRepository;
import com.qaplatform.web.automation.domain.WebStepEntity;
import com.qaplatform.web.automation.domain.WebStepRepository;
import com.qaplatform.web.automation.domain.WebTestDataEntity;
import com.qaplatform.web.automation.domain.WebTestDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Web platform's bridge to the F6 {@link StepExecutor} SPI — the analog of
 * Android's {@code AndroidStepExecutor}. Bean is stateless; the per-run
 * {@link Page} is bound through {@link #forRun(Page, int)} which returns a
 * single-run {@code StepExecutor} lambda.
 *
 * <p>Each {@code execute(...)} call:</p>
 * <ol>
 *   <li>Re-loads the {@link WebStepEntity} by id from the DB (the
 *       {@link RunStep} adapter only carries the id + action + payload).</li>
 *   <li>Dispatches on {@link WebStepAction} to the matching Playwright
 *       primitive — {@code page.navigate(url)}, {@code page.locator(sel).click()},
 *       {@code assertThat(locator).isVisible()}, …</li>
 *   <li>Wraps Playwright's exceptions: assertion misses become FAILED with the
 *       Playwright message; everything else becomes ERROR. We never propagate
 *       exceptions upward — the orchestrator's loop relies on the StepOutcome
 *       contract for terminal-state accounting.</li>
 * </ol>
 *
 * <p>Step DSL v1 covers the navigation + interaction + wait + assert primitives
 * most scenarios need; future actions (network intercept, multi-tab, file
 * upload, drag) will be added without changing the SPI surface.</p>
 */
@Component
public class WebStepExecutor {

    private static final Logger log = LoggerFactory.getLogger(WebStepExecutor.class);

    private final WebStepRepository steps;
    private final WebElementRepository elements;
    private final WebTestDataRepository testData;

    public WebStepExecutor(WebStepRepository steps,
                           WebElementRepository elements,
                           WebTestDataRepository testData) {
        this.steps = steps;
        this.elements = elements;
        this.testData = testData;
    }

    /**
     * @param page              Playwright {@link Page} bound for the duration of one run
     * @param defaultTimeoutMs  step-level timeout fallback (used when the entity's
     *                          {@code timeoutMs} is unspecified)
     */
    public StepExecutor forRun(Page page, int defaultTimeoutMs) {
        return (runStep, ctx) -> {
            WebStepEntity entity = steps.findById(runStep.id()).orElse(null);
            if (entity == null) {
                ctx.log().warn("step " + runStep.id() + " not found in DB; aborting");
                return StepOutcome.error("step " + runStep.id() + " not found");
            }
            int timeout = entity.getTimeoutMs() > 0 ? entity.getTimeoutMs() : defaultTimeoutMs;
            // Catalog resolution happens once per step — fixed inputs into the
            // dispatch table. If catalog refs are unset, falls back to the
            // step's literal selector / value (back-compat with v1 rows).
            ResolvedStep resolved;
            try { resolved = resolve(entity, ctx.environment()); }
            catch (RuntimeException re) { return StepOutcome.failed(re.getMessage()); }
            try {
                return dispatch(page, entity, resolved, timeout);
            } catch (com.microsoft.playwright.PlaywrightException pe) {
                // Playwright distinguishes assertion failures (TimeoutError-shaped)
                // from other errors only via message inspection. We treat
                // everything thrown from inside an action as FAILED — the loop
                // already captures a screenshot, that's the diagnostic the user
                // needs. ERROR is reserved for executor-internal bugs (above).
                return StepOutcome.failed(pe.getMessage());
            } catch (Exception e) {
                return StepOutcome.error(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        };
    }

    /**
     * The pair of resolved values the dispatcher uses for one step. {@code selector}
     * is the locator string Playwright sees; {@code value} is the literal /
     * substituted argument (URL, text-to-type, expected match, …).
     */
    private record ResolvedStep(String selector, String value) {}

    private ResolvedStep resolve(WebStepEntity step, String environment) {
        // Selector: prefer catalog ref over literal. If the element row is gone
        // (FK got nulled), fall back to literal — surface "step requires selector"
        // downstream if both are absent.
        String selector = step.getSelector();
        if (step.getTargetElementId() != null) {
            WebElementEntity el = elements.findById(step.getTargetElementId()).orElse(null);
            if (el != null) selector = el.getPrimaryValue();
        }

        // Value: prefer catalog ref + env override. test_data has (project,
        // name, environment) — first try (env-specific), fall back to the
        // row pointed at by data_id (which is one env's row, may not be the
        // run's env — that's fine).
        String value = step.getValue();
        if (step.getDataId() != null) {
            WebTestDataEntity td = testData.findById(step.getDataId()).orElse(null);
            if (td != null) {
                value = td.getValue();
                if (environment != null && !environment.equals(td.getEnvironment())) {
                    testData.findByProjectIdAndNameAndEnvironment(td.getProjectId(), td.getName(), environment)
                            .ifPresent(envSpecific -> { /* shadow local */ });
                    var byEnv = testData.findByProjectIdAndNameAndEnvironment(
                            td.getProjectId(), td.getName(), environment);
                    if (byEnv.isPresent()) value = byEnv.get().getValue();
                }
            }
        }
        return new ResolvedStep(selector, value);
    }

    /* ──────────────────────────── dispatch ─────────────────────────────── */

    private StepOutcome dispatch(Page page, WebStepEntity step, ResolvedStep r, int timeoutMs) {
        return switch (step.getAction()) {
            // ── Navigation ───────────────────────────────────────────────
            case GOTO         -> { page.navigate(req("value", r.value())); yield StepOutcome.passed(r.value()); }
            case RELOAD       -> { page.reload(); yield StepOutcome.passed(null); }
            case GO_BACK      -> { page.goBack(); yield StepOutcome.passed(null); }
            case GO_FORWARD   -> { page.goForward(); yield StepOutcome.passed(null); }

            // ── Interaction ──────────────────────────────────────────────
            case CLICK -> {
                locator(page, step, r).click(new Locator.ClickOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(r.selector());
            }
            case DBL_CLICK -> {
                locator(page, step, r).dblclick(new Locator.DblclickOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(r.selector());
            }
            case FILL -> {
                locator(page, step, r).fill(req("value", r.value()),
                        new Locator.FillOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(r.selector());
            }
            case PRESS_KEY -> {
                // selector optional — if provided, key goes to that element;
                // otherwise to the active document focus via page.keyboard.
                String key = req("value", r.value());
                if (r.selector() != null && !r.selector().isBlank()) {
                    locator(page, step, r).press(key, new Locator.PressOptions().setTimeout(timeoutMs));
                } else {
                    page.keyboard().press(key);
                }
                yield StepOutcome.passed(r.selector() != null ? r.selector() : "<page>");
            }
            case CHECK -> {
                locator(page, step, r).check(new Locator.CheckOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(r.selector());
            }
            case UNCHECK -> {
                locator(page, step, r).uncheck(new Locator.UncheckOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(r.selector());
            }
            case SELECT -> {
                locator(page, step, r).selectOption(req("value", r.value()),
                        new Locator.SelectOptionOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(r.selector());
            }
            case HOVER -> {
                locator(page, step, r).hover(new Locator.HoverOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(r.selector());
            }

            // ── Wait ─────────────────────────────────────────────────────
            case WAIT_FOR_SELECTOR -> {
                locator(page, step, r).waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(r.selector());
            }
            case WAIT_FOR_LOAD_STATE -> {
                page.waitForLoadState(parseLoadState(r.value()));
                yield StepOutcome.passed(r.value());
            }
            case SLEEP -> {
                long ms = parseLongOr(r.value(), 1000L);
                page.waitForTimeout(ms);
                yield StepOutcome.passed("sleep " + ms + "ms");
            }

            // ── Assert ───────────────────────────────────────────────────
            case ASSERT_VISIBLE -> {
                assertThat(locator(page, step, r)).isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(r.selector());
            }
            case ASSERT_HIDDEN -> {
                assertThat(locator(page, step, r)).isHidden(new LocatorAssertions.IsHiddenOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(r.selector());
            }
            case ASSERT_TEXT_EQUALS -> {
                assertThat(locator(page, step, r)).hasText(req("value", r.value()),
                        new LocatorAssertions.HasTextOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(r.selector());
            }
            case ASSERT_TEXT_CONTAINS -> {
                assertThat(locator(page, step, r)).containsText(req("value", r.value()),
                        new LocatorAssertions.ContainsTextOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(r.selector());
            }
            case ASSERT_URL_EQUALS -> {
                assertThat(page).hasURL(req("value", r.value()),
                        new PageAssertions.HasURLOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(null);
            }
            case ASSERT_URL_CONTAINS -> {
                String urlPart = req("value", r.value());
                if (!page.url().contains(urlPart)) {
                    yield StepOutcome.failed("url '" + page.url() + "' does not contain '" + urlPart + "'");
                }
                yield StepOutcome.passed(null);
            }
            case ASSERT_TITLE_EQUALS -> {
                assertThat(page).hasTitle(req("value", r.value()),
                        new PageAssertions.HasTitleOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(null);
            }
            case ASSERT_TITLE_CONTAINS -> {
                String titlePart = req("value", r.value());
                String title = page.title();
                if (!title.contains(titlePart)) {
                    yield StepOutcome.failed("title '" + title + "' does not contain '" + titlePart + "'");
                }
                yield StepOutcome.passed(null);
            }
            case ASSERT_ATTRIBUTE -> {
                // value format: attrName=expectedValue
                String spec = req("value", r.value());
                int eq = spec.indexOf('=');
                if (eq <= 0) yield StepOutcome.failed("ASSERT_ATTRIBUTE value must be name=value");
                String attr = spec.substring(0, eq);
                String want = spec.substring(eq + 1);
                assertThat(locator(page, step, r)).hasAttribute(attr, want,
                        new LocatorAssertions.HasAttributeOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(r.selector());
            }

            // ── Util ─────────────────────────────────────────────────────
            case SCREENSHOT -> {
                // Orchestrator captures screenshots on FAILED/ERROR for the
                // report. This action is the explicit-checkpoint variant —
                // the orchestrator picks it up via screenshotAfter / a
                // later faz can wire byte[] into StepOutcome here.
                yield StepOutcome.passed(null);
            }
            case COMMENT -> {
                log.info("step {} comment: {}", step.getId(), r.value() == null ? "" : r.value());
                yield StepOutcome.passed(null);
            }
            case EVAL_JS -> {
                page.evaluate(req("value", r.value()));
                yield StepOutcome.passed(null);
            }
        };
    }

    /* ───────────────────────── helpers ─────────────────────────────────── */

    private static Locator locator(Page page, WebStepEntity step, ResolvedStep r) {
        String sel = r.selector();
        if (sel == null || sel.isBlank()) {
            throw new IllegalArgumentException("step " + step.getId() + " (" + step.getAction() +
                    ") requires a selector (literal or element catalog ref)");
        }
        return page.locator(sel);
    }

    private static String req(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("step requires non-empty '" + name + "'");
        }
        return value;
    }

    private static LoadState parseLoadState(String v) {
        return switch (v == null ? "load" : v.trim().toLowerCase()) {
            case "domcontentloaded" -> LoadState.DOMCONTENTLOADED;
            case "networkidle"      -> LoadState.NETWORKIDLE;
            default                  -> LoadState.LOAD;
        };
    }

    private static long parseLongOr(String s, long d) {
        if (s == null || s.isBlank()) return d;
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException e) { return d; }
    }
}
