package com.qaplatform.web.automation.api;

import com.qaplatform.web.automation.browser.BrowserCatalog;
import com.qaplatform.web.automation.browser.BrowserProfile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exposes the static browser-profile catalog so the run dialog can render
 * a picker without hard-coding profile ids on the frontend. Read-only;
 * editing the catalog means editing {@code browser-profiles.json} and
 * restarting the service.
 */
@RestController
@RequestMapping("/api/web/browsers")
public class BrowserController {

    private final BrowserCatalog catalog;

    public BrowserController(BrowserCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<BrowserProfile> list() {
        return catalog.all();
    }
}
