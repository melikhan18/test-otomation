package com.qaplatform.shared.tenant.service;

import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.shared.tenant.api.dto.ProjectPlatformDtos;
import com.qaplatform.shared.tenant.domain.ProjectPlatformEntity;
import com.qaplatform.shared.tenant.domain.ProjectPlatformRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages which platforms are activated on a given project.
 *
 * <p>Auth is purely "is the caller a platform-admin or company owner". Since
 * tenant-service doesn't yet own the company/project tables (auth-service still
 * does in F5), we can't cross-check membership locally — we trust the JWT's
 * platformAdmin flag for now and delegate finer-grained authorization to
 * auth-service via the gateway's tenant route (header-based). When the
 * tenancy migration completes (F-future) this will tighten.</p>
 */
@Service
public class ProjectPlatformService {

    private final ProjectPlatformRepository repo;

    public ProjectPlatformService(ProjectPlatformRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public ProjectPlatformDtos.ProjectPlatformsView list(JwtPrincipal caller, long projectId) {
        requireAuth(caller);
        List<ProjectPlatformDtos.PlatformView> rows =
                repo.findAllByProjectIdOrderByPlatformAsc(projectId).stream()
                        .map(p -> new ProjectPlatformDtos.PlatformView(
                                p.getPlatform(), p.getEnabledAt(), p.getEnabledBy()))
                        .toList();
        return new ProjectPlatformDtos.ProjectPlatformsView(projectId, rows);
    }

    @Transactional
    public ProjectPlatformDtos.PlatformView enable(JwtPrincipal caller, long projectId,
                                                   ProjectPlatformDtos.EnableRequest req) {
        requireAuth(caller);
        // Idempotent: re-enabling an already-enabled platform is not an error,
        // we just return the existing row. Saves the client from having to do
        // an exists check first.
        if (repo.existsByProjectIdAndPlatform(projectId, req.platform())) {
            return repo.findAllByProjectIdOrderByPlatformAsc(projectId).stream()
                    .filter(p -> p.getPlatform().equals(req.platform()))
                    .findFirst()
                    .map(p -> new ProjectPlatformDtos.PlatformView(p.getPlatform(), p.getEnabledAt(), p.getEnabledBy()))
                    .orElseThrow();  // can't happen given the existsBy check above
        }
        ProjectPlatformEntity saved = repo.save(new ProjectPlatformEntity(projectId, req.platform(), caller.userId()));
        return new ProjectPlatformDtos.PlatformView(saved.getPlatform(), saved.getEnabledAt(), saved.getEnabledBy());
    }

    @Transactional
    public void disable(JwtPrincipal caller, long projectId, String platform) {
        requireAuth(caller);
        long removed = repo.deleteByProjectIdAndPlatform(projectId, platform);
        if (removed == 0) throw ApiException.notFound("platform not enabled on this project");
    }

    /**
     * Coarse auth — only platform admins can mutate project platforms in F5.
     * Once tenant-service owns the project/company tables we'll switch to a
     * proper OWNER / QA_MANAGER check.
     */
    private static void requireAuth(JwtPrincipal caller) {
        if (caller == null || caller.userId() == null) throw ApiException.unauthorized("missing identity");
        if (!caller.platformAdmin()) throw ApiException.forbidden("platform admin required (F5 skeleton — finer check coming F-future)");
    }
}
