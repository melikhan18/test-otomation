package com.devicefarm.automation.service;

import com.devicefarm.automation.api.dto.SuiteDtos;
import com.devicefarm.automation.domain.*;
import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtPrincipal;
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

    /* ─────────────────────────── Suite CRUD ───────────────────────────── */

    @Transactional
    public SuiteDtos.View create(JwtPrincipal caller, SuiteDtos.CreateRequest req) {
        Long pid = requireProduct(caller);
        SuiteEntity s = new SuiteEntity(pid, req.name().trim(), caller.userId());
        s.setDescription(req.description());
        s.setTags(toArray(req.tags()));
        return toView(suites.save(s));
    }

    @Transactional
    public SuiteDtos.View update(JwtPrincipal caller, long id, SuiteDtos.UpdateRequest req) {
        SuiteEntity s = ensureOwned(caller, id);
        s.setName(req.name().trim());
        s.setDescription(req.description());
        s.setTags(toArray(req.tags()));
        return toView(s);
    }

    @Transactional
    public void delete(JwtPrincipal caller, long id) {
        SuiteEntity s = ensureOwned(caller, id);
        links.deleteAllBySuiteId(s.getId());
        suites.delete(s);
    }

    @Transactional(readOnly = true)
    public SuiteDtos.View get(JwtPrincipal caller, long id) {
        return toView(ensureOwned(caller, id));
    }

    @Transactional(readOnly = true)
    public List<SuiteDtos.Summary> list(JwtPrincipal caller) {
        Long pid = requireProduct(caller);
        List<SuiteEntity> raw = suites.findAllByProductIdOrderByUpdatedAtDesc(pid);
        return raw.stream().map(s -> new SuiteDtos.Summary(
                s.getId(), s.getProductId(), s.getName(), s.getDescription(),
                List.of(s.getTags()),
                (int) links.countBySuiteId(s.getId()),
                s.getCreatedAt(), s.getUpdatedAt()
        )).toList();
    }

    /* ──────────────────────── Scenario membership ─────────────────────── */

    @Transactional
    public SuiteDtos.View addScenario(JwtPrincipal caller, long suiteId, SuiteDtos.AddScenarioRequest req) {
        SuiteEntity suite = ensureOwned(caller, suiteId);
        ScenarioEntity sc = scenarios.findById(req.scenarioId())
                .orElseThrow(() -> ApiException.notFound("scenario"));
        if (!sc.getProductId().equals(suite.getProductId())) {
            throw ApiException.forbidden("cross-product scenario");
        }
        // Idempotent: skip if already present.
        var existing = links.findAllBySuiteIdOrderByOrderIndexAsc(suite.getId());
        for (SuiteScenarioEntity link : existing) {
            if (link.getScenarioId().equals(sc.getId())) return toView(suite);
        }
        links.save(new SuiteScenarioEntity(suite.getId(), sc.getId(), existing.size()));
        suites.save(suite);
        return toView(suite);
    }

    @Transactional
    public SuiteDtos.View removeScenario(JwtPrincipal caller, long suiteId, long scenarioId) {
        SuiteEntity suite = ensureOwned(caller, suiteId);
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
    public SuiteDtos.View reorderScenarios(JwtPrincipal caller, long suiteId, SuiteDtos.ReorderRequest req) {
        SuiteEntity suite = ensureOwned(caller, suiteId);
        var existing = links.findAllBySuiteIdOrderByOrderIndexAsc(suite.getId());
        if (existing.size() != req.scenarioIds().size()) {
            throw ApiException.badRequest("scenario id list size mismatch (expected " + existing.size() + ", got " + req.scenarioIds().size() + ")");
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

    /* ───────────────────────────── helpers ────────────────────────────── */

    private SuiteEntity ensureOwned(JwtPrincipal caller, long id) {
        Long pid = requireProduct(caller);
        SuiteEntity s = suites.findById(id).orElseThrow(() -> ApiException.notFound("suite"));
        if (!s.getProductId().equals(pid)) throw ApiException.forbidden("cross-product access");
        return s;
    }

    private static Long requireProduct(JwtPrincipal caller) {
        if (caller == null || caller.productId() == null) throw ApiException.unauthorized("missing identity");
        return caller.productId();
    }

    private static String[] toArray(List<String> tags) {
        if (tags == null) return new String[0];
        return tags.stream().filter(t -> t != null && !t.isBlank()).toArray(String[]::new);
    }

    private SuiteDtos.View toView(SuiteEntity suite) {
        var existing = links.findAllBySuiteIdOrderByOrderIndexAsc(suite.getId());
        List<Long> scenarioIds = existing.stream().map(SuiteScenarioEntity::getScenarioId).toList();

        // Bulk-fetch scenarios + their step counts.
        Map<Long, ScenarioEntity> scMap = scenarioIds.isEmpty() ? Map.of()
                : scenarios.findAllById(scenarioIds).stream()
                    .collect(Collectors.toMap(ScenarioEntity::getId, x -> x));
        Map<Long, Integer> stepCounts = new HashMap<>();
        for (Long sid : scenarioIds) stepCounts.put(sid, (int) steps.countByScenarioId(sid));

        List<SuiteDtos.ScenarioRef> refs = new ArrayList<>(existing.size());
        for (SuiteScenarioEntity link : existing) {
            ScenarioEntity sc = scMap.get(link.getScenarioId());
            if (sc == null) continue; // orphan link (shouldn't happen)
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
