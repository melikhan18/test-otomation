package com.qaplatform.android.automation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaplatform.android.automation.api.dto.ScenarioDtos;
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
public class ScenarioService {

    private final ScenarioRepository scenarios;
    private final StepRepository steps;
    private final ElementRepository elements;
    private final TestDataRepository data;
    private final SuiteScenarioRepository suiteScenarios;
    private final SuiteRepository suites;
    private final ObjectMapper json;

    public ScenarioService(ScenarioRepository scenarios, StepRepository steps,
                           ElementRepository elements, TestDataRepository data,
                           SuiteScenarioRepository suiteScenarios, SuiteRepository suites,
                           ObjectMapper json) {
        this.scenarios = scenarios;
        this.steps = steps;
        this.elements = elements;
        this.data = data;
        this.suiteScenarios = suiteScenarios;
        this.suites = suites;
        this.json = json;
    }

    /* ───────────────────────────── Scenario CRUD ──────────────────────────── */

    @Transactional
    public ScenarioDtos.View create(JwtPrincipal caller, ProjectContext ctx, ScenarioDtos.CreateRequest req) {
        ScenarioEntity s = new ScenarioEntity(ctx.projectId(),
                req.name().trim(), caller.userId());
        s.setDescription(req.description());
        s.setTags(toArray(req.tags()));
        s.setPreconditions(req.preconditions());
        return toFullView(scenarios.save(s));
    }

    @Transactional
    public ScenarioDtos.View update(JwtPrincipal caller, ProjectContext ctx, long id, ScenarioDtos.UpdateRequest req) {
        ScenarioEntity s = ensureInProject(ctx, id);
        s.setName(req.name().trim());
        s.setDescription(req.description());
        s.setTags(toArray(req.tags()));
        s.setPreconditions(req.preconditions());
        return toFullView(s);
    }

    @Transactional
    public void delete(JwtPrincipal caller, ProjectContext ctx, long id) {
        ScenarioEntity s = ensureInProject(ctx, id);
        steps.deleteAllByScenarioId(s.getId());
        suiteScenarios.deleteAllByScenarioId(s.getId());
        scenarios.delete(s);
    }

    @Transactional(readOnly = true)
    public ScenarioDtos.View get(JwtPrincipal caller, ProjectContext ctx, long id) {
        return toFullView(ensureInProject(ctx, id));
    }

    @Transactional(readOnly = true)
    public List<ScenarioDtos.Summary> list(JwtPrincipal caller, ProjectContext ctx) {
        List<ScenarioEntity> raw = scenarios.findAllByProjectIdOrderByUpdatedAtDesc(ctx.projectId());
        return raw.stream().map(s ->
            new ScenarioDtos.Summary(
                s.getId(), s.getName(), s.getDescription(),
                List.of(s.getTags()), s.getVersion(),
                (int) steps.countByScenarioId(s.getId()),
                s.getCreatedAt(), s.getUpdatedAt()
            )
        ).toList();
    }

    /* ─────────────────────────────── Step ops ─────────────────────────────── */

    @Transactional
    public ScenarioDtos.View addStep(JwtPrincipal caller, ProjectContext ctx, long scenarioId, ScenarioDtos.StepCreateRequest req) {
        ScenarioEntity sc = ensureInProject(ctx, scenarioId);
        validateRefs(ctx, req.targetElementId(), req.dataId());
        StepValidator.validate(req.action(), req.targetElementId(), req.dataId(), req.literalValue());

        // Validate tree position. Both parent + branch null = root level;
        // both set = inside an IF branch. Mismatched values are a bug.
        Long parentId = req.parentStepId();
        String branch = req.branchLabel();
        if ((parentId == null) != (branch == null)) {
            throw ApiException.badRequest("parentStepId and branchLabel must be set together (or both null for root)");
        }
        if (parentId != null) {
            StepEntity parent = ensureStep(sc, parentId);
            if (parent.getAction() != StepAction.IF) {
                throw ApiException.badRequest("parent step must be an IF (was " + parent.getAction() + ")");
            }
        }

        // IF rows MUST carry a condition; non-IF rows MUST NOT.
        if (req.action() == StepAction.IF) {
            if (req.condition() == null) throw ApiException.badRequest("IF step requires a condition");
            validateCondition(req.condition());
        } else if (req.condition() != null) {
            throw ApiException.badRequest("condition is only valid for IF actions");
        }

        List<StepEntity> siblings = (parentId == null)
                ? steps.findRootStepsByScenarioId(sc.getId())
                : steps.findAllByParentStepIdAndBranchLabelOrderByOrderIndexAsc(parentId, branch);
        int insertAt = req.position() != null
                ? Math.max(0, Math.min(req.position(), siblings.size()))
                : siblings.size();
        for (int i = insertAt; i < siblings.size(); i++) {
            StepEntity sh = siblings.get(i);
            sh.setOrderIndex(sh.getOrderIndex() + 1);
        }

        StepEntity step = new StepEntity(sc.getId(), insertAt, req.action());
        step.setTargetElementId(req.targetElementId());
        step.setDataId(req.dataId());
        step.setLiteralValue(req.literalValue());
        step.setExpectedResult(nullIfBlank(req.expectedResult()));
        if (req.timeoutMs() != null)        step.setTimeoutMs(req.timeoutMs());
        if (req.retryCount() != null)       step.setRetryCount(req.retryCount());
        if (req.screenshotAfter() != null)  step.setScreenshotAfter(req.screenshotAfter());
        step.setParentStepId(parentId);
        step.setBranchLabel(branch);
        if (req.condition() != null) step.setConditionJson(writeJson(req.condition()));
        steps.save(step);

        scenarios.save(sc);
        return toFullView(sc);
    }

    @Transactional
    public ScenarioDtos.View updateStep(JwtPrincipal caller, ProjectContext ctx, long scenarioId, long stepId, ScenarioDtos.StepUpdateRequest req) {
        ScenarioEntity sc = ensureInProject(ctx, scenarioId);
        StepEntity st = ensureStep(sc, stepId);
        validateRefs(ctx, req.targetElementId(), req.dataId());
        StepValidator.validate(req.action(), req.targetElementId(), req.dataId(), req.literalValue());

        // Block IF ↔ non-IF action mutation. Switching would orphan
        // either the condition data or the child branches. Users must
        // delete and recreate explicitly.
        boolean wasIf = st.getAction() == StepAction.IF;
        boolean willIf = req.action() == StepAction.IF;
        if (wasIf != willIf) {
            throw ApiException.badRequest("cannot toggle a step in/out of IF; delete it and create the right type");
        }

        st.setAction(req.action());
        st.setTargetElementId(req.targetElementId());
        st.setDataId(req.dataId());
        st.setLiteralValue(req.literalValue());
        st.setExpectedResult(nullIfBlank(req.expectedResult()));
        if (req.timeoutMs() != null)        st.setTimeoutMs(req.timeoutMs());
        if (req.retryCount() != null)       st.setRetryCount(req.retryCount());
        if (req.screenshotAfter() != null)  st.setScreenshotAfter(req.screenshotAfter());
        if (willIf) {
            if (req.condition() == null) throw ApiException.badRequest("IF step requires a condition");
            validateCondition(req.condition());
            st.setConditionJson(writeJson(req.condition()));
        }

        scenarios.save(sc);
        return toFullView(sc);
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    @Transactional
    public ScenarioDtos.View deleteStep(JwtPrincipal caller, ProjectContext ctx, long scenarioId, long stepId) {
        ScenarioEntity sc = ensureInProject(ctx, scenarioId);
        StepEntity st = ensureStep(sc, stepId);
        int removed = st.getOrderIndex();
        Long parentId = st.getParentStepId();
        String branch = st.getBranchLabel();
        steps.delete(st);   // ON DELETE CASCADE handles IF children

        List<StepEntity> siblings = (parentId == null)
                ? steps.findRootStepsByScenarioId(sc.getId())
                : steps.findAllByParentStepIdAndBranchLabelOrderByOrderIndexAsc(parentId, branch);
        for (StepEntity s : siblings) {
            if (s.getOrderIndex() > removed) s.setOrderIndex(s.getOrderIndex() - 1);
        }

        scenarios.save(sc);
        return toFullView(sc);
    }

    @Transactional
    public ScenarioDtos.View reorderSteps(JwtPrincipal caller, ProjectContext ctx, long scenarioId, ScenarioDtos.ReorderRequest req) {
        // Scope-limited: all ids must live in the same branch (root or one
        // IF's "then"/"else"). Cross-branch DnD isn't supported in v1.
        ScenarioEntity sc = ensureInProject(ctx, scenarioId);
        if (req.stepIds().isEmpty()) return toFullView(sc);
        StepEntity first = ensureStep(sc, req.stepIds().get(0));
        Long parentId = first.getParentStepId();
        String branch = first.getBranchLabel();
        List<StepEntity> existing = (parentId == null)
                ? steps.findRootStepsByScenarioId(sc.getId())
                : steps.findAllByParentStepIdAndBranchLabelOrderByOrderIndexAsc(parentId, branch);
        if (existing.size() != req.stepIds().size()) {
            throw ApiException.badRequest("step id list size mismatch with target scope (expected " + existing.size() + ", got " + req.stepIds().size() + ")");
        }
        Map<Long, StepEntity> byId = existing.stream().collect(Collectors.toMap(StepEntity::getId, x -> x));
        for (int i = 0; i < req.stepIds().size(); i++) {
            StepEntity st = byId.get(req.stepIds().get(i));
            if (st == null) throw ApiException.badRequest("unknown step id (or wrong scope): " + req.stepIds().get(i));
            st.setOrderIndex(i);
        }
        scenarios.save(sc);
        return toFullView(sc);
    }

    /* ─────────────────────── condition helpers ────────────────────────── */

    private static void validateCondition(StepCondition c) {
        if (c.type() == null || c.operator() == null) {
            throw ApiException.badRequest("condition.type and condition.operator are required");
        }
        switch (c.type()) {
            case "element_state" -> {
                if (c.subjectId() == null) throw ApiException.badRequest("element_state condition needs subjectId (element)");
                switch (c.operator()) {
                    case "is_visible", "is_hidden", "exists" -> {}
                    case "text_contains", "text_equals" -> {
                        if (c.value() == null) throw ApiException.badRequest("text_* condition needs a value");
                    }
                    default -> throw ApiException.badRequest("unknown element_state operator: " + c.operator());
                }
            }
            case "test_data_compare" -> {
                if (c.subjectId() == null) throw ApiException.badRequest("test_data_compare needs subjectId (test data)");
                if (c.value() == null)     throw ApiException.badRequest("test_data_compare needs a value");
                switch (c.operator()) {
                    case "equals", "not_equals", "contains", "greater_than", "less_than" -> {}
                    default -> throw ApiException.badRequest("unknown test_data_compare operator: " + c.operator());
                }
            }
            default -> throw ApiException.badRequest("unknown condition type: " + c.type());
        }
    }

    private String writeJson(StepCondition c) {
        try { return json.writeValueAsString(c); }
        catch (JsonProcessingException e) { throw ApiException.badRequest("invalid condition JSON: " + e.getMessage()); }
    }

    private StepCondition readJson(String s) {
        if (s == null || s.isBlank()) return null;
        try { return json.readValue(s, StepCondition.class); }
        catch (JsonProcessingException e) { return null; }
    }

    /* ───────────────────────────── helpers ────────────────────────────────── */

    private void validateRefs(ProjectContext ctx, Long elementId, Long dataId) {
        if (elementId != null) {
            ElementEntity el = elements.findById(elementId).orElseThrow(() -> ApiException.notFound("element"));
            if (!ctx.projectId().equals(el.getProjectId())) throw ApiException.forbidden("cross-project element");
        }
        if (dataId != null) {
            TestDataEntity td = data.findById(dataId).orElseThrow(() -> ApiException.notFound("test data"));
            if (!ctx.projectId().equals(td.getProjectId())) throw ApiException.forbidden("cross-project test data");
        }
    }

    private ScenarioEntity ensureInProject(ProjectContext ctx, long id) {
        ScenarioEntity s = scenarios.findById(id).orElseThrow(() -> ApiException.notFound("scenario"));
        if (!ctx.projectId().equals(s.getProjectId())) throw ApiException.forbidden("scenario not in active project");
        return s;
    }

    private StepEntity ensureStep(ScenarioEntity sc, long stepId) {
        StepEntity st = steps.findById(stepId).orElseThrow(() -> ApiException.notFound("step"));
        if (!st.getScenarioId().equals(sc.getId())) throw ApiException.badRequest("step belongs to a different scenario");
        return st;
    }

    private static String[] toArray(List<String> tags) {
        if (tags == null) return new String[0];
        return tags.stream().filter(t -> t != null && !t.isBlank()).toArray(String[]::new);
    }

    /* ─────────────────────── projection / view builders ───────────────────── */

    private ScenarioDtos.View toFullView(ScenarioEntity sc) {
        // Pre-fetch every step + element + test data in the scenario in
        // three batches. Then walk the tree recursively from root steps
        // and build StepViews with populated children for IF rows.
        List<StepEntity> raw = steps.findAllByScenarioIdOrderByOrderIndexAsc(sc.getId());

        List<Long> elementIds = raw.stream().map(StepEntity::getTargetElementId).filter(java.util.Objects::nonNull).toList();
        List<Long> dataIds    = raw.stream().map(StepEntity::getDataId).filter(java.util.Objects::nonNull).toList();
        Map<Long, ElementEntity>  eMap = elementIds.isEmpty() ? Map.of()
                : elements.findAllById(elementIds).stream().collect(Collectors.toMap(ElementEntity::getId, x -> x));
        Map<Long, TestDataEntity> dMap = dataIds.isEmpty() ? Map.of()
                : data.findAllById(dataIds).stream().collect(Collectors.toMap(TestDataEntity::getId, x -> x));

        // Group steps by parentStepId so the recursive walker doesn't hit
        // the DB per IF (single pass O(n)).
        Map<Long, List<StepEntity>> byParent = new HashMap<>();
        for (StepEntity st : raw) {
            Long key = st.getParentStepId();
            byParent.computeIfAbsent(key, __ -> new ArrayList<>()).add(st);
        }
        for (List<StepEntity> bucket : byParent.values()) {
            bucket.sort((a, b) -> Integer.compare(a.getOrderIndex(), b.getOrderIndex()));
        }

        List<StepEntity> rootSteps = byParent.getOrDefault(null, List.of());
        List<ScenarioDtos.StepView> stepViews = new ArrayList<>(rootSteps.size());
        for (StepEntity st : rootSteps) {
            stepViews.add(buildStepView(st, eMap, dMap, byParent));
        }

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
        parentRefs.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));

        return new ScenarioDtos.View(
                sc.getId(), sc.getName(), sc.getDescription(),
                List.of(sc.getTags()), sc.getPreconditions(),
                sc.getVersion(), sc.getCreatedAt(), sc.getUpdatedAt(),
                stepViews,
                parentRefs
        );
    }

    /**
     * Builds one StepView and, when the step is an IF, recursively builds
     * its children (then-first, then else). All entity lookups are done
     * via the pre-built maps to keep this O(n) total.
     */
    private ScenarioDtos.StepView buildStepView(
            StepEntity st,
            Map<Long, ElementEntity> eMap,
            Map<Long, TestDataEntity> dMap,
            Map<Long, List<StepEntity>> byParent
    ) {
        ElementEntity el = st.getTargetElementId() != null ? eMap.get(st.getTargetElementId()) : null;
        TestDataEntity td = st.getDataId() != null ? dMap.get(st.getDataId()) : null;

        List<ScenarioDtos.StepView> children = List.of();
        if (st.getAction() == StepAction.IF) {
            List<StepEntity> kids = byParent.getOrDefault(st.getId(), List.of());
            // Order: all "then" children first (in their orderIndex), then
            // all "else" children. Each carries its own branchLabel so the
            // frontend can split them into two lanes.
            List<StepEntity> thenKids = new ArrayList<>();
            List<StepEntity> elseKids = new ArrayList<>();
            for (StepEntity k : kids) {
                if ("then".equals(k.getBranchLabel())) thenKids.add(k);
                else if ("else".equals(k.getBranchLabel())) elseKids.add(k);
            }
            children = new ArrayList<>(thenKids.size() + elseKids.size());
            for (StepEntity k : thenKids) children.add(buildStepView(k, eMap, dMap, byParent));
            for (StepEntity k : elseKids) children.add(buildStepView(k, eMap, dMap, byParent));
        }

        return new ScenarioDtos.StepView(
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
                st.getParentStepId(),
                st.getBranchLabel(),
                readJson(st.getConditionJson()),
                children,
                st.getCreatedAt()
        );
    }
}
