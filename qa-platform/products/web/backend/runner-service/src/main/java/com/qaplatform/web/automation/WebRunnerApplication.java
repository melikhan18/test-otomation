package com.qaplatform.web.automation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Web platform's automation service — Playwright-based, server-side.
 *
 * <p>One process holds the Spring API, the Playwright Java client, and the
 * Node "driver" subprocess that talks to Chromium / Firefox / WebKit. Browser
 * instances spawn per-run inside this same container (the monolithic
 * deployment model — see Dockerfile + docker-compose entry).</p>
 *
 * <p>{@code @EnableAsync} is here so {@code WebReportsPublisher} can push run
 * summaries to {@code reports-aggregator-service} without blocking the
 * orchestrator's terminal-state path — same fire-and-forget pattern Android
 * uses.</p>
 */
@SpringBootApplication
@EnableAsync
public class WebRunnerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebRunnerApplication.class, args);
    }
}
