package com.devicefarm.automation.service;

import com.devicefarm.automation.api.dto.WorkspaceDtos;
import com.devicefarm.automation.domain.*;
import com.devicefarm.automation.tenancy.ProjectContext;
import com.devicefarm.common.jwt.JwtPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class WorkspaceService {

    private final SuiteRepository suites;
    private final SuiteScenarioRepository links;
    private final ScenarioRepository scenarios;
    private final StepRepository steps;

    public WorkspaceService(SuiteRepository suites, SuiteScenarioRepository links,
                            ScenarioRepository scenarios, StepRepository steps) {
        this.suites = suites;
        this.links = links;
        this.scenarios = scenarios;
        this.steps = steps;
    }

    /**
     * Build the full Automation workspace tree in a single round-trip:
     *   1. fetch every suite + every scenario for the product
     *   2. fetch every suite↔scenario link (all suites at once)
     *   3. compute step counts once
     *   4. group scenarios by suite + compute orphans
     */
    @Transactional(readOnly = true)
    public WorkspaceDtos.Tree tree(JwtPrincipal caller, ProjectContext ctx) {
        List<SuiteEntity>    suiteList    = suites.findAllByProjectIdOrderByUpdatedAtDesc(ctx.projectId());
        List<ScenarioEntity> scenarioList = scenarios.findAllByProjectIdOrderByUpdatedAtDesc(ctx.projectId());
        Map<Long, ScenarioEntity> scenarioById = scenarioList.stream()
                .collect(Collectors.toMap(ScenarioEntity::getId, x -> x));

        // Step counts in one pass
        Map<Long, Integer> stepCounts = new HashMap<>(scenarioList.size());
        for (ScenarioEntity s : scenarioList) stepCounts.put(s.getId(), (int) steps.countByScenarioId(s.getId()));

        // Collect all links across all suites for this product, then group by suite
        Set<Long> linkedScenarioIds = new HashSet<>();
        Map<Long, List<SuiteScenarioEntity>> linksBySuite = new HashMap<>();
        for (SuiteEntity suite : suiteList) {
            List<SuiteScenarioEntity> bs = links.findAllBySuiteIdOrderByOrderIndexAsc(suite.getId());
            linksBySuite.put(suite.getId(), bs);
            for (SuiteScenarioEntity ln : bs) linkedScenarioIds.add(ln.getScenarioId());
        }

        // Build suite nodes
        List<WorkspaceDtos.SuiteNode> suiteNodes = new ArrayList<>(suiteList.size());
        for (SuiteEntity suite : suiteList) {
            List<SuiteScenarioEntity> bs = linksBySuite.getOrDefault(suite.getId(), List.of());
            List<WorkspaceDtos.ScenarioNode> children = new ArrayList<>(bs.size());
            for (SuiteScenarioEntity ln : bs) {
                ScenarioEntity sc = scenarioById.get(ln.getScenarioId());
                if (sc == null) continue; // orphan link, ignored
                children.add(node(sc, stepCounts, ln.getOrderIndex()));
            }
            suiteNodes.add(new WorkspaceDtos.SuiteNode(
                    suite.getId(), suite.getName(), suite.getDescription(),
                    List.of(suite.getTags()),
                    children.size(), suite.getUpdatedAt(), children
            ));
        }

        // Orphans = scenarios that aren't referenced by any suite
        List<WorkspaceDtos.ScenarioNode> orphans = scenarioList.stream()
                .filter(s -> !linkedScenarioIds.contains(s.getId()))
                .map(s -> node(s, stepCounts, null))
                .toList();

        return new WorkspaceDtos.Tree(suiteNodes, orphans, scenarioList.size(), suiteList.size());
    }

    private WorkspaceDtos.ScenarioNode node(ScenarioEntity s, Map<Long, Integer> stepCounts, Integer orderInSuite) {
        return new WorkspaceDtos.ScenarioNode(
                s.getId(), s.getName(), s.getDescription(),
                List.of(s.getTags()),
                stepCounts.getOrDefault(s.getId(), 0),
                s.getVersion(),
                s.getUpdatedAt(),
                orderInSuite
        );
    }
}
