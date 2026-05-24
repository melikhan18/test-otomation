package com.qaplatform.web.automation.service;

import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.web.automation.api.dto.WebElementDtos;
import com.qaplatform.web.automation.domain.WebElementEntity;
import com.qaplatform.web.automation.domain.WebElementRepository;
import com.qaplatform.web.automation.tenancy.ProjectContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WebElementService {

    private final WebElementRepository repo;

    public WebElementService(WebElementRepository repo) { this.repo = repo; }

    @Transactional
    public WebElementDtos.View create(JwtPrincipal caller, ProjectContext ctx, WebElementDtos.CreateRequest req) {
        if (repo.existsByProjectIdAndName(ctx.projectId(), req.name())) {
            throw ApiException.conflict("element name already exists in this project");
        }
        WebElementEntity e = new WebElementEntity(
                ctx.projectId(), req.name(), req.primaryStrategy(), req.primaryValue(), caller.userId());
        e.setDescription(req.description());
        e.setFallbackLocatorsJson(WebLocatorJson.write(req.fallbackLocators()));
        return toView(repo.save(e));
    }

    @Transactional
    public WebElementDtos.View update(JwtPrincipal caller, ProjectContext ctx, long id, WebElementDtos.UpdateRequest req) {
        WebElementEntity e = ensureInProject(ctx, id);
        if (!e.getName().equals(req.name()) && repo.existsByProjectIdAndName(ctx.projectId(), req.name())) {
            throw ApiException.conflict("element name already exists in this project");
        }
        e.setName(req.name());
        e.setDescription(req.description());
        e.setPrimaryStrategy(req.primaryStrategy());
        e.setPrimaryValue(req.primaryValue());
        e.setFallbackLocatorsJson(WebLocatorJson.write(req.fallbackLocators()));
        return toView(e);
    }

    @Transactional
    public void delete(JwtPrincipal caller, ProjectContext ctx, long id) {
        WebElementEntity e = ensureInProject(ctx, id);
        repo.delete(e);  // steps' target_element_id nulled by FK ON DELETE SET NULL
    }

    @Transactional(readOnly = true)
    public List<WebElementDtos.View> list(JwtPrincipal caller, ProjectContext ctx) {
        return repo.findAllByProjectIdOrderByUpdatedAtDesc(ctx.projectId()).stream()
                .map(WebElementService::toView).toList();
    }

    @Transactional(readOnly = true)
    public WebElementDtos.View get(JwtPrincipal caller, ProjectContext ctx, long id) {
        return toView(ensureInProject(ctx, id));
    }

    private WebElementEntity ensureInProject(ProjectContext ctx, long id) {
        WebElementEntity e = repo.findById(id).orElseThrow(() -> ApiException.notFound("element"));
        if (!ctx.projectId().equals(e.getProjectId())) throw ApiException.forbidden("element not in active project");
        return e;
    }

    private static WebElementDtos.View toView(WebElementEntity e) {
        return new WebElementDtos.View(
                e.getId(), e.getName(), e.getDescription(),
                e.getPrimaryStrategy(), e.getPrimaryValue(),
                WebLocatorJson.read(e.getFallbackLocatorsJson()),
                e.getCreatedByUserId(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
