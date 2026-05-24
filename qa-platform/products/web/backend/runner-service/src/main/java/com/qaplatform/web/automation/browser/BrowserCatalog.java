package com.qaplatform.web.automation.browser;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Static catalog of available {@link BrowserProfile}s — the web stack's
 * "device list". Loaded once at startup from
 * {@code classpath:browser-profiles.json}.
 *
 * <p>Lookup-by-id is O(1) via {@link #find(String)}; iteration order matches
 * the JSON file's order so the UI picker renders deterministically. Adding
 * a new profile is a one-line JSON edit + service restart — no migration,
 * no DB row.</p>
 */
@Component
public class BrowserCatalog {

    private static final Logger log = LoggerFactory.getLogger(BrowserCatalog.class);

    private final ObjectMapper json;
    private Map<String, BrowserProfile> byId = Collections.emptyMap();
    private List<BrowserProfile> ordered = List.of();

    public BrowserCatalog(ObjectMapper json) {
        this.json = json;
    }

    @PostConstruct
    void load() throws Exception {
        try (InputStream in = new ClassPathResource("browser-profiles.json").getInputStream()) {
            BrowserProfile[] arr = json.readValue(in, BrowserProfile[].class);
            Map<String, BrowserProfile> idx = new LinkedHashMap<>();
            for (BrowserProfile p : arr) idx.put(p.id(), p);
            this.byId = Collections.unmodifiableMap(idx);
            this.ordered = List.copyOf(idx.values());
            log.info("Loaded {} browser profiles: {}", ordered.size(),
                    ordered.stream().map(BrowserProfile::id).toList());
        }
    }

    public List<BrowserProfile> all() { return ordered; }

    public Optional<BrowserProfile> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }
}
