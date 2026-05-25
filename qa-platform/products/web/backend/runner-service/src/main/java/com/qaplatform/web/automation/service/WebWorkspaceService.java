package com.qaplatform.web.automation.service;

import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.web.automation.api.dto.WebWorkspaceDtos;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the workspace tree for the WEB stack — single-shot composition
 * of the project's suites + scenarios + the link table.
 *
 * <p>Implementation deliberately keeps the per-row queries cheap (no
 * cross-join, no N+1 element/data lookups — those aren't shown in the
 * tree). For projects with hundreds of scenarios we'd switch to a
 * single JDBC query; for v1 the JPA path is fine.</p>
 */
@Service
public class WebWorkspaceService {

    private final WebSuiteRepository suites;
    private final WebSuiteScenarioRepository suiteScenarios;
    private final WebScenarioRepository scenarios;
    private final WebStepRepository steps;

    public WebWorkspaceService(WebSuiteRepository suites,
                               WebSuiteScenarioRepository suiteScenarios,
                               WebScenarioRepository scenarios,
                               WebStepRepository steps) {
        this.suites = suites;
        this.suiteScenarios = suiteScenarios;
        this.scenarios = scenarios;
        this.steps = steps;
    }

    @Transactional(readOnly = true)
    public WebWorkspaceDtos.Tree tree(JwtPrincipal caller, ProjectContext ctx) {
        List<WebScenarioEntity> allScenarios = scenarios.findAllByProjectIdOrderByUpdatedAtDesc(ctx.projectId());
        Map<Long, WebScenarioEntity> scenarioById = new HashMap<>();
        Map<Long, Integer> stepCountById = new HashMap<>();
        for (WebScenarioEntity sc : allScenarios) {
            scenarioById.put(sc.getId(), sc);
            stepCountById.put(sc.getId(), (int) steps.countByScenarioId(sc.getId()));
        }

        List<WebSuiteEntity> allSuites = suites.findAllByProjectIdOrderByUpdatedAtDesc(ctx.projectId());
        List<WebWorkspaceDtos.SuiteNode> suiteNodes = new ArrayList<>();
        Set<Long> attachedScenarioIds = new HashSet<>();

        for (WebSuiteEntity s : allSuites) {
            List<WebSuiteScenarioEntity> links = suiteScenarios.findAllBySuiteIdOrderByOrderIndexAsc(s.getId());
            List<WebWorkspaceDtos.ScenarioNode> children = new ArrayList<>();
            for (WebSuiteScenarioEntity link : links) {
                WebScenarioEntity sc = scenarioById.get(link.getScenarioId());
                if (sc == null) continue;  // suite_scenarios cascade should prevent this, defensive
                attachedScenarioIds.add(sc.getId());
                children.add(toScenarioNode(sc, stepCountById));
            }
            suiteNodes.add(new WebWorkspaceDtos.SuiteNode(
                    s.getId(), s.getName(), s.getDescription(),
                    List.of(s.getTags()),
                    s.getUpdatedAt().toString(),
                    children
            ));
        }

        List<WebWorkspaceDtos.ScenarioNode> orphans = new ArrayList<>();
        for (WebScenarioEntity sc : allScenarios) {
            if (!attachedScenarioIds.contains(sc.getId())) {
                orphans.add(toScenarioNode(sc, stepCountById));
            }
        }

        return new WebWorkspaceDtos.Tree(
                suiteNodes, orphans,
                allSuites.size(), allScenarios.size()
        );
    }

    private static WebWorkspaceDtos.ScenarioNode toScenarioNode(WebScenarioEntity sc, Map<Long, Integer> stepCounts) {
        return new WebWorkspaceDtos.ScenarioNode(
                sc.getId(), sc.getName(), sc.getDescription(), List.of(sc.getTags()),
                stepCounts.getOrDefault(sc.getId(), 0),
                sc.getVersion(),
                sc.getUpdatedAt().toString()
        );
    }
}
