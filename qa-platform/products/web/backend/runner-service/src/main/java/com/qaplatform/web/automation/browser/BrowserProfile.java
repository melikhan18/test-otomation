package com.qaplatform.web.automation.browser;

/**
 * Immutable browser configuration. The web stack's analog of an Android
 * {@code Device} row — but config-as-data, no enrollment, no lifecycle.
 *
 * <p>One profile = one (engine × viewport × DPR × user-agent × locale)
 * tuple the orchestrator hands to Playwright at run start. Users pick a
 * profile by {@link #id()} when triggering a run; new profiles get added
 * by editing {@code browser-profiles.json} (config reload — no
 * migration, no UI flow).</p>
 *
 * @param id                 stable slug; runs.browser_profile_id stores this
 * @param displayName        human-readable label for the picker UI
 * @param engine             "chromium" | "firefox" | "webkit"
 * @param width              viewport width (CSS pixels)
 * @param height             viewport height (CSS pixels)
 * @param deviceScaleFactor  DPR — mobile profiles usually 2.0 / 2.625 / 3.0
 * @param isMobile           toggles Playwright's touch + mobile UA emulation
 * @param userAgent          override; null = browser default
 * @param locale             passed to BrowserContext (Accept-Language + JS Intl)
 * @param timezone           passed to BrowserContext (JS Date/Intl)
 */
public record BrowserProfile(
        String id,
        String displayName,
        String engine,
        int width,
        int height,
        double deviceScaleFactor,
        boolean isMobile,
        String userAgent,
        String locale,
        String timezone
) {}
