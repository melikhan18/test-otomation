package com.devicefarm.automation.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Combined hierarchy payload for the unified Automation workspace UI.
 * One request returns the full sidebar tree — suites with their ordered scenarios + any
 * scenario that isn't linked to a suite.
 */
public class WorkspaceDtos {

    public record Tree(
            List<SuiteNode> suites,
            List<ScenarioNode> orphanScenarios,
            int totalScenarios,
            int totalSuites
    ) {}

    public record SuiteNode(
            long id,
            String name,
            String description,
            List<String> tags,
            int scenarioCount,
            Instant updatedAt,
            List<ScenarioNode> scenarios
    ) {}

    /** Same shape used inside a suite or in the orphan list. */
    public record ScenarioNode(
            long id,
            String name,
            String description,
            List<String> tags,
            int stepCount,
            int version,
            Instant updatedAt,
            /** Position inside the parent suite — null when this node is in the orphan list. */
            Integer orderIndexInSuite
    ) {}
}
