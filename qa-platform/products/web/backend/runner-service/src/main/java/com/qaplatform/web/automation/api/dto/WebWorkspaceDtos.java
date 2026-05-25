package com.qaplatform.web.automation.api.dto;

import java.util.List;

/**
 * Tree shape for the /api/web/workspace/tree endpoint.
 *
 * <p>Mirrors Android's WorkspaceTree (same UI layout consumes it). Each
 * suite carries its scenario children inline so the left-sidebar tree
 * renders in one round trip; {@code orphanScenarios} is the bucket of
 * scenarios not attached to any suite.</p>
 */
public class WebWorkspaceDtos {

    public record ScenarioNode(long id, String name, String description, List<String> tags,
                               int stepCount, int version, String updatedAt) {}

    public record SuiteNode(long id, String name, String description, List<String> tags,
                            String updatedAt, List<ScenarioNode> scenarios) {}

    public record Tree(List<SuiteNode> suites,
                       List<ScenarioNode> orphanScenarios,
                       int totalSuites,
                       int totalScenarios) {}
}
