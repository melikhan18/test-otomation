package com.qaplatform.web.automation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.web.automation.api.dto.WebScenarioDtos;
import com.qaplatform.web.automation.domain.WebScenarioEntity;
import com.qaplatform.web.automation.domain.WebScenarioRepository;
import com.qaplatform.web.automation.domain.WebStepAction;
import com.qaplatform.web.automation.domain.WebStepCondition;
import com.qaplatform.web.automation.domain.WebStepEntity;
import com.qaplatform.web.automation.domain.WebStepRepository;
import com.qaplatform.web.automation.tenancy.ProjectContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scenario + step CRUD for the web stack. Steps form a tree: root-level
 * steps have null parent / branch; children of an {@link WebStepAction#IF}
 * carry the IF's id and a branch label ("then" / "else").
 *
 * <p>Most legacy scenarios are flat (no IFs) and behave exactly as before
 * — every step is rooted directly under the scenario.</p>
 */
@Service
public class WebScenarioService {

    private final WebScenarioRepository scenarios;
    private final WebStepRepository steps;
    private final ObjectMapper json;

    public WebScenarioService(WebScenarioRepository scenarios,
                              WebStepRepository steps,
                              ObjectMapper json) {
        this.scenarios = scenarios;
        this.steps = steps;
        this.json = json;
    }

    /* ─────────────────────────── scenarios ─────────────────────────────── */

    @Transactional
    public WebScenarioDtos.View create(JwtPrincipal caller, ProjectContext ctx, WebScenarioDtos.CreateRequest req) {
        WebScenarioEntity s = new WebScenarioEntity(ctx.projectId(), req.name().trim(), caller.userId());
        s.setDescription(req.description());
        s.setTags(toArray(req.tags()));
        return toView(scenarios.save(s));
    }

    @Transactional
    public WebScenarioDtos.View update(JwtPrincipal caller, ProjectContext ctx, long id, WebScenarioDtos.UpdateRequest req) {
        WebScenarioEntity s = ensureInProject(ctx, id);
        s.setName(req.name().trim());
        s.setDescription(req.description());
        s.setTags(toArray(req.tags()));
        return toView(s);
    }

    @Transactional
    public void delete(JwtPrincipal caller, ProjectContext ctx, long id) {
        WebScenarioEntity s = ensureInProject(ctx, id);
        scenarios.delete(s);   // steps cascade via FK
    }

    @Transactional(readOnly = true)
    public WebScenarioDtos.View get(JwtPrincipal caller, ProjectContext ctx, long id) {
        return toView(ensureInProject(ctx, id));
    }

    @Transactional(readOnly = true)
    public List<WebScenarioDtos.Summary> list(JwtPrincipal caller, ProjectContext ctx) {
        return scenarios.findAllByProjectIdOrderByUpdatedAtDesc(ctx.projectId()).stream()
                .map(s -> new WebScenarioDtos.Summary(
                        s.getId(), s.getName(), s.getDescription(),
                        List.of(s.getTags()), s.getVersion(),
                        (int) steps.countByScenarioId(s.getId()),
                        s.getCreatedAt(), s.getUpdatedAt()
                )).toList();
    }

    /* ───────────────────────────── steps ───────────────────────────────── */

    @Transactional
    public WebScenarioDtos.StepView addStep(JwtPrincipal caller, ProjectContext ctx, long scenarioId,
                                            WebScenarioDtos.StepCreateRequest req) {
        WebScenarioEntity s = ensureInProject(ctx, scenarioId);

        // Validate tree position: parent + branch must be coherent. Either
        // both null (root) or both set (child inside a branch).
        Long parentId = req.parentStepId();
        String branch = req.branchLabel();
        if ((parentId == null) != (branch == null)) {
            throw ApiException.badRequest("parentStepId and branchLabel must be set together (or both null for root)");
        }
        if (parentId != null) {
            WebStepEntity parent = ensureStepInScenario(s.getId(), parentId);
            if (parent.getAction() != WebStepAction.IF) {
                throw ApiException.badRequest("parent step must be an IF (was " + parent.getAction() + ")");
            }
        }

        // IF rows MUST carry a condition; non-IF rows MUST NOT.
        if (req.action() == WebStepAction.IF) {
            if (req.condition() == null) throw ApiException.badRequest("IF step requires a condition");
            validateCondition(req.condition());
        } else if (req.condition() != null) {
            throw ApiException.badRequest("condition is only valid for IF actions");
        }

        // Find the right sibling list (root or branch) and shift downstream
        // indices to make room at `position`.
        List<WebStepEntity> siblings = (parentId == null)
                ? steps.findRootStepsByScenarioId(s.getId())
                : steps.findAllByParentStepIdAndBranchLabelOrderByOrderIndexAsc(parentId, branch);
        int order = req.position() == null
                ? siblings.size()
                : Math.max(0, Math.min(req.position(), siblings.size()));
        for (int i = order; i < siblings.size(); i++) {
            WebStepEntity sh = siblings.get(i);
            sh.setOrderIndex(sh.getOrderIndex() + 1);
            steps.save(sh);
        }

        WebStepEntity step = new WebStepEntity(s.getId(), order, req.action());
        step.setSelector(req.selector());
        step.setValue(req.value());
        step.setTargetElementId(req.targetElementId());
        step.setDataId(req.dataId());
        if (req.timeoutMs() != null) step.setTimeoutMs(req.timeoutMs());
        if (req.screenshotAfter() != null) step.setScreenshotAfter(req.screenshotAfter());
        step.setParentStepId(parentId);
        step.setBranchLabel(branch);
        if (req.condition() != null) step.setConditionJson(writeJson(req.condition()));

        WebStepEntity saved = steps.save(step);
        scenarios.save(s);  // bumps version via @PreUpdate
        return toStepView(saved, List.of());  // freshly inserted IF starts childless
    }

    @Transactional
    public WebScenarioDtos.StepView updateStep(JwtPrincipal caller, ProjectContext ctx, long scenarioId, long stepId,
                                                WebScenarioDtos.StepUpdateRequest req) {
        WebScenarioEntity s = ensureInProject(ctx, scenarioId);
        WebStepEntity step = ensureStepInScenario(s.getId(), stepId);

        // Block action mutation across the IF / non-IF boundary — switching
        // a CLICK to an IF (or vice versa) would leave orphan condition
        // data or orphan children. Force users to delete + re-create.
        boolean wasIf  = step.getAction() == WebStepAction.IF;
        boolean willIf = req.action() == WebStepAction.IF;
        if (wasIf != willIf) {
            throw ApiException.badRequest("cannot toggle a step in/out of IF; delete it and create the right type");
        }

        step.setAction(req.action());
        step.setSelector(req.selector());
        step.setValue(req.value());
        step.setTargetElementId(req.targetElementId());
        step.setDataId(req.dataId());
        if (req.timeoutMs() != null) step.setTimeoutMs(req.timeoutMs());
        if (req.screenshotAfter() != null) step.setScreenshotAfter(req.screenshotAfter());
        if (willIf) {
            if (req.condition() == null) throw ApiException.badRequest("IF step requires a condition");
            validateCondition(req.condition());
            step.setConditionJson(writeJson(req.condition()));
        }
        steps.save(step);
        scenarios.save(s);
        return toStepView(step, loadChildren(step));
    }

    @Transactional
    public void deleteStep(JwtPrincipal caller, ProjectContext ctx, long scenarioId, long stepId) {
        WebScenarioEntity s = ensureInProject(ctx, scenarioId);
        WebStepEntity step = ensureStepInScenario(s.getId(), stepId);
        int gone = step.getOrderIndex();
        Long parentId = step.getParentStepId();
        String branch = step.getBranchLabel();
        steps.delete(step);   // ON DELETE CASCADE handles any IF children
        // compact remaining sibling indices in the same scope
        List<WebStepEntity> siblings = (parentId == null)
                ? steps.findRootStepsByScenarioId(s.getId())
                : steps.findAllByParentStepIdAndBranchLabelOrderByOrderIndexAsc(parentId, branch);
        for (WebStepEntity r : siblings) {
            if (r.getOrderIndex() > gone) {
                r.setOrderIndex(r.getOrderIndex() - 1);
                steps.save(r);
            }
        }
        scenarios.save(s);
    }

    @Transactional
    public void reorderSteps(JwtPrincipal caller, ProjectContext ctx, long scenarioId,
                             WebScenarioDtos.ReorderRequest req) {
        // Reorder is scope-limited: caller passes all sibling ids in one
        // branch (or all root siblings). Cross-branch moves aren't supported
        // here — they'd require a re-parent endpoint, which v1 doesn't need.
        WebScenarioEntity s = ensureInProject(ctx, scenarioId);
        if (req.stepIds().isEmpty()) return;
        WebStepEntity first = ensureStepInScenario(s.getId(), req.stepIds().get(0));
        Long parentId = first.getParentStepId();
        String branch = first.getBranchLabel();
        List<WebStepEntity> existing = (parentId == null)
                ? steps.findRootStepsByScenarioId(s.getId())
                : steps.findAllByParentStepIdAndBranchLabelOrderByOrderIndexAsc(parentId, branch);
        if (existing.size() != req.stepIds().size()) {
            throw ApiException.badRequest("reorder list size mismatch with target scope");
        }
        Map<Long, WebStepEntity> byId = new HashMap<>();
        existing.forEach(st -> byId.put(st.getId(), st));
        for (int i = 0; i < req.stepIds().size(); i++) {
            WebStepEntity st = byId.get(req.stepIds().get(i));
            if (st == null) throw ApiException.badRequest("step " + req.stepIds().get(i) + " not in this scope");
            st.setOrderIndex(i);
            steps.save(st);
        }
        scenarios.save(s);
    }

    /* ─────────────────────────── helpers ──────────────────────────────── */

    private WebScenarioEntity ensureInProject(ProjectContext ctx, long id) {
        WebScenarioEntity s = scenarios.findById(id).orElseThrow(() -> ApiException.notFound("scenario"));
        if (!ctx.projectId().equals(s.getProjectId())) throw ApiException.forbidden("scenario not in active project");
        return s;
    }

    private WebStepEntity ensureStepInScenario(long scenarioId, long stepId) {
        WebStepEntity step = steps.findById(stepId).orElseThrow(() -> ApiException.notFound("step"));
        if (!step.getScenarioId().equals(scenarioId)) throw ApiException.badRequest("step not in this scenario");
        return step;
    }

    private static void validateCondition(WebStepCondition c) {
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

    private String writeJson(WebStepCondition c) {
        try { return json.writeValueAsString(c); }
        catch (JsonProcessingException e) { throw ApiException.badRequest("invalid condition JSON: " + e.getMessage()); }
    }

    private WebStepCondition readJson(String s) {
        if (s == null || s.isBlank()) return null;
        try { return json.readValue(s, WebStepCondition.class); }
        catch (JsonProcessingException e) { return null; }
    }

    private static String[] toArray(List<String> tags) {
        if (tags == null) return new String[0];
        return tags.stream().filter(t -> t != null && !t.isBlank()).map(String::trim).distinct().toArray(String[]::new);
    }

    /* ─────────────────────────── view building ────────────────────────── */

    private WebScenarioDtos.View toView(WebScenarioEntity s) {
        List<WebStepEntity> roots = steps.findRootStepsByScenarioId(s.getId());
        List<WebScenarioDtos.StepView> stepViews = new ArrayList<>(roots.size());
        for (WebStepEntity r : roots) stepViews.add(toStepView(r, loadChildren(r)));
        return new WebScenarioDtos.View(
                s.getId(), s.getName(), s.getDescription(),
                List.of(s.getTags()), s.getVersion(),
                s.getCreatedAt(), s.getUpdatedAt(),
                stepViews
        );
    }

    /**
     * Recursive child loader. For IF steps fetches both "then" + "else"
     * branch members, concatenates them (then-first), and recurses into
     * any nested IFs. Non-IF steps return an empty child list cheaply.
     *
     * <p>This is O(N) DB queries where N is the number of IFs in the
     * scenario. For v1 — handful of IFs per scenario — that's fine. If
     * profiling shows it later we can swap to a single recursive CTE.</p>
     */
    private List<WebScenarioDtos.StepView> loadChildren(WebStepEntity parent) {
        if (parent.getAction() != WebStepAction.IF) return List.of();
        List<WebStepEntity> thenKids = steps.findAllByParentStepIdAndBranchLabelOrderByOrderIndexAsc(parent.getId(), "then");
        List<WebStepEntity> elseKids = steps.findAllByParentStepIdAndBranchLabelOrderByOrderIndexAsc(parent.getId(), "else");
        List<WebScenarioDtos.StepView> out = new ArrayList<>(thenKids.size() + elseKids.size());
        for (WebStepEntity k : thenKids) out.add(toStepView(k, loadChildren(k)));
        for (WebStepEntity k : elseKids) out.add(toStepView(k, loadChildren(k)));
        return out;
    }

    private WebScenarioDtos.StepView toStepView(WebStepEntity st, List<WebScenarioDtos.StepView> children) {
        return new WebScenarioDtos.StepView(
                st.getId(), st.getScenarioId(), st.getOrderIndex(), st.getAction(),
                st.getSelector(), st.getValue(),
                st.getTargetElementId(), st.getDataId(),
                st.getTimeoutMs(), st.isScreenshotAfter(),
                st.getParentStepId(), st.getBranchLabel(),
                readJson(st.getConditionJson()),
                children,
                st.getCreatedAt()
        );
    }
}
