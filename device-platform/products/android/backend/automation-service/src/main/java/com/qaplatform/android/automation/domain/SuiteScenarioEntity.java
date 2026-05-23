package com.qaplatform.android.automation.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "suite_scenarios", schema = "android_automation")
@IdClass(SuiteScenarioEntity.Key.class)
public class SuiteScenarioEntity {

    @Id @Column(name = "suite_id")    private Long suiteId;
    @Id @Column(name = "scenario_id") private Long scenarioId;

    @Column(name = "order_index", nullable = false) private int orderIndex;

    protected SuiteScenarioEntity() {}

    public SuiteScenarioEntity(Long suiteId, Long scenarioId, int orderIndex) {
        this.suiteId = suiteId;
        this.scenarioId = scenarioId;
        this.orderIndex = orderIndex;
    }

    public Long getSuiteId()    { return suiteId; }
    public Long getScenarioId() { return scenarioId; }
    public int getOrderIndex()  { return orderIndex; }
    public void setOrderIndex(int v) { this.orderIndex = v; }

    public static class Key implements Serializable {
        private Long suiteId;
        private Long scenarioId;

        public Key() {}
        public Key(Long suiteId, Long scenarioId) { this.suiteId = suiteId; this.scenarioId = scenarioId; }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return Objects.equals(suiteId, k.suiteId) && Objects.equals(scenarioId, k.scenarioId);
        }
        @Override public int hashCode() { return Objects.hash(suiteId, scenarioId); }
    }
}
