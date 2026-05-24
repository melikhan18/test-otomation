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
import com.qaplatform.web.automation.domain.WebStepEntity;
import com.qaplatform.web.automation.domain.WebStepRepository;
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

    public WebStepExecutor(WebStepRepository steps) {
        this.steps = steps;
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
            try {
                return dispatch(page, entity, timeout);
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

    /* ──────────────────────────── dispatch ─────────────────────────────── */

    private StepOutcome dispatch(Page page, WebStepEntity step, int timeoutMs) {
        return switch (step.getAction()) {
            // ── Navigation ───────────────────────────────────────────────
            case GOTO         -> { page.navigate(req("value", step.getValue())); yield StepOutcome.passed(step.getValue()); }
            case RELOAD       -> { page.reload(); yield StepOutcome.passed(null); }
            case GO_BACK      -> { page.goBack(); yield StepOutcome.passed(null); }
            case GO_FORWARD   -> { page.goForward(); yield StepOutcome.passed(null); }

            // ── Interaction ──────────────────────────────────────────────
            case CLICK -> {
                locator(page, step).click(new Locator.ClickOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(step.getSelector());
            }
            case DBL_CLICK -> {
                locator(page, step).dblclick(new Locator.DblclickOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(step.getSelector());
            }
            case FILL -> {
                locator(page, step).fill(req("value", step.getValue()),
                        new Locator.FillOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(step.getSelector());
            }
            case PRESS_KEY -> {
                // selector optional — if provided, key goes to that element;
                // otherwise to the active document focus via page.keyboard.
                String key = req("value", step.getValue());
                if (step.getSelector() != null && !step.getSelector().isBlank()) {
                    locator(page, step).press(key, new Locator.PressOptions().setTimeout(timeoutMs));
                } else {
                    page.keyboard().press(key);
                }
                yield StepOutcome.passed(step.getSelector() != null ? step.getSelector() : "<page>");
            }
            case CHECK -> {
                locator(page, step).check(new Locator.CheckOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(step.getSelector());
            }
            case UNCHECK -> {
                locator(page, step).uncheck(new Locator.UncheckOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(step.getSelector());
            }
            case SELECT -> {
                locator(page, step).selectOption(req("value", step.getValue()),
                        new Locator.SelectOptionOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(step.getSelector());
            }
            case HOVER -> {
                locator(page, step).hover(new Locator.HoverOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(step.getSelector());
            }

            // ── Wait ─────────────────────────────────────────────────────
            case WAIT_FOR_SELECTOR -> {
                locator(page, step).waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(step.getSelector());
            }
            case WAIT_FOR_LOAD_STATE -> {
                page.waitForLoadState(parseLoadState(step.getValue()));
                yield StepOutcome.passed(step.getValue());
            }
            case SLEEP -> {
                long ms = parseLongOr(step.getValue(), 1000L);
                page.waitForTimeout(ms);
                yield StepOutcome.passed("sleep " + ms + "ms");
            }

            // ── Assert ───────────────────────────────────────────────────
            case ASSERT_VISIBLE -> {
                assertThat(locator(page, step)).isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(step.getSelector());
            }
            case ASSERT_HIDDEN -> {
                assertThat(locator(page, step)).isHidden(new LocatorAssertions.IsHiddenOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(step.getSelector());
            }
            case ASSERT_TEXT_EQUALS -> {
                assertThat(locator(page, step)).hasText(req("value", step.getValue()),
                        new LocatorAssertions.HasTextOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(step.getSelector());
            }
            case ASSERT_TEXT_CONTAINS -> {
                assertThat(locator(page, step)).containsText(req("value", step.getValue()),
                        new LocatorAssertions.ContainsTextOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(step.getSelector());
            }
            case ASSERT_URL_EQUALS -> {
                assertThat(page).hasURL(req("value", step.getValue()),
                        new PageAssertions.HasURLOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(null);
            }
            case ASSERT_URL_CONTAINS -> {
                String urlPart = req("value", step.getValue());
                if (!page.url().contains(urlPart)) {
                    yield StepOutcome.failed("url '" + page.url() + "' does not contain '" + urlPart + "'");
                }
                yield StepOutcome.passed(null);
            }
            case ASSERT_TITLE_EQUALS -> {
                assertThat(page).hasTitle(req("value", step.getValue()),
                        new PageAssertions.HasTitleOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(null);
            }
            case ASSERT_TITLE_CONTAINS -> {
                String titlePart = req("value", step.getValue());
                String title = page.title();
                if (!title.contains(titlePart)) {
                    yield StepOutcome.failed("title '" + title + "' does not contain '" + titlePart + "'");
                }
                yield StepOutcome.passed(null);
            }
            case ASSERT_ATTRIBUTE -> {
                // value format: attrName=expectedValue
                String spec = req("value", step.getValue());
                int eq = spec.indexOf('=');
                if (eq <= 0) yield StepOutcome.failed("ASSERT_ATTRIBUTE value must be name=value");
                String attr = spec.substring(0, eq);
                String want = spec.substring(eq + 1);
                assertThat(locator(page, step)).hasAttribute(attr, want,
                        new LocatorAssertions.HasAttributeOptions().setTimeout(timeoutMs));
                yield StepOutcome.passed(step.getSelector());
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
                log.info("step {} comment: {}", step.getId(), step.getValue() == null ? "" : step.getValue());
                yield StepOutcome.passed(null);
            }
            case EVAL_JS -> {
                page.evaluate(req("value", step.getValue()));
                yield StepOutcome.passed(null);
            }
        };
    }

    /* ───────────────────────── helpers ─────────────────────────────────── */

    private static Locator locator(Page page, WebStepEntity step) {
        String sel = step.getSelector();
        if (sel == null || sel.isBlank()) {
            throw new IllegalArgumentException("step " + step.getId() + " (" + step.getAction() +
                    ") requires a selector");
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
