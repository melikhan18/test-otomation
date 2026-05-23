package com.qaplatform.shared.reports.domain;

import com.qaplatform.common.runengine.status.RunStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RunSummaryRepository extends JpaRepository<RunSummaryEntity, Long> {

    Optional<RunSummaryEntity> findByPlatformAndSourceRunId(String platform, Long sourceRunId);

    List<RunSummaryEntity> findAllByProjectIdOrderByFinishedAtDescIdDesc(Long projectId, Pageable page);

    /**
     * Cross-platform rollup: counts grouped by (platform, status) for one
     * project since a cutoff. Used by the dashboard "platform breakdown"
     * widget.
     */
    @Query("""
        SELECT r.platform AS platform, r.status AS status, COUNT(r) AS count
        FROM RunSummaryEntity r
        WHERE r.projectId = :projectId
          AND (r.finishedAt IS NULL OR r.finishedAt >= :since)
        GROUP BY r.platform, r.status
        """)
    List<PlatformStatusCount> aggregateByPlatformAndStatus(Long projectId, Instant since);

    interface PlatformStatusCount {
        String getPlatform();
        RunStatus getStatus();
        long getCount();
    }
}
