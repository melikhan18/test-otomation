package com.qaplatform.web.automation.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One step inside a {@link WebScenarioEntity}. Carries the Playwright
 * locator string (CSS / XPath / role= / text= syntax — all valid) and the
 * action's argument value inline; v1 has no element catalog, so the
 * scenario is fully self-contained.
 *
 * <p>{@code selector} interpretation by action:</p>
 * <ul>
 *   <li>Interaction / Wait — the Playwright locator to act on</li>
 *   <li>{@code GOTO} — ignored; {@code value} holds the URL</li>
 *   <li>{@code ASSERT_URL_*} / {@code ASSERT_TITLE_*} — ignored</li>
 *   <li>{@code SLEEP} — both ignored; {@code value} is the ms duration</li>
 * </ul>
 */
@Entity
@Table(name = "steps", schema = "web_automation")
public class WebStepEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_id", nullable = false) private Long scenarioId;
    @Column(name = "order_index", nullable = false) private int orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WebStepAction action;

    @Column(columnDefinition = "TEXT") private String selector;
    @Column(columnDefinition = "TEXT") private String value;

    /**
     * Optional catalog ref. When set, takes precedence over the literal
     * {@link #selector} — the executor uses the linked element's locator
     * stack (primary + fallbacks). ON DELETE SET NULL: deleting an element
     * leaves the step broken-but-visible so the user can repoint it.
     */
    @Column(name = "target_element_id") private Long targetElementId;

    /**
     * Optional catalog ref. When set, takes precedence over the literal
     * {@link #value} — the executor pulls the linked test-data row's
     * value, respecting the run's environment.
     */
    @Column(name = "data_id") private Long dataId;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs = 5000;

    @Column(name = "screenshot_after", nullable = false)
    private boolean screenshotAfter = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected WebStepEntity() {}

    public WebStepEntity(Long scenarioId, int orderIndex, WebStepAction action) {
        this.scenarioId = scenarioId;
        this.orderIndex = orderIndex;
        this.action = action;
    }

    public Long getId() { return id; }
    public Long getScenarioId() { return scenarioId; }
    public int getOrderIndex() { return orderIndex; }       public void setOrderIndex(int v) { this.orderIndex = v; }
    public WebStepAction getAction() { return action; }     public void setAction(WebStepAction v) { this.action = v; }
    public String getSelector() { return selector; }        public void setSelector(String v) { this.selector = v; }
    public String getValue() { return value; }              public void setValue(String v) { this.value = v; }
    public Long getTargetElementId() { return targetElementId; } public void setTargetElementId(Long v) { this.targetElementId = v; }
    public Long getDataId() { return dataId; }                   public void setDataId(Long v) { this.dataId = v; }
    public int getTimeoutMs() { return timeoutMs; }         public void setTimeoutMs(int v) { this.timeoutMs = v; }
    public boolean isScreenshotAfter() { return screenshotAfter; } public void setScreenshotAfter(boolean v) { this.screenshotAfter = v; }
    public Instant getCreatedAt() { return createdAt; }
}
