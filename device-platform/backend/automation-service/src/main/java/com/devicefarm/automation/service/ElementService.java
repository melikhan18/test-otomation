package com.devicefarm.automation.service;

import com.devicefarm.automation.api.dto.ElementDtos;
import com.devicefarm.automation.domain.ElementEntity;
import com.devicefarm.automation.domain.ElementRepository;
import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ElementService {

    private final ElementRepository repo;

    public ElementService(ElementRepository repo) { this.repo = repo; }

    @Transactional
    public ElementDtos.View create(JwtPrincipal caller, ElementDtos.CreateRequest req) {
        Long pid = requireProduct(caller);
        if (repo.existsByProductIdAndName(pid, req.name())) {
            throw ApiException.conflict("element name already exists in this product");
        }
        ElementEntity e = new ElementEntity(pid, req.name(), req.primaryStrategy(), req.primaryValue(), caller.userId());
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
    public ElementDtos.View update(JwtPrincipal caller, long id, ElementDtos.UpdateRequest req) {
        ElementEntity e = ensureOwned(caller, id);
        if (!e.getName().equals(req.name())
                && repo.existsByProductIdAndName(e.getProductId(), req.name())) {
            throw ApiException.conflict("element name already exists in this product");
        }
        e.setName(req.name());
        e.setDescription(req.description());
        e.setPrimaryStrategy(req.primaryStrategy());
        e.setPrimaryValue(req.primaryValue());
        e.setFallbackLocatorsJson(LocatorJson.write(req.fallbackLocators()));
        return toView(e);
    }

    @Transactional
    public void delete(JwtPrincipal caller, long id) {
        repo.delete(ensureOwned(caller, id));
    }

    @Transactional(readOnly = true)
    public ElementDtos.View get(JwtPrincipal caller, long id) {
        return toView(ensureOwned(caller, id));
    }

    @Transactional(readOnly = true)
    public List<ElementDtos.View> list(JwtPrincipal caller, String q) {
        Long pid = requireProduct(caller);
        List<ElementEntity> raw = (q == null || q.isBlank())
                ? repo.findAllByProductIdOrderByNameAsc(pid)
                : repo.searchByProductId(pid, q.trim());
        return raw.stream().map(this::toView).toList();
    }

    private ElementEntity ensureOwned(JwtPrincipal caller, long id) {
        Long pid = requireProduct(caller);
        ElementEntity e = repo.findById(id).orElseThrow(() -> ApiException.notFound("element"));
        if (!e.getProductId().equals(pid)) throw ApiException.forbidden("cross-product access");
        return e;
    }

    private static Long requireProduct(JwtPrincipal caller) {
        if (caller == null || caller.productId() == null) throw ApiException.unauthorized("missing identity");
        return caller.productId();
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
