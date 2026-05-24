package com.qaplatform.web.automation.service;

import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.web.automation.api.dto.WebSuiteDtos;
import com.qaplatform.web.automation.domain.WebScenarioEntity;
import com.qaplatform.web.automation.domain.WebScenarioRepository;
import com.qaplatform.web.automation.domain.WebStepRepository;
import com.qaplatform.web.automation.domain.WebSuiteEntity;
import com.qaplatform.web.automation.domain.WebSuiteRepository;
import com.qaplatform.web.automation.domain.WebSuiteScenarioEntity;
import com.qaplatform.web.automation.domain.WebSuiteScenarioRepository;
import com.qaplatform.web.automation.tenancy.ProjectContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WebSuiteService {

    private final WebSuiteRepository suites;
    private final WebSuiteScenarioRepository links;
    private final WebScenarioRepository scenarios;
    private final WebStepRepository steps;

    public WebSuiteService(WebSuiteRepository suites, WebSuiteScenarioRepository links,
                           WebScenarioRepository scenarios, WebStepRepository steps) {
        this.suites = suites;
        this.links = links;
        this.scenarios = scenarios;
        this.steps = steps;
    }

    @Transactional
    public WebSuiteDtos.View create(JwtPrincipal caller, ProjectContext ctx, WebSuiteDtos.CreateRequest req) {
        WebSuiteEntity s = new WebSuiteEntity(ctx.projectId(), req.name().trim(), caller.userId());
        s.setDescription(req.description());
        s.setTags(toArray(req.tags()));
        return toView(suites.save(s));
    }

    @Transactional
    public WebSuiteDtos.View update(JwtPrincipal caller, ProjectContext ctx, long id, WebSuiteDtos.UpdateRequest req) {
        WebSuiteEntity s = ensureInProject(ctx, id);
        s.setName(req.name().trim());
        s.setDescription(req.description());
        s.setTags(toArray(req.tags()));
        return toView(s);
    }

    @Transactional
    public void delete(JwtPrincipal caller, ProjectContext ctx, long id) {
        WebSuiteEntity s = ensureInProject(ctx, id);
        links.deleteAllBySuiteId(s.getId());
        suites.delete(s);
    }

    @Transactional(readOnly = true)
    public WebSuiteDtos.View get(JwtPrincipal caller, ProjectContext ctx, long id) {
        return toView(ensureInProject(ctx, id));
    }

    @Transactional(readOnly = true)
    public List<WebSuiteDtos.Summary> list(JwtPrincipal caller, ProjectContext ctx) {
        return suites.findAllByProjectIdOrderByUpdatedAtDesc(ctx.projectId()).stream()
                .map(s -> new WebSuiteDtos.Summary(
                        s.getId(), s.getName(), s.getDescription(),
                        List.of(s.getTags()),
                        (int) links.countBySuiteId(s.getId()),
                        s.getCreatedAt(), s.getUpdatedAt()
                )).toList();
    }

    @Transactional
    public WebSuiteDtos.View addScenario(JwtPrincipal caller, ProjectContext ctx, long suiteId, long scenarioId) {
        WebSuiteEntity s = ensureInProject(ctx, suiteId);
        WebScenarioEntity sc = scenarios.findById(scenarioId)
                .orElseThrow(() -> ApiException.notFound("scenario"));
        if (!ctx.projectId().equals(sc.getProjectId())) {
            throw ApiException.forbidden("scenario not in active project");
        }
        // Append at end
        int order = (int) links.countBySuiteId(suiteId);
        links.save(new WebSuiteScenarioEntity(suiteId, scenarioId, order));
        suites.save(s);  // bump updated_at
        return toView(s);
    }

    @Transactional
    public void removeScenario(JwtPrincipal caller, ProjectContext ctx, long suiteId, long scenarioId) {
        WebSuiteEntity s = ensureInProject(ctx, suiteId);
        List<WebSuiteScenarioEntity> existing = links.findAllBySuiteIdOrderByOrderIndexAsc(suiteId);
        WebSuiteScenarioEntity target = existing.stream()
                .filter(l -> l.getScenarioId().equals(scenarioId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("scenario not in suite"));
        int gone = target.getOrderIndex();
        links.delete(target);
        // compact remaining
        for (WebSuiteScenarioEntity l : existing) {
            if (l.getOrderIndex() > gone) {
                l.setOrderIndex(l.getOrderIndex() - 1);
                links.save(l);
            }
        }
        suites.save(s);
    }

    @Transactional
    public void reorderScenarios(JwtPrincipal caller, ProjectContext ctx, long suiteId, List<Long> scenarioIds) {
        WebSuiteEntity s = ensureInProject(ctx, suiteId);
        List<WebSuiteScenarioEntity> existing = links.findAllBySuiteIdOrderByOrderIndexAsc(suiteId);
        if (existing.size() != scenarioIds.size()) throw ApiException.badRequest("reorder list size mismatch");
        Map<Long, WebSuiteScenarioEntity> byScenario = new HashMap<>();
        existing.forEach(l -> byScenario.put(l.getScenarioId(), l));
        for (int i = 0; i < scenarioIds.size(); i++) {
            WebSuiteScenarioEntity l = byScenario.get(scenarioIds.get(i));
            if (l == null) throw ApiException.badRequest("scenario " + scenarioIds.get(i) + " not in suite");
            l.setOrderIndex(i);
            links.save(l);
        }
        suites.save(s);
    }

    private WebSuiteEntity ensureInProject(ProjectContext ctx, long id) {
        WebSuiteEntity s = suites.findById(id).orElseThrow(() -> ApiException.notFound("suite"));
        if (!ctx.projectId().equals(s.getProjectId())) throw ApiException.forbidden("suite not in active project");
        return s;
    }

    private static String[] toArray(List<String> tags) {
        if (tags == null) return new String[0];
        return tags.stream().filter(t -> t != null && !t.isBlank()).map(String::trim).distinct().toArray(String[]::new);
    }

    private WebSuiteDtos.View toView(WebSuiteEntity s) {
        List<WebSuiteScenarioEntity> raw = links.findAllBySuiteIdOrderByOrderIndexAsc(s.getId());
        List<Long> scenarioIds = raw.stream().map(WebSuiteScenarioEntity::getScenarioId).toList();
        Map<Long, WebScenarioEntity> scenarioById = new HashMap<>();
        if (!scenarioIds.isEmpty()) {
            scenarios.findAllById(scenarioIds).forEach(sc -> scenarioById.put(sc.getId(), sc));
        }
        // Bulk-count steps per scenario (one query per scenario id; small N).
        Map<Long, Integer> stepCounts = new HashMap<>();
        for (Long sid : scenarioIds) stepCounts.put(sid, (int) steps.countByScenarioId(sid));

        List<WebSuiteDtos.ScenarioRef> refs = new java.util.ArrayList<>();
        for (WebSuiteScenarioEntity link : raw) {
            WebScenarioEntity sc = scenarioById.get(link.getScenarioId());
            if (sc == null) continue;  // scenario was deleted; suite_scenarios is CASCADE so shouldn't happen
            refs.add(new WebSuiteDtos.ScenarioRef(
                    sc.getId(), sc.getName(), sc.getDescription(), List.of(sc.getTags()),
                    stepCounts.getOrDefault(sc.getId(), 0),
                    link.getOrderIndex()
            ));
        }
        return new WebSuiteDtos.View(
                s.getId(), s.getName(), s.getDescription(),
                List.of(s.getTags()),
                s.getCreatedAt(), s.getUpdatedAt(),
                refs
        );
    }
}
