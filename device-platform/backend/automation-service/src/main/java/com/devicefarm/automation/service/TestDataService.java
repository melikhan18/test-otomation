package com.devicefarm.automation.service;

import com.devicefarm.automation.api.dto.TestDataDtos;
import com.devicefarm.automation.domain.TestDataEntity;
import com.devicefarm.automation.domain.TestDataRepository;
import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TestDataService {

    private static final String MASKED = "••••••••";

    private final TestDataRepository repo;

    public TestDataService(TestDataRepository repo) { this.repo = repo; }

    @Transactional
    public TestDataDtos.View create(JwtPrincipal caller, TestDataDtos.CreateRequest req) {
        Long pid = requireProduct(caller);
        if (repo.existsByProductIdAndNameAndEnvironment(pid, req.name(), req.environment())) {
            throw ApiException.conflict("test data with this name + environment already exists");
        }
        TestDataEntity e = new TestDataEntity(pid, req.name(), req.environment(), req.value(), caller.userId());
        e.setDescription(req.description());
        e.setSensitive(req.sensitive());
        return toView(repo.save(e), false);
    }

    @Transactional
    public TestDataDtos.View update(JwtPrincipal caller, long id, TestDataDtos.UpdateRequest req) {
        TestDataEntity e = ensureOwned(caller, id);
        boolean renamed = !e.getName().equals(req.name()) || !e.getEnvironment().equals(req.environment());
        if (renamed && repo.existsByProductIdAndNameAndEnvironment(e.getProductId(), req.name(), req.environment())) {
            throw ApiException.conflict("test data with this name + environment already exists");
        }
        e.setName(req.name());
        e.setEnvironment(req.environment());
        e.setValue(req.value());
        e.setDescription(req.description());
        e.setSensitive(req.sensitive());
        return toView(e, false);
    }

    @Transactional
    public void delete(JwtPrincipal caller, long id) {
        repo.delete(ensureOwned(caller, id));
    }

    @Transactional(readOnly = true)
    public TestDataDtos.View get(JwtPrincipal caller, long id, boolean reveal) {
        TestDataEntity e = ensureOwned(caller, id);
        return toView(e, reveal && caller.isAdmin());
    }

    @Transactional(readOnly = true)
    public List<TestDataDtos.View> list(JwtPrincipal caller, String environment, boolean reveal) {
        Long pid = requireProduct(caller);
        List<TestDataEntity> raw = (environment == null || environment.isBlank())
                ? repo.findAllByProductIdOrderByNameAscEnvironmentAsc(pid)
                : repo.findAllByProductIdAndEnvironmentOrderByNameAsc(pid, environment);
        boolean canReveal = reveal && caller.isAdmin();
        return raw.stream().map(e -> toView(e, canReveal)).toList();
    }

    @Transactional(readOnly = true)
    public List<String> environments(JwtPrincipal caller) {
        Long pid = requireProduct(caller);
        List<String> envs = repo.findDistinctEnvironments(pid);
        if (!envs.contains("default")) envs = new java.util.ArrayList<>(envs) {{ add(0, "default"); }};
        return envs;
    }

    private TestDataEntity ensureOwned(JwtPrincipal caller, long id) {
        Long pid = requireProduct(caller);
        TestDataEntity e = repo.findById(id).orElseThrow(() -> ApiException.notFound("test data"));
        if (!e.getProductId().equals(pid)) throw ApiException.forbidden("cross-product access");
        return e;
    }

    private static Long requireProduct(JwtPrincipal caller) {
        if (caller == null || caller.productId() == null) throw ApiException.unauthorized("missing identity");
        return caller.productId();
    }

    private TestDataDtos.View toView(TestDataEntity e, boolean revealSensitive) {
        boolean mask = e.isSensitive() && !revealSensitive;
        String value = mask ? MASKED : e.getValue();
        return new TestDataDtos.View(
                e.getId(), e.getProductId(), e.getName(), e.getEnvironment(),
                value, e.getDescription(), e.isSensitive(), mask,
                e.getCreatedByUserId(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
