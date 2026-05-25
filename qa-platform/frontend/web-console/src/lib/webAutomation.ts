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
  /** One-line "what does this do" tooltip shown in the Action picker. */
  description: string;
  iconName: WebStepActionIconName;
};

export type WebStepActionIconName =
  | "Globe" | "RotateCw" | "ArrowLeft" | "ArrowRight"
  | "MousePointerClick" | "MousePointer2" | "Keyboard" | "KeyRound"
  | "CheckSquare" | "Square" | "ListChecks" | "Hand"
  | "Hourglass" | "Loader" | "Clock"
  | "Eye" | "EyeOff" | "Equal" | "TextSearch" | "Link" | "Heading1" | "Hash"
  | "Camera" | "MessageSquare" | "Code2";

export const WEB_STEP_ACTIONS: WebStepActionDef[] = [
  /* ── Navigation ──────────────────────────────────────────────────── */
  { key: "GOTO",        label: "Go to URL",     category: "navigation", needsSelector: false, needsValue: true,  valueLabel: "https://example.com", tone: "blue", iconName: "Globe",       description: "Navigate the browser to the given URL." },
  { key: "RELOAD",      label: "Reload",        category: "navigation", needsSelector: false, needsValue: false,                                    tone: "blue", iconName: "RotateCw",    description: "Reload the current page." },
  { key: "GO_BACK",     label: "Go back",       category: "navigation", needsSelector: false, needsValue: false,                                    tone: "blue", iconName: "ArrowLeft",   description: "Go to the previous page in browser history." },
  { key: "GO_FORWARD",  label: "Go forward",    category: "navigation", needsSelector: false, needsValue: false,                                    tone: "blue", iconName: "ArrowRight",  description: "Go to the next page in browser history." },

  /* ── Interaction ─────────────────────────────────────────────────── */
  { key: "CLICK",       label: "Click",         category: "interaction", needsSelector: true,  needsValue: false,                                              tone: "green", iconName: "MousePointerClick", description: "Click the element matched by the selector." },
  { key: "DBL_CLICK",   label: "Double click",  category: "interaction", needsSelector: true,  needsValue: false,                                              tone: "green", iconName: "MousePointer2",    description: "Double-click the element." },
  { key: "FILL",        label: "Fill",          category: "interaction", needsSelector: true,  needsValue: true,  valueLabel: "text to type",                  tone: "green", iconName: "Keyboard",          description: "Type the given text into an input. Clears existing content first." },
  { key: "PRESS_KEY",   label: "Press key",     category: "interaction", needsSelector: false, needsValue: true,  valueLabel: "Enter | Escape | Tab | …",       tone: "green", iconName: "KeyRound",          description: "Send a keyboard key (Enter, Escape, Tab, ArrowDown, …)." },
  { key: "CHECK",       label: "Check",         category: "interaction", needsSelector: true,  needsValue: false,                                              tone: "green", iconName: "CheckSquare",       description: "Check the targeted checkbox / radio (idempotent)." },
  { key: "UNCHECK",     label: "Uncheck",       category: "interaction", needsSelector: true,  needsValue: false,                                              tone: "green", iconName: "Square",            description: "Uncheck the targeted checkbox (idempotent)." },
  { key: "SELECT",      label: "Select option", category: "interaction", needsSelector: true,  needsValue: true,  valueLabel: "option value",                  tone: "green", iconName: "ListChecks",        description: "Pick an option in a <select> element by value." },
  { key: "HOVER",       label: "Hover",         category: "interaction", needsSelector: true,  needsValue: false,                                              tone: "green", iconName: "Hand",              description: "Move the pointer over the element (triggers :hover styles)." },

  /* ── Wait ────────────────────────────────────────────────────────── */
  { key: "WAIT_FOR_SELECTOR",   label: "Wait for selector",   category: "wait", needsSelector: true,  needsValue: false,                                                                                tone: "amber", iconName: "Hourglass", description: "Block until the selector becomes visible, or fail after the step timeout." },
  { key: "WAIT_FOR_LOAD_STATE", label: "Wait for load state", category: "wait", needsSelector: false, needsValue: true,  valueLabel: "load | domcontentloaded | networkidle",                          tone: "amber", iconName: "Loader",    description: "Block until the page reaches the given load state." },
  { key: "SLEEP",               label: "Sleep",               category: "wait", needsSelector: false, needsValue: true,  valueLabel: "milliseconds",                                                    tone: "amber", iconName: "Clock",     description: "Pause for a fixed duration. Prefer Wait actions when possible." },

  /* ── Assert ──────────────────────────────────────────────────────── */
  { key: "ASSERT_VISIBLE",        label: "Verify visible",         category: "assert", needsSelector: true,  needsValue: false,                                                tone: "violet", iconName: "Eye",        description: "Fail unless the matched element is visible." },
  { key: "ASSERT_HIDDEN",         label: "Verify hidden",          category: "assert", needsSelector: true,  needsValue: false,                                                tone: "violet", iconName: "EyeOff",     description: "Fail if the matched element is currently visible." },
  { key: "ASSERT_TEXT_EQUALS",    label: "Verify text equals",     category: "assert", needsSelector: true,  needsValue: true,  valueLabel: "expected text",                   tone: "violet", iconName: "Equal",      description: "Fail unless the element's text equals the expected value exactly." },
  { key: "ASSERT_TEXT_CONTAINS",  label: "Verify text contains",   category: "assert", needsSelector: true,  needsValue: true,  valueLabel: "substring",                       tone: "violet", iconName: "TextSearch", description: "Fail unless the element's text contains the expected substring." },
  { key: "ASSERT_URL_EQUALS",     label: "Verify URL equals",      category: "assert", needsSelector: false, needsValue: true,  valueLabel: "exact URL",                       tone: "violet", iconName: "Link",       description: "Fail unless the current page URL equals the expected value." },
  { key: "ASSERT_URL_CONTAINS",   label: "Verify URL contains",    category: "assert", needsSelector: false, needsValue: true,  valueLabel: "substring",                       tone: "violet", iconName: "Link",       description: "Fail unless the URL contains the expected substring (handy for /dashboard etc.)." },
  { key: "ASSERT_TITLE_EQUALS",   label: "Verify title equals",    category: "assert", needsSelector: false, needsValue: true,  valueLabel: "exact title",                     tone: "violet", iconName: "Heading1",   description: "Fail unless the page title equals the expected value." },
  { key: "ASSERT_TITLE_CONTAINS", label: "Verify title contains",  category: "assert", needsSelector: false, needsValue: true,  valueLabel: "substring",                       tone: "violet", iconName: "Heading1",   description: "Fail unless the page title contains the expected substring." },
  { key: "ASSERT_ATTRIBUTE",      label: "Verify attribute",       category: "assert", needsSelector: true,  needsValue: true,  valueLabel: "name=value",                      tone: "violet", iconName: "Hash",       description: "Fail unless an attribute on the matched element equals the expected value." },

  /* ── Util ────────────────────────────────────────────────────────── */
  { key: "SCREENSHOT", label: "Screenshot", category: "util", needsSelector: false, needsValue: false,                                              tone: "gray", iconName: "Camera",       description: "Capture a PNG of the current page and attach it to the run." },
  { key: "COMMENT",    label: "Comment",    category: "util", needsSelector: false, needsValue: true,  valueLabel: "free-form note",               tone: "gray", iconName: "MessageSquare", description: "Inline note — no side effect, just documentation in the run." },
  { key: "EVAL_JS",    label: "Eval JS",    category: "util", needsSelector: false, needsValue: true,  valueLabel: "JavaScript expression",        tone: "gray", iconName: "Code2",         description: "Execute a JavaScript expression in the page context." },
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
  // Backend exposes /api/scenarios/{id}/steps/reorder, but the WEB workspace
  // ships without drag-and-drop reorder — users splice steps via the
  // insert-here lines / position parameter on addStep instead. Keeping this
  // method around just so callers can find it is just dead surface area.
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

/* ──────────────────────────  Workspace tree  ────────────────────────── */

export type WebWorkspaceScenarioNode = {
  id: number;
  name: string;
  description: string | null;
  tags: string[];
  stepCount: number;
  version: number;
  updatedAt: string;
};

export type WebWorkspaceSuiteNode = {
  id: number;
  name: string;
  description: string | null;
  tags: string[];
  updatedAt: string;
  scenarios: WebWorkspaceScenarioNode[];
};

export type WebWorkspaceTree = {
  suites: WebWorkspaceSuiteNode[];
  orphanScenarios: WebWorkspaceScenarioNode[];
  totalSuites: number;
  totalScenarios: number;
};

export const webWorkspaceApi = {
  tree: () => api.get<WebWorkspaceTree>("/api/workspace/tree").then((r) => r.data),
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
  // Backend exposes /api/suites/{id}/scenarios/reorder, but the WEB suite
  // panel has no DnD — scenarios are added/removed only, order isn't user-
  // editable for v1. Dropping the client method keeps the surface honest.
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
