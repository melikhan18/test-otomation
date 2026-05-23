package com.qaplatform.shared.reports;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reports aggregator — the cross-platform run roll-up.
 *
 * <h2>Scope (F7 skeleton)</h2>
 *
 * <p>Each platform stack (Android today, iOS / Backend / Web later) pushes a
 * single summary row per terminal run to this service. Dashboards read from
 * here so a "last 7 days of failures across all stacks" query is one table
 * scan instead of a federated join across N platform schemas.</p>
 *
 * <p>F7 ships the ingest endpoint, the storage shape, and the read surface
 * the dashboard needs. Subscriber / event-bus pull will follow in a later
 * faz; for now Android's {@code RunOrchestrator} fires a fire-and-forget
 * HTTP POST when a run reaches a terminal state.</p>
 */
@SpringBootApplication
public class ReportsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReportsApplication.class, args);
    }
}
