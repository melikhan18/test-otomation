package com.qaplatform.web.automation.service;

import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.web.automation.api.dto.WebTestDataDtos;
import com.qaplatform.web.automation.domain.WebTestDataEntity;
import com.qaplatform.web.automation.domain.WebTestDataRepository;
import com.qaplatform.web.automation.tenancy.ProjectContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WebTestDataService {

    private static final String MASKED = "••••••••";

    private final WebTestDataRepository repo;

    public WebTestDataService(WebTestDataRepository repo) { this.repo = repo; }

    @Transactional
    public WebTestDataDtos.View create(JwtPrincipal caller, ProjectContext ctx, WebTestDataDtos.CreateRequest req) {
        if (repo.existsByProjectIdAndNameAndEnvironment(ctx.projectId(), req.name(), req.environment())) {
            throw ApiException.conflict("test data with this name + environment already exists");
        }
        WebTestDataEntity e = new WebTestDataEntity(
                ctx.projectId(), req.name(), req.environment(), req.value(), caller.userId());
        e.setDescription(req.description());
        e.setSensitive(req.sensitive());
        return toView(repo.save(e), false);
    }

    @Transactional
    public WebTestDataDtos.View update(JwtPrincipal caller, ProjectContext ctx, long id, WebTestDataDtos.UpdateRequest req) {
        WebTestDataEntity e = ensureInProject(ctx, id);
        boolean keyChanged = !e.getName().equals(req.name()) || !e.getEnvironment().equals(req.environment());
        if (keyChanged && repo.existsByProjectIdAndNameAndEnvironment(ctx.projectId(), req.name(), req.environment())) {
            throw ApiException.conflict("another test data with this name + environment already exists");
        }
        e.setName(req.name());
        e.setEnvironment(req.environment());
        e.setValue(req.value());
        e.setDescription(req.description());
        e.setSensitive(req.sensitive());
        return toView(e, false);
    }

    @Transactional
    public void delete(JwtPrincipal caller, ProjectContext ctx, long id) {
        WebTestDataEntity e = ensureInProject(ctx, id);
        repo.delete(e);
    }

    @Transactional(readOnly = true)
    public List<WebTestDataDtos.View> list(JwtPrincipal caller, ProjectContext ctx) {
        return repo.findAllByProjectIdOrderByUpdatedAtDesc(ctx.projectId()).stream()
                .map(e -> toView(e, false)).toList();
    }

    @Transactional(readOnly = true)
    public WebTestDataDtos.View get(JwtPrincipal caller, ProjectContext ctx, long id, boolean reveal) {
        return toView(ensureInProject(ctx, id), reveal);
    }

    /** Distinct environment list for the run dialog dropdown. */
    @Transactional(readOnly = true)
    public List<String> environments(JwtPrincipal caller, ProjectContext ctx) {
        return repo.findAllByProjectIdOrderByUpdatedAtDesc(ctx.projectId()).stream()
                .map(WebTestDataEntity::getEnvironment).distinct().sorted().toList();
    }

    private WebTestDataEntity ensureInProject(ProjectContext ctx, long id) {
        WebTestDataEntity e = repo.findById(id).orElseThrow(() -> ApiException.notFound("test data"));
        if (!ctx.projectId().equals(e.getProjectId())) throw ApiException.forbidden("test data not in active project");
        return e;
    }

    private WebTestDataDtos.View toView(WebTestDataEntity e, boolean revealSensitive) {
        boolean mask = e.isSensitive() && !revealSensitive;
        return new WebTestDataDtos.View(
                e.getId(), e.getName(), e.getEnvironment(),
                mask ? MASKED : e.getValue(), e.getDescription(), e.isSensitive(), mask,
                e.getCreatedByUserId(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
