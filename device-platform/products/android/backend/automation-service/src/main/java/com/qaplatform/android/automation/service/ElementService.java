package com.qaplatform.android.automation.service;

import com.qaplatform.android.automation.api.dto.ElementDtos;
import com.qaplatform.android.automation.domain.ElementEntity;
import com.qaplatform.android.automation.domain.ElementRepository;
import com.qaplatform.android.automation.tenancy.ProjectContext;
import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ElementService {

    private final ElementRepository repo;

    public ElementService(ElementRepository repo) { this.repo = repo; }

    @Transactional
    public ElementDtos.View create(JwtPrincipal caller, ProjectContext ctx, ElementDtos.CreateRequest req) {
        if (repo.existsByProjectIdAndName(ctx.projectId(), req.name())) {
            throw ApiException.conflict("element name already exists in this project");
        }
        ElementEntity e = new ElementEntity(ctx.legacyProductId(), ctx.projectId(),
                req.name(), req.primaryStrategy(), req.primaryValue(), caller.userId());
        e.setDescription(req.description());
        e.setFallbackLocatorsJson(LocatorJson.write(req.fallbackLocators()));
        e.setScreenshotData(req.screenshotData());
        e.setSampleBounds(req.sampleBounds());
        e.setSampleClass(req.sampleClass());
        e.setSampleText(req.sampleText());
        e.setSampleResourceId(req.sampleResourceId());
        return toView(repo.save(e));
    }

    @Transactional
    public ElementDtos.View update(JwtPrincipal caller, ProjectContext ctx, long id, ElementDtos.UpdateRequest req) {
        ElementEntity e = ensureInProject(ctx, id);
        if (!e.getName().equals(req.name())
                && repo.existsByProjectIdAndName(ctx.projectId(), req.name())) {
            throw ApiException.conflict("element name already exists in this project");
        }
        e.setName(req.name());
        e.setDescription(req.description());
        e.setPrimaryStrategy(req.primaryStrategy());
        e.setPrimaryValue(req.primaryValue());
        e.setFallbackLocatorsJson(LocatorJson.write(req.fallbackLocators()));
        return toView(e);
    }

    @Transactional
    public void delete(JwtPrincipal caller, ProjectContext ctx, long id) {
        repo.delete(ensureInProject(ctx, id));
    }

    @Transactional(readOnly = true)
    public ElementDtos.View get(JwtPrincipal caller, ProjectContext ctx, long id) {
        return toView(ensureInProject(ctx, id));
    }

    @Transactional(readOnly = true)
    public List<ElementDtos.View> list(JwtPrincipal caller, ProjectContext ctx, String q) {
        List<ElementEntity> raw = (q == null || q.isBlank())
                ? repo.findAllByProjectIdOrderByNameAsc(ctx.projectId())
                : repo.searchByProjectId(ctx.projectId(), q.trim());
        return raw.stream().map(this::toView).toList();
    }

    private ElementEntity ensureInProject(ProjectContext ctx, long id) {
        ElementEntity e = repo.findById(id).orElseThrow(() -> ApiException.notFound("element"));
        if (!ctx.projectId().equals(e.getProjectId())) throw ApiException.forbidden("element not in active project");
        return e;
    }

    private ElementDtos.View toView(ElementEntity e) {
        return new ElementDtos.View(
                e.getId(), e.getProductId(), e.getName(), e.getDescription(),
                e.getPrimaryStrategy(), e.getPrimaryValue(),
                LocatorJson.read(e.getFallbackLocatorsJson()),
                e.getScreenshotData(), e.getSampleBounds(),
                e.getSampleClass(), e.getSampleText(), e.getSampleResourceId(),
                e.getCreatedByUserId(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
