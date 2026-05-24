package com.qaplatform.web.automation.service;

import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.web.automation.api.dto.WebScenarioDtos;
import com.qaplatform.web.automation.domain.WebScenarioEntity;
import com.qaplatform.web.automation.domain.WebScenarioRepository;
import com.qaplatform.web.automation.domain.WebStepEntity;
import com.qaplatform.web.automation.domain.WebStepRepository;
import com.qaplatform.web.automation.tenancy.ProjectContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scenario + step CRUD for the web stack. No suites in v1 (scenarios run
 * standalone); no element catalog (selectors are literal). Drag-reorder
 * supported via {@link #reorderSteps}.
 */
@Service
public class WebScenarioService {

    private final WebScenarioRepository scenarios;
    private final WebStepRepository steps;

    public WebScenarioService(WebScenarioRepository scenarios, WebStepRepository steps) {
        this.scenarios = scenarios;
        this.steps = steps;
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
        List<WebStepEntity> existing = steps.findAllByScenarioIdOrderByOrderIndexAsc(s.getId());
        int order = req.position() == null ? existing.size() : Math.max(0, Math.min(req.position(), existing.size()));
        // shift downstream steps if inserting in the middle
        for (int i = order; i < existing.size(); i++) {
            WebStepEntity sh = existing.get(i);
            sh.setOrderIndex(sh.getOrderIndex() + 1);
            steps.save(sh);
        }
        WebStepEntity step = new WebStepEntity(s.getId(), order, req.action());
        step.setSelector(req.selector());
        step.setValue(req.value());
        if (req.timeoutMs() != null) step.setTimeoutMs(req.timeoutMs());
        if (req.screenshotAfter() != null) step.setScreenshotAfter(req.screenshotAfter());
        WebStepEntity saved = steps.save(step);
        scenarios.save(s);  // bumps version via @PreUpdate
        return toStepView(saved);
    }

    @Transactional
    public WebScenarioDtos.StepView updateStep(JwtPrincipal caller, ProjectContext ctx, long scenarioId, long stepId,
                                                WebScenarioDtos.StepUpdateRequest req) {
        WebScenarioEntity s = ensureInProject(ctx, scenarioId);
        WebStepEntity step = ensureStepInScenario(s.getId(), stepId);
        step.setAction(req.action());
        step.setSelector(req.selector());
        step.setValue(req.value());
        if (req.timeoutMs() != null) step.setTimeoutMs(req.timeoutMs());
        if (req.screenshotAfter() != null) step.setScreenshotAfter(req.screenshotAfter());
        steps.save(step);
        scenarios.save(s);
        return toStepView(step);
    }

    @Transactional
    public void deleteStep(JwtPrincipal caller, ProjectContext ctx, long scenarioId, long stepId) {
        WebScenarioEntity s = ensureInProject(ctx, scenarioId);
        WebStepEntity step = ensureStepInScenario(s.getId(), stepId);
        int gone = step.getOrderIndex();
        steps.delete(step);
        // compact remaining indices
        List<WebStepEntity> rest = steps.findAllByScenarioIdOrderByOrderIndexAsc(s.getId());
        for (WebStepEntity r : rest) {
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
        WebScenarioEntity s = ensureInProject(ctx, scenarioId);
        List<WebStepEntity> existing = steps.findAllByScenarioIdOrderByOrderIndexAsc(s.getId());
        if (existing.size() != req.stepIds().size()) {
            throw ApiException.badRequest("reorder list size mismatch");
        }
        var byId = new java.util.HashMap<Long, WebStepEntity>();
        existing.forEach(st -> byId.put(st.getId(), st));
        for (int i = 0; i < req.stepIds().size(); i++) {
            WebStepEntity st = byId.get(req.stepIds().get(i));
            if (st == null) throw ApiException.badRequest("step " + req.stepIds().get(i) + " not in scenario");
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

    private static String[] toArray(List<String> tags) {
        if (tags == null) return new String[0];
        return tags.stream().filter(t -> t != null && !t.isBlank()).map(String::trim).distinct().toArray(String[]::new);
    }

    private WebScenarioDtos.View toView(WebScenarioEntity s) {
        List<WebScenarioDtos.StepView> stepViews = steps.findAllByScenarioIdOrderByOrderIndexAsc(s.getId())
                .stream().map(WebScenarioService::toStepView).toList();
        return new WebScenarioDtos.View(
                s.getId(), s.getName(), s.getDescription(),
                List.of(s.getTags()), s.getVersion(),
                s.getCreatedAt(), s.getUpdatedAt(),
                stepViews
        );
    }

    private static WebScenarioDtos.StepView toStepView(WebStepEntity st) {
        return new WebScenarioDtos.StepView(
                st.getId(), st.getScenarioId(), st.getOrderIndex(), st.getAction(),
                st.getSelector(), st.getValue(),
                st.getTimeoutMs(), st.isScreenshotAfter(),
                st.getCreatedAt()
        );
    }
}
