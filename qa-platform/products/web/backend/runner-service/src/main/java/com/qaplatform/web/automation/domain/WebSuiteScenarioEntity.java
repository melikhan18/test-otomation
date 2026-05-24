package com.qaplatform.web.automation.domain;

import jakarta.persistence.*;

/**
 * Join row in the ordered M:N relationship suite → scenarios. Standalone
 * entity (not a {@code @ManyToMany}) so we can carry {@code order_index}
 * and reorder cheaply without rewriting the whole list.
 */
@Entity
@Table(name = "suite_scenarios", schema = "web_automation")
public class WebSuiteScenarioEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suite_id",    nullable = false) private Long suiteId;
    @Column(name = "scenario_id", nullable = false) private Long scenarioId;
    @Column(name = "order_index", nullable = false) private int orderIndex;

    protected WebSuiteScenarioEntity() {}

    public WebSuiteScenarioEntity(Long suiteId, Long scenarioId, int orderIndex) {
        this.suiteId = suiteId;
        this.scenarioId = scenarioId;
        this.orderIndex = orderIndex;
    }

    public Long getId() { return id; }
    public Long getSuiteId() { return suiteId; }
    public Long getScenarioId() { return scenarioId; }
    public int getOrderIndex() { return orderIndex; } public void setOrderIndex(int v) { this.orderIndex = v; }
}
