import { api } from "./api";
import type { RunStatus, StepResultStatus, SuiteRunStatus } from "./automation";

/* ──────────────────────────  Browser catalog  ────────────────────────── */

export type BrowserProfile = {
  id: string;
  displayName: string;
  engine: "chromium" | "firefox" | "webkit";
  width: number;
  height: number;
  deviceScaleFactor: number;
  isMobile: boolean;
  userAgent: string | null;
  locale: string;
  timezone: string;
};

export const browserApi = {
  list: () => api.get<BrowserProfile[]>("/api/browsers").then((r) => r.data),
};

/* ──────────────────────────  Step DSL  ──────────────────────────────── */

/**
 * Web platform's step action enum — mirrors the backend `WebStepAction` in
 * `products/web/backend/runner-service/.../domain/WebStepAction.java`.
 * Deliberately different from Android's flat list: web steps need URL +
 * selector + value, not element-id + data-id + literal.
 */
export type WebStepAction =
  | "GOTO" | "RELOAD" | "GO_BACK" | "GO_FORWARD"
  | "CLICK" | "DBL_CLICK" | "FILL" | "PRESS_KEY"
  | "CHECK" | "UNCHECK" | "SELECT" | "HOVER"
  | "WAIT_FOR_SELECTOR" | "WAIT_FOR_LOAD_STATE" | "SLEEP"
  | "ASSERT_VISIBLE" | "ASSERT_HIDDEN"
  | "ASSERT_TEXT_EQUALS" | "ASSERT_TEXT_CONTAINS"
  | "ASSERT_URL_EQUALS" | "ASSERT_URL_CONTAINS"
  | "ASSERT_TITLE_EQUALS" | "ASSERT_TITLE_CONTAINS"
  | "ASSERT_ATTRIBUTE"
  | "SCREENSHOT" | "COMMENT" | "EVAL_JS";

/** Per-action UI metadata — same shape as Android's StepActionDef. */
export type WebStepActionDef = {
  key: WebStepAction;
  label: string;
  category: "navigation" | "interaction" | "wait" | "assert" | "util";
  /** Does this action need a selector string? */
  needsSelector: boolean;
  /** Does this action need a value string? (URL, text to type, expected match, etc.) */
  needsValue: boolean;
  /** Placeholder shown in the value input — points the user at the expected shape. */
  valueLabel?: string;
  tone: "blue" | "green" | "amber" | "violet" | "gray";
};

export const WEB_STEP_ACTIONS: WebStepActionDef[] = [
  /* ── Navigation ──────────────────────────────────────────────────── */
  { key: "GOTO",        label: "Go to URL",     category: "navigation", needsSelector: false, needsValue: true,  valueLabel: "https://example.com",                tone: "blue" },
  { key: "RELOAD",      label: "Reload",        category: "navigation", needsSelector: false, needsValue: false,                                                  tone: "blue" },
  { key: "GO_BACK",     label: "Go back",       category: "navigation", needsSelector: false, needsValue: false,                                                  tone: "blue" },
  { key: "GO_FORWARD",  label: "Go forward",    category: "navigation", needsSelector: false, needsValue: false,                                                  tone: "blue" },

  /* ── Interaction ─────────────────────────────────────────────────── */
  { key: "CLICK",       label: "Click",         category: "interaction", needsSelector: true,  needsValue: false,                                                 tone: "green" },
  { key: "DBL_CLICK",   label: "Double click",  category: "interaction", needsSelector: true,  needsValue: false,                                                 tone: "green" },
  { key: "FILL",        label: "Fill",          category: "interaction", needsSelector: true,  needsValue: true,  valueLabel: "text to type",                    tone: "green" },
  { key: "PRESS_KEY",   label: "Press key",     category: "interaction", needsSelector: false, needsValue: true,  valueLabel: "Enter | Escape | Tab | …",         tone: "green" },
  { key: "CHECK",       label: "Check",         category: "interaction", needsSelector: true,  needsValue: false,                                                 tone: "green" },
  { key: "UNCHECK",     label: "Uncheck",       category: "interaction", needsSelector: true,  needsValue: false,                                                 tone: "green" },
  { key: "SELECT",      label: "Select option", category: "interaction", needsSelector: true,  needsValue: true,  valueLabel: "option value",                    tone: "green" },
  { key: "HOVER",       label: "Hover",         category: "interaction", needsSelector: true,  needsValue: false,                                                 tone: "green" },

  /* ── Wait ────────────────────────────────────────────────────────── */
  { key: "WAIT_FOR_SELECTOR",   label: "Wait for selector",   category: "wait", needsSelector: true,  needsValue: false,                                          tone: "amber" },
  { key: "WAIT_FOR_LOAD_STATE", label: "Wait for load state", category: "wait", needsSelector: false, needsValue: true,  valueLabel: "load | domcontentloaded | networkidle", tone: "amber" },
  { key: "SLEEP",               label: "Sleep",               category: "wait", needsSelector: false, needsValue: true,  valueLabel: "milliseconds",              tone: "amber" },

  /* ── Assert ──────────────────────────────────────────────────────── */
  { key: "ASSERT_VISIBLE",        label: "Verify visible",         category: "assert", needsSelector: true,  needsValue: false,                                  tone: "violet" },
  { key: "ASSERT_HIDDEN",         label: "Verify hidden",          category: "assert", needsSelector: true,  needsValue: false,                                  tone: "violet" },
  { key: "ASSERT_TEXT_EQUALS",    label: "Verify text equals",     category: "assert", needsSelector: true,  needsValue: true,  valueLabel: "expected text",     tone: "violet" },
  { key: "ASSERT_TEXT_CONTAINS",  label: "Verify text contains",   category: "assert", needsSelector: true,  needsValue: true,  valueLabel: "substring",          tone: "violet" },
  { key: "ASSERT_URL_EQUALS",     label: "Verify URL equals",      category: "assert", needsSelector: false, needsValue: true,  valueLabel: "exact URL",         tone: "violet" },
  { key: "ASSERT_URL_CONTAINS",   label: "Verify URL contains",    category: "assert", needsSelector: false, needsValue: true,  valueLabel: "substring",          tone: "violet" },
  { key: "ASSERT_TITLE_EQUALS",   label: "Verify title equals",    category: "assert", needsSelector: false, needsValue: true,  valueLabel: "exact title",       tone: "violet" },
  { key: "ASSERT_TITLE_CONTAINS", label: "Verify title contains",  category: "assert", needsSelector: false, needsValue: true,  valueLabel: "substring",          tone: "violet" },
  { key: "ASSERT_ATTRIBUTE",      label: "Verify attribute",       category: "assert", needsSelector: true,  needsValue: true,  valueLabel: "name=value",         tone: "violet" },

  /* ── Util ────────────────────────────────────────────────────────── */
  { key: "SCREENSHOT", label: "Screenshot", category: "util", needsSelector: false, needsValue: false,                                                            tone: "gray" },
  { key: "COMMENT",    label: "Comment",    category: "util", needsSelector: false, needsValue: true,  valueLabel: "free-form note",                             tone: "gray" },
  { key: "EVAL_JS",    label: "Eval JS",    category: "util", needsSelector: false, needsValue: true,  valueLabel: "JavaScript expression",                      tone: "gray" },
];

export const WEB_STEP_ACTION_MAP: Record<WebStepAction, WebStepActionDef> = Object.fromEntries(
  WEB_STEP_ACTIONS.map((d) => [d.key, d]),
) as Record<WebStepAction, WebStepActionDef>;

/* ──────────────────────────  Scenarios  ─────────────────────────────── */

export type WebStepView = {
  id: number;
  scenarioId: number;
  orderIndex: number;
  action: WebStepAction;
  selector: string | null;
  value: string | null;
  /** Catalog ref — takes precedence over selector when set. */
  targetElementId: number | null;
  /** Catalog ref — takes precedence over value when set. */
  dataId: number | null;
  timeoutMs: number;
  screenshotAfter: boolean;
  createdAt: string;
};

export type WebScenarioSummary = {
  id: number;
  name: string;
  description: string | null;
  tags: string[];
  version: number;
  stepCount: number;
  createdAt: string;
  updatedAt: string;
};

export type WebScenarioView = {
  id: number;
  name: string;
  description: string | null;
  tags: string[];
  version: number;
  createdAt: string;
  updatedAt: string;
  steps: WebStepView[];
};

export type WebScenarioCreate = { name: string; description?: string | null; tags?: string[] };
export type WebScenarioUpdate = WebScenarioCreate;

export type WebStepCreate = {
  action: WebStepAction;
  selector?: string | null;
  value?: string | null;
  /** Catalog reference — takes precedence over `selector` when set. */
  targetElementId?: number | null;
  /** Catalog reference — takes precedence over `value` when set. */
  dataId?: number | null;
  timeoutMs?: number | null;
  screenshotAfter?: boolean | null;
  /** Insertion index (0-based). null = append at end. */
  position?: number | null;
};
export type WebStepUpdate = Omit<WebStepCreate, "position">;

export const webScenarioApi = {
  list: () => api.get<WebScenarioSummary[]>("/api/scenarios").then((r) => r.data),
  get:  (id: number) => api.get<WebScenarioView>(`/api/scenarios/${id}`).then((r) => r.data),
  create: (body: WebScenarioCreate) => api.post<WebScenarioView>("/api/scenarios", body).then((r) => r.data),
  update: (id: number, body: WebScenarioUpdate) => api.put<WebScenarioView>(`/api/scenarios/${id}`, body).then((r) => r.data),
  delete: (id: number) => api.delete<void>(`/api/scenarios/${id}`).then((r) => r.data),

  addStep:    (sid: number, body: WebStepCreate) => api.post<WebStepView>(`/api/scenarios/${sid}/steps`, body).then((r) => r.data),
  updateStep: (sid: number, stepId: number, body: WebStepUpdate) =>
    api.put<WebStepView>(`/api/scenarios/${sid}/steps/${stepId}`, body).then((r) => r.data),
  deleteStep: (sid: number, stepId: number) =>
    api.delete<void>(`/api/scenarios/${sid}/steps/${stepId}`).then((r) => r.data),
  reorderSteps: (sid: number, stepIds: number[]) =>
    api.post<void>(`/api/scenarios/${sid}/steps/reorder`, { stepIds }).then((r) => r.data),
};

/* ──────────────────────────  Runs  ──────────────────────────────────── */

export type WebStepResultView = {
  id: number;
  stepId: number | null;
  orderIndex: number;
  action: WebStepAction;
  status: StepResultStatus;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  errorMessage: string | null;
  screenshotUrl: string | null;
};

export type WebRunSummary = {
  id: number;
  scenarioId: number | null;
  scenarioName: string | null;
  browserProfileId: string;
  environment: string;
  status: RunStatus;
  totalSteps: number;
  passedSteps: number;
  failedSteps: number;
  durationMs: number | null;
  videoUrl: string | null;
  traceUrl: string | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
};

export type WebRunView = {
  id: number;
  scenarioId: number | null;
  scenarioName: string | null;
  scenarioVersion: number | null;
  browserProfileId: string;
  environment: string;
  status: RunStatus;
  triggeredByUserId: number;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  totalSteps: number;
  passedSteps: number;
  failedSteps: number;
  errorSummary: string | null;
  videoUrl: string | null;
  traceUrl: string | null;
  createdAt: string;
  stepResults: WebStepResultView[];
};

export type WebRunCreate = {
  scenarioId: number;
  browserProfileId: string;
  environment?: string;
};

export const webRunApi = {
  list:   (scenarioId?: number) =>
    api.get<WebRunSummary[]>("/api/runs", { params: scenarioId ? { scenarioId } : {} }).then((r) => r.data),
  get:    (id: number) => api.get<WebRunView>(`/api/runs/${id}`).then((r) => r.data),
  create: (body: WebRunCreate) => api.post<WebRunView>("/api/runs", body).then((r) => r.data),
};

/* ──────────────────────────  Element catalog  ───────────────────────── */

export type WebLocatorStrategy = "CSS" | "XPATH" | "ROLE" | "TEXT" | "TEST_ID";

export type WebLocator = { strategy: WebLocatorStrategy; value: string };

export type WebElementView = {
  id: number;
  name: string;
  description: string | null;
  primaryStrategy: WebLocatorStrategy;
  primaryValue: string;
  fallbackLocators: WebLocator[];
  createdByUserId: number;
  createdAt: string;
  updatedAt: string;
};

export type WebElementCreate = {
  name: string;
  description?: string | null;
  primaryStrategy: WebLocatorStrategy;
  primaryValue: string;
  fallbackLocators?: WebLocator[];
};
export type WebElementUpdate = WebElementCreate;

export const webElementApi = {
  list:   () => api.get<WebElementView[]>("/api/elements").then((r) => r.data),
  get:    (id: number) => api.get<WebElementView>(`/api/elements/${id}`).then((r) => r.data),
  create: (body: WebElementCreate) => api.post<WebElementView>("/api/elements", body).then((r) => r.data),
  update: (id: number, body: WebElementUpdate) =>
    api.put<WebElementView>(`/api/elements/${id}`, body).then((r) => r.data),
  delete: (id: number) => api.delete<void>(`/api/elements/${id}`).then((r) => r.data),
};

/* ──────────────────────────  Test data  ─────────────────────────────── */

export type WebTestDataView = {
  id: number;
  name: string;
  environment: string;
  value: string;       // "••••••••" when sensitive && !masked override
  description: string | null;
  sensitive: boolean;
  masked: boolean;
  createdByUserId: number;
  createdAt: string;
  updatedAt: string;
};

export type WebTestDataCreate = {
  name: string;
  environment: string;
  value: string;
  description?: string | null;
  sensitive?: boolean;
};
export type WebTestDataUpdate = WebTestDataCreate;

export const webTestDataApi = {
  list:   () => api.get<WebTestDataView[]>("/api/test-data").then((r) => r.data),
  get:    (id: number, reveal = false) =>
    api.get<WebTestDataView>(`/api/test-data/${id}`, { params: { reveal } }).then((r) => r.data),
  create: (body: WebTestDataCreate) => api.post<WebTestDataView>("/api/test-data", body).then((r) => r.data),
  update: (id: number, body: WebTestDataUpdate) =>
    api.put<WebTestDataView>(`/api/test-data/${id}`, body).then((r) => r.data),
  delete: (id: number) => api.delete<void>(`/api/test-data/${id}`).then((r) => r.data),
  environments: () => api.get<string[]>("/api/test-data/environments").then((r) => r.data),
};

/* ──────────────────────────  Suites  ────────────────────────────────── */

export type WebSuiteScenarioRef = {
  scenarioId: number;
  name: string;
  description: string | null;
  tags: string[];
  stepCount: number;
  orderIndex: number;
};

export type WebSuiteSummary = {
  id: number;
  name: string;
  description: string | null;
  tags: string[];
  scenarioCount: number;
  createdAt: string;
  updatedAt: string;
};

export type WebSuiteView = {
  id: number;
  name: string;
  description: string | null;
  tags: string[];
  createdAt: string;
  updatedAt: string;
  scenarios: WebSuiteScenarioRef[];
};

export type WebSuiteCreate = { name: string; description?: string | null; tags?: string[] };
export type WebSuiteUpdate = WebSuiteCreate;

export const webSuiteApi = {
  list:   () => api.get<WebSuiteSummary[]>("/api/suites").then((r) => r.data),
  get:    (id: number) => api.get<WebSuiteView>(`/api/suites/${id}`).then((r) => r.data),
  create: (body: WebSuiteCreate) => api.post<WebSuiteView>("/api/suites", body).then((r) => r.data),
  update: (id: number, body: WebSuiteUpdate) => api.put<WebSuiteView>(`/api/suites/${id}`, body).then((r) => r.data),
  delete: (id: number) => api.delete<void>(`/api/suites/${id}`).then((r) => r.data),

  addScenario: (suiteId: number, scenarioId: number) =>
    api.post<WebSuiteView>(`/api/suites/${suiteId}/scenarios`, { scenarioId }).then((r) => r.data),
  removeScenario: (suiteId: number, scenarioId: number) =>
    api.delete<void>(`/api/suites/${suiteId}/scenarios/${scenarioId}`).then((r) => r.data),
  reorderScenarios: (suiteId: number, scenarioIds: number[]) =>
    api.post<void>(`/api/suites/${suiteId}/scenarios/reorder`, { scenarioIds }).then((r) => r.data),
};

/* ──────────────────────────  Suite runs  ────────────────────────────── */

export type WebSuiteRunChild = {
  id: number;
  scenarioId: number | null;
  scenarioName: string | null;
  status: RunStatus;
  totalSteps: number;
  passedSteps: number;
  failedSteps: number;
  durationMs: number | null;
  videoUrl: string | null;
  traceUrl: string | null;
  startedAt: string | null;
  finishedAt: string | null;
};

export type WebSuiteRunSummary = {
  id: number;
  suiteId: number;
  suiteName: string | null;
  browserProfileId: string;
  environment: string;
  status: SuiteRunStatus;
  totalScenarios: number;
  passedScenarios: number;
  failedScenarios: number;
  durationMs: number | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
};

export type WebSuiteRunView = {
  id: number;
  suiteId: number;
  suiteName: string | null;
  browserProfileId: string;
  environment: string;
  status: SuiteRunStatus;
  triggeredByUserId: number;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  totalScenarios: number;
  passedScenarios: number;
  failedScenarios: number;
  errorSummary: string | null;
  createdAt: string;
  runs: WebSuiteRunChild[];
};

export type WebSuiteRunCreate = {
  suiteId: number;
  browserProfileId: string;
  environment?: string;
};

export const webSuiteRunApi = {
  list:   (suiteId?: number) =>
    api.get<WebSuiteRunSummary[]>("/api/suite-runs", { params: suiteId ? { suiteId } : {} }).then((r) => r.data),
  get:    (id: number) => api.get<WebSuiteRunView>(`/api/suite-runs/${id}`).then((r) => r.data),
  create: (body: WebSuiteRunCreate) => api.post<WebSuiteRunView>("/api/suite-runs", body).then((r) => r.data),
};
