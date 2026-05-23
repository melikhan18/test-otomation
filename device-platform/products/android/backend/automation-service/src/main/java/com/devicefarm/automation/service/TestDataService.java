package com.devicefarm.automation.service;

import com.devicefarm.automation.api.dto.TestDataDtos;
import com.devicefarm.automation.domain.TestDataEntity;
import com.devicefarm.automation.domain.TestDataRepository;
import com.devicefarm.automation.tenancy.ProjectContext;
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
    public TestDataDtos.View create(JwtPrincipal caller, ProjectContext ctx, TestDataDtos.CreateRequest req) {
        if (repo.existsByProjectIdAndNameAndEnvironment(ctx.projectId(), req.name(), req.environment())) {
            throw ApiException.conflict("test data with this name + environment already exists");
        }
        TestDataEntity e = new TestDataEntity(ctx.legacyProductId(), ctx.projectId(),
                req.name(), req.environment(), req.value(), caller.userId());
        e.setDescription(req.description());
        e.setSensitive(req.sensitive());
        return toView(repo.save(e), false);
    }

    @Transactional
    public TestDataDtos.View update(JwtPrincipal caller, ProjectContext ctx, long id, TestDataDtos.UpdateRequest req) {
        TestDataEntity e = ensureInProject(ctx, id);
        boolean renamed = !e.getName().equals(req.name()) || !e.getEnvironment().equals(req.environment());
        if (renamed && repo.existsByProjectIdAndNameAndEnvironment(ctx.projectId(), req.name(), req.environment())) {
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
    public void delete(JwtPrincipal caller, ProjectContext ctx, long id) {
        repo.delete(ensureInProject(ctx, id));
    }

    @Transactional(readOnly = true)
    public TestDataDtos.View get(JwtPrincipal caller, ProjectContext ctx, long id, boolean reveal) {
        TestDataEntity e = ensureInProject(ctx, id);
        return toView(e, reveal && caller.isAdmin());
    }

    @Transactional(readOnly = true)
    public List<TestDataDtos.View> list(JwtPrincipal caller, ProjectContext ctx, String environment, boolean reveal) {
        List<TestDataEntity> raw = (environment == null || environment.isBlank())
                ? repo.findAllByProjectIdOrderByNameAscEnvironmentAsc(ctx.projectId())
                : repo.findAllByProjectIdAndEnvironmentOrderByNameAsc(ctx.projectId(), environment);
        boolean canReveal = reveal && caller.isAdmin();
        return raw.stream().map(e -> toView(e, canReveal)).toList();
    }

    @Transactional(readOnly = true)
    public List<String> environments(JwtPrincipal caller, ProjectContext ctx) {
        List<String> envs = repo.findDistinctEnvironments(ctx.projectId());
        if (!envs.contains("default")) envs = new java.util.ArrayList<>(envs) {{ add(0, "default"); }};
        return envs;
    }

    private TestDataEntity ensureInProject(ProjectContext ctx, long id) {
        TestDataEntity e = repo.findById(id).orElseThrow(() -> ApiException.notFound("test data"));
        if (!ctx.projectId().equals(e.getProjectId())) throw ApiException.forbidden("test data not in active project");
        return e;
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
