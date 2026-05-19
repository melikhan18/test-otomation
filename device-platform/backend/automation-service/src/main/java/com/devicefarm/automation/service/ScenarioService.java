package com.devicefarm.automation.service;

import com.devicefarm.automation.api.dto.ScenarioDtos;
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
public class ScenarioService {

    private final ScenarioRepository scenarios;
    private final StepRepository steps;
    private final ElementRepository elements;
    private final TestDataRepository data;
    private final SuiteScenarioRepository suiteScenarios;
    private final SuiteRepository suites;

    public ScenarioService(ScenarioRepository scenarios, StepRepository steps,
                           ElementRepository elements, TestDataRepository data,
                           SuiteScenarioRepository suiteScenarios, SuiteRepository suites) {
        this.scenarios = scenarios;
        this.steps = steps;
        this.elements = elements;
        this.data = data;
        this.suiteScenarios = suiteScenarios;
        this.suites = suites;
    }

    /* ───────────────────────────── Scenario CRUD ──────────────────────────── */

    @Transactional
    public ScenarioDtos.View create(JwtPrincipal caller, ScenarioDtos.CreateRequest req) {
        Long pid = requireProduct(caller);
        ScenarioEntity s = new ScenarioEntity(pid, req.name().trim(), caller.userId());
        s.setDescription(req.description());
        s.setTags(toArray(req.tags()));
        s.setPreconditions(req.preconditions());
        return toFullView(scenarios.save(s));
    }

    @Transactional
    public ScenarioDtos.View update(JwtPrincipal caller, long id, ScenarioDtos.UpdateRequest req) {
        ScenarioEntity s = ensureOwned(caller, id);
        s.setName(req.name().trim());
        s.setDescription(req.description());
        s.setTags(toArray(req.tags()));
        s.setPreconditions(req.preconditions());
        return toFullView(s);
    }

    @Transactional
    public void delete(JwtPrincipal caller, long id) {
        ScenarioEntity s = ensureOwned(caller, id);
        steps.deleteAllByScenarioId(s.getId());
        suiteScenarios.deleteAllByScenarioId(s.getId());
        scenarios.delete(s);
    }

    @Transactional(readOnly = true)
    public ScenarioDtos.View get(JwtPrincipal caller, long id) {
        return toFullView(ensureOwned(caller, id));
    }

    @Transactional(readOnly = true)
    public List<ScenarioDtos.Summary> list(JwtPrincipal caller) {
        Long pid = requireProduct(caller);
        List<ScenarioEntity> raw = scenarios.findAllByProductIdOrderByUpdatedAtDesc(pid);
        return raw.stream().map(s ->
            new ScenarioDtos.Summary(
                s.getId(), s.getProductId(), s.getName(), s.getDescription(),
                List.of(s.getTags()), s.getVersion(),
                (int) steps.countByScenarioId(s.getId()),
                s.getCreatedAt(), s.getUpdatedAt()
            )
        ).toList();
    }

    /* ─────────────────────────────── Step ops ─────────────────────────────── */

    @Transactional
    public ScenarioDtos.View addStep(JwtPrincipal caller, long scenarioId, ScenarioDtos.StepCreateRequest req) {
        ScenarioEntity sc = ensureOwned(caller, scenarioId);
        validateRefs(caller.productId(), req.targetElementId(), req.dataId());
        StepValidator.validate(req.action(), req.targetElementId(), req.dataId(), req.literalValue());

        List<StepEntity> existing = steps.findAllByScenarioIdOrderByOrderIndexAsc(sc.getId());
        int insertAt = req.position() != null ? Math.max(0, Math.min(req.position(), existing.size())) : existing.size();

        // Shift everything from insertAt onward by +1
        for (int i = insertAt; i < existing.size(); i++) {
            StepEntity st = existing.get(i);
            st.setOrderIndex(st.getOrderIndex() + 1);
        }

        StepEntity step = new StepEntity(sc.getId(), insertAt, req.action());
        step.setTargetElementId(req.targetElementId());
        step.setDataId(req.dataId());
        step.setLiteralValue(req.literalValue());
        step.setExpectedResult(nullIfBlank(req.expectedResult()));
        if (req.timeoutMs() != null)        step.setTimeoutMs(req.timeoutMs());
        if (req.retryCount() != null)       step.setRetryCount(req.retryCount());
        if (req.screenshotAfter() != null)  step.setScreenshotAfter(req.screenshotAfter());
        steps.save(step);

        // touch parent so version increments
        scenarios.save(sc);
        return toFullView(sc);
    }

    @Transactional
    public ScenarioDtos.View updateStep(JwtPrincipal caller, long scenarioId, long stepId, ScenarioDtos.StepUpdateRequest req) {
        ScenarioEntity sc = ensureOwned(caller, scenarioId);
        StepEntity st = ensureStep(sc, stepId);
        validateRefs(caller.productId(), req.targetElementId(), req.dataId());
        StepValidator.validate(req.action(), req.targetElementId(), req.dataId(), req.literalValue());

        st.setAction(req.action());
        st.setTargetElementId(req.targetElementId());
        st.setDataId(req.dataId());
        st.setLiteralValue(req.literalValue());
        st.setExpectedResult(nullIfBlank(req.expectedResult()));
        if (req.timeoutMs() != null)        st.setTimeoutMs(req.timeoutMs());
        if (req.retryCount() != null)       st.setRetryCount(req.retryCount());
        if (req.screenshotAfter() != null)  st.setScreenshotAfter(req.screenshotAfter());

        scenarios.save(sc);
        return toFullView(sc);
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    @Transactional
    public ScenarioDtos.View deleteStep(JwtPrincipal caller, long scenarioId, long stepId) {
        ScenarioEntity sc = ensureOwned(caller, scenarioId);
        StepEntity st = ensureStep(sc, stepId);
        int removed = st.getOrderIndex();
        steps.delete(st);

        // Compact remaining order indices
        List<StepEntity> remaining = steps.findAllByScenarioIdOrderByOrderIndexAsc(sc.getId());
        for (StepEntity s : remaining) {
            if (s.getOrderIndex() > removed) s.setOrderIndex(s.getOrderIndex() - 1);
        }

        scenarios.save(sc);
        return toFullView(sc);
    }

    /** Replace step order using the supplied list. Must contain ALL step ids of the scenario. */
    @Transactional
    public ScenarioDtos.View reorderSteps(JwtPrincipal caller, long scenarioId, ScenarioDtos.ReorderRequest req) {
        ScenarioEntity sc = ensureOwned(caller, scenarioId);
        List<StepEntity> existing = steps.findAllByScenarioIdOrderByOrderIndexAsc(sc.getId());
        if (existing.size() != req.stepIds().size()) {
            throw ApiException.badRequest("step id list size mismatch (expected " + existing.size() + ", got " + req.stepIds().size() + ")");
        }
        Map<Long, StepEntity> byId = existing.stream().collect(Collectors.toMap(StepEntity::getId, x -> x));
        for (int i = 0; i < req.stepIds().size(); i++) {
            StepEntity st = byId.get(req.stepIds().get(i));
            if (st == null) throw ApiException.badRequest("unknown step id: " + req.stepIds().get(i));
            st.setOrderIndex(i);
        }
        scenarios.save(sc);
        return toFullView(sc);
    }

    /* ───────────────────────────── helpers ────────────────────────────────── */

    private void validateRefs(Long productId, Long elementId, Long dataId) {
        if (elementId != null) {
            ElementEntity el = elements.findById(elementId).orElseThrow(() -> ApiException.notFound("element"));
            if (!el.getProductId().equals(productId)) throw ApiException.forbidden("cross-product element");
        }
        if (dataId != null) {
            TestDataEntity td = data.findById(dataId).orElseThrow(() -> ApiException.notFound("test data"));
            if (!td.getProductId().equals(productId)) throw ApiException.forbidden("cross-product test data");
        }
    }

    private ScenarioEntity ensureOwned(JwtPrincipal caller, long id) {
        Long pid = requireProduct(caller);
        ScenarioEntity s = scenarios.findById(id).orElseThrow(() -> ApiException.notFound("scenario"));
        if (!s.getProductId().equals(pid)) throw ApiException.forbidden("cross-product access");
        return s;
    }

    private StepEntity ensureStep(ScenarioEntity sc, long stepId) {
        StepEntity st = steps.findById(stepId).orElseThrow(() -> ApiException.notFound("step"));
        if (!st.getScenarioId().equals(sc.getId())) throw ApiException.badRequest("step belongs to a different scenario");
        return st;
    }

    private static Long requireProduct(JwtPrincipal caller) {
        if (caller == null || caller.productId() == null) throw ApiException.unauthorized("missing identity");
        return caller.productId();
    }

    private static String[] toArray(List<String> tags) {
        if (tags == null) return new String[0];
        return tags.stream().filter(t -> t != null && !t.isBlank()).toArray(String[]::new);
    }

    /* ─────────────────────── projection / view builders ───────────────────── */

    private ScenarioDtos.View toFullView(ScenarioEntity sc) {
        List<StepEntity> raw = steps.findAllByScenarioIdOrderByOrderIndexAsc(sc.getId());

        // Pre-fetch referenced elements + data so each step view is built without N+1.
        List<Long> elementIds = raw.stream().map(StepEntity::getTargetElementId).filter(java.util.Objects::nonNull).toList();
        List<Long> dataIds    = raw.stream().map(StepEntity::getDataId).filter(java.util.Objects::nonNull).toList();
        Map<Long, ElementEntity>  eMap = elementIds.isEmpty() ? Map.of()
                : elements.findAllById(elementIds).stream().collect(Collectors.toMap(ElementEntity::getId, x -> x));
        Map<Long, TestDataEntity> dMap = dataIds.isEmpty() ? Map.of()
                : data.findAllById(dataIds).stream().collect(Collectors.toMap(TestDataEntity::getId, x -> x));

        List<ScenarioDtos.StepView> stepViews = new ArrayList<>(raw.size());
        for (StepEntity st : raw) {
            ElementEntity el = st.getTargetElementId() != null ? eMap.get(st.getTargetElementId()) : null;
            TestDataEntity td = st.getDataId() != null ? dMap.get(st.getDataId()) : null;
            stepViews.add(new ScenarioDtos.StepView(
                st.getId(), st.getScenarioId(), st.getOrderIndex(), st.getAction(),
                el != null ? new ScenarioDtos.ElementRef(
                        el.getId(), el.getName(), el.getPrimaryStrategy(), el.getPrimaryValue(), el.getScreenshotData()
                ) : null,
                td != null ? new ScenarioDtos.DataRef(
                        td.getId(), td.getName(), td.getEnvironment(), td.isSensitive()
                ) : null,
                st.getLiteralValue(),
                st.getExpectedResult(),
                st.getTimeoutMs(),
                st.getRetryCount(),
                st.isScreenshotAfter(),
                st.getCreatedAt()
            ));
        }

        // Reverse-link: every suite this scenario currently belongs to. Used by the UI to
        // warn about the blast radius of an edit / delete.
        List<SuiteScenarioEntity> parentLinks = suiteScenarios.findAllByScenarioId(sc.getId());
        List<Long> parentIds = parentLinks.stream().map(SuiteScenarioEntity::getSuiteId).toList();
        Map<Long, SuiteEntity> parentSuiteMap = parentIds.isEmpty() ? Map.of()
                : suites.findAllById(parentIds).stream()
                    .collect(Collectors.toMap(SuiteEntity::getId, x -> x));
        List<ScenarioDtos.ParentSuiteRef> parentRefs = new ArrayList<>(parentLinks.size());
        for (SuiteScenarioEntity link : parentLinks) {
            SuiteEntity suite = parentSuiteMap.get(link.getSuiteId());
            if (suite == null) continue;
            parentRefs.add(new ScenarioDtos.ParentSuiteRef(
                    suite.getId(), suite.getName(), List.of(suite.getTags())
            ));
        }
        // Stable order by suite name for predictable UI rendering
        parentRefs.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));

        return new ScenarioDtos.View(
                sc.getId(), sc.getProductId(), sc.getName(), sc.getDescription(),
                List.of(sc.getTags()), sc.getPreconditions(),
                sc.getVersion(), sc.getCreatedAt(), sc.getUpdatedAt(),
                stepViews,
                parentRefs
        );
    }
}
