package com.qaplatform.shared.reports.api;

import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.shared.reports.api.dto.ReportsDtos;
import com.qaplatform.shared.reports.service.ReportsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/reports")
public class ReportsController {

    private final ReportsService service;

    public ReportsController(ReportsService service) {
        this.service = service;
    }

    /**
     * Push a terminal-state run summary. Idempotent on (platform, sourceRunId).
     * Returns 201 on first ingest, 200 on subsequent overwrites — but for the
     * F7 skeleton we just return 201 either way; the body is the canonical
     * view either way.
     */
    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportsDtos.RunSummaryView push(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestBody @Valid ReportsDtos.PushRunSummary req) {
        return service.push(caller, req);
    }

    @GetMapping("/runs")
    public ReportsDtos.RunSummaryList list(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestParam long projectId,
            @RequestParam(defaultValue = "50") int limit) {
        return service.list(caller, projectId, limit);
    }

    /**
     * Aggregate rollup for the dashboard. {@code daysBack} defaults to 7
     * if not provided — gives a "last week at a glance" view. Clients that
     * need a different window pass a different value (max 365 enforced here
     * to keep the query bounded; deeper history will land when partitioning
     * is in place).
     */
    @GetMapping("/summary")
    public ReportsDtos.PlatformStatusSummary summary(
            @AuthenticationPrincipal JwtPrincipal caller,
            @RequestParam long projectId,
            @RequestParam(defaultValue = "7") int daysBack) {
        int capped = Math.max(1, Math.min(daysBack, 365));
        Instant since = Instant.now().minus(capped, ChronoUnit.DAYS);
        return service.summary(caller, projectId, since);
    }
}
