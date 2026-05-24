package com.qaplatform.web.automation.domain;

/**
 * Locator strategies surfaced in the UI when a user defines an
 * {@link WebElementEntity}. Each value is passed verbatim to
 * Playwright's {@code page.locator(...)}, which already handles the
 * full CSS / XPath / role / text / test-id syntax — see Playwright docs
 * "Locators". The enum just gates the UI dropdown to a curated set;
 * the backend doesn't transform the value.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code CSS}     → {@code "button.primary"}, {@code "[data-cy=login]"}</li>
 *   <li>{@code XPATH}   → {@code "//button[@aria-label='Login']"}</li>
 *   <li>{@code ROLE}    → {@code "role=button[name='Login']"}</li>
 *   <li>{@code TEXT}    → {@code "text=Sign in"} (case-insensitive substring)</li>
 *   <li>{@code TEST_ID} → {@code "[data-testid=login-btn]"} (also via Playwright's
 *       configurable {@code page.locator(byTestId(...))} attribute)</li>
 * </ul>
 */
public enum WebLocatorStrategy {
    CSS,
    XPATH,
    ROLE,
    TEXT,
    TEST_ID
}
