package com.qaplatform.android.automation.service;

import com.qaplatform.android.automation.api.dto.SuiteDtos;
import com.qaplatform.android.automation.domain.*;
import com.qaplatform.android.automation.tenancy.ProjectContext;
import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SuiteService {

    private final SuiteRepository suites;
    private final SuiteScenarioRepository links;
    private final ScenarioRepository scenarios;
    private final StepRepository steps;

    public SuiteService(SuiteRepository suites, SuiteScenarioRepository links,
                        ScenarioRepository scenarios, StepRepository steps) {
        this.suites = suites;
        this.links = links;
        this.scenarios = scenarios;
        this.steps = steps;
    }

    @Transactional
    public SuiteDtos.View create(JwtPrincipal caller, ProjectContext ctx, SuiteDtos.CreateRequest req) {
        SuiteEntity s = new SuiteEntity(ctx.legacyProductId(), ctx.projectId(),
                req.name().trim(), caller.userId());
        s.setDescription(req.description());
        s.setTags(toArray(req.tags()));
        return toView(suites.save(s));
    }

    @Transactional
    public SuiteDtos.View update(JwtPrincipal caller, ProjectContext ctx, long id, SuiteDtos.UpdateRequest req) {
        SuiteEntity s = ensureInProject(ctx, id);
        s.setName(req.name().trim());
        s.setDescription(req.description());
        s.setTags(toArray(req.tags()));
        return toView(s);
    }

    @Transactional
    public void delete(JwtPrincipal caller, ProjectContext ctx, long id) {
        SuiteEntity s = ensureInProject(ctx, id);
        links.deleteAllBySuiteId(s.getId());
        suites.delete(s);
    }

    @Transactional(readOnly = true)
    public SuiteDtos.View get(JwtPrincipal caller, ProjectContext ctx, long id) {
        return toView(ensureInProject(ctx, id));
    }

    @Transactional(readOnly = true)
    public List<SuiteDtos.Summary> list(JwtPrincipal caller, ProjectContext ctx) {
        return suites.findAllByProjectIdOrderByUpdatedAtDesc(ctx.projectId()).stream()
                .map(s -> new SuiteDtos.Summary(
                        s.getId(), s.getProductId(), s.getName(), s.getDescription(),
                        List.of(s.getTags()),
                        (int) links.countBySuiteId(s.getId()),
                        s.getCreatedAt(), s.getUpdatedAt()
                )).toList();
    }

    @Transactional
    public SuiteDtos.View addScenario(JwtPrincipal caller, ProjectContext ctx, long suiteId, SuiteDtos.AddScenarioRequest req) {
        SuiteEntity suite = ensureInProject(ctx, suiteId);
        ScenarioEntity sc = scenarios.findById(req.scenarioId())
                .orElseThrow(() -> ApiException.notFound("scenario"));
        if (!ctx.projectId().equals(sc.getProjectId())) {
            throw ApiException.forbidden("scenario not in active project");
        }
        var existing = links.findAllBySuiteIdOrderByOrderIndexAsc(suite.getId());
        for (SuiteScenarioEntity link : existing) {
            if (link.getScenarioId().equals(sc.getId())) return toView(suite);
        }
        links.save(new SuiteScenarioEntity(suite.getId(), sc.getId(), existing.size()));
        suites.save(suite);
        return toView(suite);
    }

    @Transactional
    public SuiteDtos.View removeScenario(JwtPrincipal caller, ProjectContext ctx, long suiteId, long scenarioId) {
        SuiteEntity suite = ensureInProject(ctx, suiteId);
        var existing = links.findAllBySuiteIdOrderByOrderIndexAsc(suite.getId());
        SuiteScenarioEntity target = null;
        for (SuiteScenarioEntity link : existing) {
            if (link.getScenarioId().equals(scenarioId)) { target = link; break; }
        }
        if (target == null) throw ApiException.notFound("scenario in suite");
        int removed = target.getOrderIndex();
        links.delete(target);
        for (SuiteScenarioEntity link : existing) {
            if (!link.getScenarioId().equals(scenarioId) && link.getOrderIndex() > removed) {
                link.setOrderIndex(link.getOrderIndex() - 1);
            }
        }
        suites.save(suite);
        return toView(suite);
    }

    @Transactional
    public SuiteDtos.View reorderScenarios(JwtPrincipal caller, ProjectContext ctx, long suiteId, SuiteDtos.ReorderRequest req) {
        SuiteEntity suite = ensureInProject(ctx, suiteId);
        var existing = links.findAllBySuiteIdOrderByOrderIndexAsc(suite.getId());
        if (existing.size() != req.scenarioIds().size()) {
            throw ApiException.badRequest("scenario id list size mismatch");
        }
        Map<Long, SuiteScenarioEntity> byId = existing.stream()
                .collect(Collectors.toMap(SuiteScenarioEntity::getScenarioId, x -> x));
        for (int i = 0; i < req.scenarioIds().size(); i++) {
            SuiteScenarioEntity link = byId.get(req.scenarioIds().get(i));
            if (link == null) throw ApiException.badRequest("unknown scenario id: " + req.scenarioIds().get(i));
            link.setOrderIndex(i);
        }
        suites.save(suite);
        return toView(suite);
    }

    private SuiteEntity ensureInProject(ProjectContext ctx, long id) {
        SuiteEntity s = suites.findById(id).orElseThrow(() -> ApiException.notFound("suite"));
        if (!ctx.projectId().equals(s.getProjectId())) throw ApiException.forbidden("suite not in active project");
        return s;
    }

    private static String[] toArray(List<String> tags) {
        if (tags == null) return new String[0];
        return tags.stream().filter(t -> t != null && !t.isBlank()).toArray(String[]::new);
    }

    private SuiteDtos.View toView(SuiteEntity suite) {
        var existing = links.findAllBySuiteIdOrderByOrderIndexAsc(suite.getId());
        List<Long> scenarioIds = existing.stream().map(SuiteScenarioEntity::getScenarioId).toList();
        Map<Long, ScenarioEntity> scMap = scenarioIds.isEmpty() ? Map.of()
                : scenarios.findAllById(scenarioIds).stream()
                    .collect(Collectors.toMap(ScenarioEntity::getId, x -> x));
        Map<Long, Integer> stepCounts = new HashMap<>();
        for (Long sid : scenarioIds) stepCounts.put(sid, (int) steps.countByScenarioId(sid));

        List<SuiteDtos.ScenarioRef> refs = new ArrayList<>(existing.size());
        for (SuiteScenarioEntity link : existing) {
            ScenarioEntity sc = scMap.get(link.getScenarioId());
            if (sc == null) continue;
            refs.add(new SuiteDtos.ScenarioRef(
                    sc.getId(), sc.getName(), sc.getDescription(), List.of(sc.getTags()),
                    stepCounts.getOrDefault(sc.getId(), 0),
                    link.getOrderIndex()
            ));
        }
        return new SuiteDtos.View(
                suite.getId(), suite.getProductId(), suite.getName(), suite.getDescription(),
                List.of(suite.getTags()),
                suite.getCreatedAt(), suite.getUpdatedAt(),
                refs
        );
    }
}
