import { api } from "./api";

/* ───────────────────────────── Types ──────────────────────────────── */

export type LocatorStrategy =
  | "RESOURCE_ID"
  | "ACCESSIBILITY_ID"
  | "TEXT"
  | "CLASS"
  | "XPATH";

export type Locator = { strategy: LocatorStrategy; value: string };

export type ElementView = {
  id: number;
  productId: number;
  name: string;
  description: string | null;
  primaryStrategy: LocatorStrategy;
  primaryValue: string;
  fallbackLocators: Locator[];
  screenshotData: string | null;     // data URL
  sampleBounds: string | null;       // "[l,t,r,b]"
  sampleClass: string | null;
  sampleText: string | null;
  sampleResourceId: string | null;
  createdByUserId: number;
  createdAt: string;
  updatedAt: string;
};

export type ElementCreate = {
  name: string;
  description?: string | null;
  primaryStrategy: LocatorStrategy;
  primaryValue: string;
  fallbackLocators?: Locator[];
  screenshotData?: string | null;
  sampleBounds?: string | null;
  sampleClass?: string | null;
  sampleText?: string | null;
  sampleResourceId?: string | null;
};

export type ElementUpdate = {
  name: string;
  description?: string | null;
  primaryStrategy: LocatorStrategy;
  primaryValue: string;
  fallbackLocators?: Locator[];
};

export type TestDataView = {
  id: number;
  productId: number;
  name: string;
  environment: string;
  value: string;
  description: string | null;
  sensitive: boolean;
  masked: boolean;
  createdByUserId: number;
  createdAt: string;
  updatedAt: string;
};

export type TestDataCreate = {
  name: string;
  environment: string;
  value: string;
  description?: string | null;
  sensitive: boolean;
};

export type TestDataUpdate = TestDataCreate;

/* ───────────────────────────── API ───────────────────────────────── */

export const elementApi = {
  list:   (q?: string) =>
    api.get<ElementView[]>("/api/automation/elements", { params: q ? { q } : {} }).then((r) => r.data),
  get:    (id: number) => api.get<ElementView>(`/api/automation/elements/${id}`).then((r) => r.data),
  create: (body: ElementCreate) => api.post<ElementView>("/api/automation/elements", body).then((r) => r.data),
  update: (id: number, body: ElementUpdate) =>
    api.put<ElementView>(`/api/automation/elements/${id}`, body).then((r) => r.data),
  delete: (id: number) => api.delete<void>(`/api/automation/elements/${id}`).then((r) => r.data),
};

export const testDataApi = {
  list: (environment?: string, reveal = false) =>
    api
      .get<TestDataView[]>("/api/automation/test-data", {
        params: { ...(environment ? { environment } : {}), reveal },
      })
      .then((r) => r.data),
  environments: () => api.get<string[]>("/api/automation/test-data/environments").then((r) => r.data),
  get:    (id: number, reveal = false) =>
    api.get<TestDataView>(`/api/automation/test-data/${id}`, { params: { reveal } }).then((r) => r.data),
  create: (body: TestDataCreate) =>
    api.post<TestDataView>("/api/automation/test-data", body).then((r) => r.data),
  update: (id: number, body: TestDataUpdate) =>
    api.put<TestDataView>(`/api/automation/test-data/${id}`, body).then((r) => r.data),
  delete: (id: number) => api.delete<void>(`/api/automation/test-data/${id}`).then((r) => r.data),
};

/* ─────────────────────────  Scenarios + Steps  ─────────────────────── */

export type StepAction =
  // touch + input
  | "CLICK" | "LONG_PRESS" | "SWIPE" | "ENTER_TEXT" | "CLEAR" | "PRESS_KEY"
  // wait
  | "WAIT_FOR_VISIBLE" | "WAIT_FOR_INVISIBLE" | "SLEEP"
  // visibility / presence
  | "ASSERT_VISIBLE" | "ASSERT_NOT_VISIBLE" | "ASSERT_NOT_PRESENT"
  // interactive state
  | "ASSERT_ENABLED" | "ASSERT_DISABLED"
  | "ASSERT_CHECKED" | "ASSERT_UNCHECKED"
  | "ASSERT_SELECTED" | "ASSERT_FOCUSED"
  // text + value content
  | "ASSERT_TEXT_EQUALS" | "ASSERT_TEXT_CONTAINS" | "ASSERT_TEXT_MATCHES"
  | "ASSERT_VALUE_EQUALS" | "ASSERT_ATTRIBUTE"
  // util
  | "SCREENSHOT" | "COMMENT";

export type ElementRef = {
  id: number;
  name: string;
  primaryStrategy: LocatorStrategy;
  primaryValue: string;
  screenshotData: string | null;
};

export type DataRef = {
  id: number;
  name: string;
  environment: string;
  sensitive: boolean;
};

export type StepView = {
  id: number;
  scenarioId: number;
  orderIndex: number;
  action: StepAction;
  targetElement: ElementRef | null;
  data: DataRef | null;
  literalValue: string | null;
  /** Xray-style expected outcome — documentation, not executed. */
  expectedResult: string | null;
  timeoutMs: number;
  retryCount: number;
  screenshotAfter: boolean;
  createdAt: string;
};

export type ScenarioSummary = {
  id: number;
  productId: number;
  name: string;
  description: string | null;
  tags: string[];
  version: number;
  stepCount: number;
  createdAt: string;
  updatedAt: string;
};

export type ParentSuiteRef = {
  id: number;
  name: string;
  tags: string[];
};

export type ScenarioView = {
  id: number;
  productId: number;
  name: string;
  description: string | null;
  tags: string[];
  preconditions: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
  steps: StepView[];
  /** Suites that reference this scenario — edits/deletes propagate to all of them. */
  parentSuites: ParentSuiteRef[];
};

export type ScenarioCreate = {
  name: string;
  description?: string | null;
  tags?: string[];
  preconditions?: string | null;
};
export type ScenarioUpdate = ScenarioCreate;

export type StepCreate = {
  action: StepAction;
  targetElementId?: number | null;
  dataId?: number | null;
  literalValue?: string | null;
  /** Xray-style expected outcome — documentation, not executed. */
  expectedResult?: string | null;
  timeoutMs?: number | null;
  retryCount?: number | null;
  screenshotAfter?: boolean | null;
  /** Insertion index. Omit to append at end. */
  position?: number | null;
};
export type StepUpdate = Omit<StepCreate, "position">;

export const scenarioApi = {
  list:   () => api.get<ScenarioSummary[]>("/api/automation/scenarios").then((r) => r.data),
  get:    (id: number) => api.get<ScenarioView>(`/api/automation/scenarios/${id}`).then((r) => r.data),
  create: (body: ScenarioCreate) => api.post<ScenarioView>("/api/automation/scenarios", body).then((r) => r.data),
  update: (id: number, body: ScenarioUpdate) =>
    api.put<ScenarioView>(`/api/automation/scenarios/${id}`, body).then((r) => r.data),
  delete: (id: number) => api.delete<void>(`/api/automation/scenarios/${id}`).then((r) => r.data),

  addStep:    (id: number, body: StepCreate) =>
    api.post<ScenarioView>(`/api/automation/scenarios/${id}/steps`, body).then((r) => r.data),
  updateStep: (id: number, stepId: number, body: StepUpdate) =>
    api.put<ScenarioView>(`/api/automation/scenarios/${id}/steps/${stepId}`, body).then((r) => r.data),
  deleteStep: (id: number, stepId: number) =>
    api.delete<ScenarioView>(`/api/automation/scenarios/${id}/steps/${stepId}`).then((r) => r.data),
  reorderSteps: (id: number, stepIds: number[]) =>
    api.put<ScenarioView>(`/api/automation/scenarios/${id}/steps/reorder`, { stepIds }).then((r) => r.data),
};

/* ─────────────────────────────  Suites  ────────────────────────────── */

export type SuiteSummary = {
  id: number;
  productId: number;
  name: string;
  description: string | null;
  tags: string[];
  scenarioCount: number;
  createdAt: string;
  updatedAt: string;
};

export type SuiteScenarioRef = {
  scenarioId: number;
  name: string;
  description: string | null;
  tags: string[];
  stepCount: number;
  orderIndex: number;
};

export type SuiteView = {
  id: number;
  productId: number;
  name: string;
  description: string | null;
  tags: string[];
  createdAt: string;
  updatedAt: string;
  scenarios: SuiteScenarioRef[];
};

export type SuiteCreate = {
  name: string;
  description?: string | null;
  tags?: string[];
};
export type SuiteUpdate = SuiteCreate;

/* ────────────  Unified workspace (suites + scenarios tree)  ────────── */

export type WorkspaceScenarioNode = {
  id: number;
  name: string;
  description: string | null;
  tags: string[];
  stepCount: number;
  version: number;
  updatedAt: string;
  /** Position inside parent suite — null when this node is in the orphan list. */
  orderIndexInSuite: number | null;
};

export type WorkspaceSuiteNode = {
  id: number;
  name: string;
  description: string | null;
  tags: string[];
  scenarioCount: number;
  updatedAt: string;
  scenarios: WorkspaceScenarioNode[];
};

export type WorkspaceTree = {
  suites: WorkspaceSuiteNode[];
  orphanScenarios: WorkspaceScenarioNode[];
  totalScenarios: number;
  totalSuites: number;
};

export const workspaceApi = {
  tree: () => api.get<WorkspaceTree>("/api/automation/workspace/tree").then((r) => r.data),
};

/* ─────────────────────────────  Runs  ─────────────────────────────── */

export type RunStatus = "QUEUED" | "RUNNING" | "PASSED" | "FAILED" | "ERROR" | "CANCELLED";
export type StepResultStatus = "PENDING" | "RUNNING" | "PASSED" | "FAILED" | "SKIPPED" | "ERROR";

export type RunSummary = {
  id: number;
  productId: number;
  scenarioId: number | null;
  scenarioName: string | null;
  deviceId: number | null;
  environment: string;
  status: RunStatus;
  totalSteps: number;
  passedSteps: number;
  failedSteps: number;
  durationMs: number | null;
  /** Public URL of the recorded MP4, if recording succeeded. */
  videoUrl: string | null;
  /** Set when this run was part of a suite. */
  suiteRunId: number | null;
  tags: string[];
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
};

export type StepResultView = {
  id: number;
  stepId: number | null;
  orderIndex: number;
  action: StepAction;
  status: StepResultStatus;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  errorMessage: string | null;
  screenshotUrl: string | null;
  resolvedLocator: string | null;
  retriesUsed: number;
};

export type RunView = {
  id: number;
  productId: number;
  scenarioId: number | null;
  scenarioName: string | null;
  scenarioVersion: number | null;
  deviceId: number | null;
  sessionId: number | null;
  environment: string;
  status: RunStatus;
  triggerType: string;
  triggeredByUserId: number;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  totalSteps: number;
  passedSteps: number;
  failedSteps: number;
  errorSummary: string | null;
  interStepDelayMs: number;
  adaptiveWait: boolean;
  /** Public URL of the recorded MP4. Null if recording was skipped or failed. */
  videoUrl: string | null;
  tags: string[];
  createdAt: string;
  stepResults: StepResultView[];
};

export type RunCreate = {
  scenarioId: number;
  deviceId: number;
  environment?: string;
  /** Sleep (ms) the orchestrator applies between every step; 0 = no pacing. */
  interStepDelayMs?: number;
  /** When true, poll inspect tree until stable (≤5s) instead of using a fixed delay. */
  adaptiveWait?: boolean;
};

export const runApi = {
  list:   (scenarioId?: number) =>
    api.get<RunSummary[]>("/api/automation/runs", { params: scenarioId ? { scenarioId } : {} }).then((r) => r.data),
  get:    (id: number) => api.get<RunView>(`/api/automation/runs/${id}`).then((r) => r.data),
  create: (body: RunCreate) => api.post<RunView>("/api/automation/runs", body).then((r) => r.data),
  updateTags: (id: number, tags: string[]) =>
    api.patch<RunView>(`/api/automation/runs/${id}/tags`, { tags }).then((r) => r.data),
};

/* ───────────────────────────  Suite runs  ──────────────────────────── */

export type SuiteRunStatus = "QUEUED" | "RUNNING" | "PASSED" | "FAILED" | "ERROR" | "CANCELLED";

export type SuiteRunSummary = {
  id: number;
  productId: number;
  suiteId: number;
  suiteName: string | null;
  deviceId: number | null;
  environment: string;
  status: SuiteRunStatus;
  totalScenarios: number;
  passedScenarios: number;
  failedScenarios: number;
  durationMs: number | null;
  tags: string[];
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
};

export type SuiteRunChild = {
  id: number;
  scenarioId: number | null;
  scenarioName: string | null;
  status: RunStatus;
  totalSteps: number;
  passedSteps: number;
  failedSteps: number;
  durationMs: number | null;
  videoUrl: string | null;
  startedAt: string | null;
  finishedAt: string | null;
};

export type SuiteRunView = {
  id: number;
  productId: number;
  suiteId: number;
  suiteName: string | null;
  deviceId: number | null;
  environment: string;
  status: SuiteRunStatus;
  triggerType: string;
  triggeredByUserId: number;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  totalScenarios: number;
  passedScenarios: number;
  failedScenarios: number;
  errorSummary: string | null;
  tags: string[];
  createdAt: string;
  runs: SuiteRunChild[];
};

export type SuiteRunCreate = {
  suiteId: number;
  deviceId: number;
  environment?: string;
  interStepDelayMs?: number;
  adaptiveWait?: boolean;
};

export const suiteRunApi = {
  list:   (suiteId?: number) =>
    api.get<SuiteRunSummary[]>("/api/automation/suite-runs", { params: suiteId ? { suiteId } : {} }).then((r) => r.data),
  get:    (id: number) => api.get<SuiteRunView>(`/api/automation/suite-runs/${id}`).then((r) => r.data),
  create: (body: SuiteRunCreate) => api.post<SuiteRunView>("/api/automation/suite-runs", body).then((r) => r.data),
  updateTags: (id: number, tags: string[]) =>
    api.patch<SuiteRunView>(`/api/automation/suite-runs/${id}/tags`, { tags }).then((r) => r.data),
};

export const suiteApi = {
  list:    () => api.get<SuiteSummary[]>("/api/automation/suites").then((r) => r.data),
  get:     (id: number) => api.get<SuiteView>(`/api/automation/suites/${id}`).then((r) => r.data),
  create:  (body: SuiteCreate) => api.post<SuiteView>("/api/automation/suites", body).then((r) => r.data),
  update:  (id: number, body: SuiteUpdate) =>
    api.put<SuiteView>(`/api/automation/suites/${id}`, body).then((r) => r.data),
  delete:  (id: number) => api.delete<void>(`/api/automation/suites/${id}`).then((r) => r.data),

  addScenario:     (id: number, scenarioId: number) =>
    api.post<SuiteView>(`/api/automation/suites/${id}/scenarios`, { scenarioId }).then((r) => r.data),
  removeScenario:  (id: number, scenarioId: number) =>
    api.delete<SuiteView>(`/api/automation/suites/${id}/scenarios/${scenarioId}`).then((r) => r.data),
  reorderScenarios: (id: number, scenarioIds: number[]) =>
    api.put<SuiteView>(`/api/automation/suites/${id}/scenarios/reorder`, { scenarioIds }).then((r) => r.data),
};

/* ─────────────────  Step action metadata (for editor)  ─────────────── */

/** Describes what input fields each action consumes — the editor reads this to render forms. */
export type StepActionDef = {
  key: StepAction;
  label: string;
  category: "touch" | "input" | "wait" | "assert" | "util";
  needsElement: boolean;
  /** "none" | "data-or-literal" | "literal-only" */
  value: "none" | "data-or-literal" | "literal-only";
  /** Hint shown in the literal-only input for actions like SLEEP, PRESS_KEY etc. */
  literalLabel?: string;
  /** Has its own timeout config (waitForVisible etc.). */
  hasTimeout?: boolean;
  /** Color tint for the node. */
  tone: "blue" | "green" | "amber" | "violet" | "gray";
};

export const STEP_ACTIONS: StepActionDef[] = [
  /* ─────────────  Touch + Input  ───────────── */
  { key: "CLICK",                label: "Click",                category: "touch",  needsElement: true,  value: "none",            tone: "blue" },
  { key: "LONG_PRESS",           label: "Long press",           category: "touch",  needsElement: true,  value: "literal-only",    literalLabel: "duration ms (default 1000)", tone: "blue" },
  { key: "SWIPE",                label: "Swipe",                category: "touch",  needsElement: true,  value: "literal-only",    literalLabel: "direction: up/down/left/right", tone: "blue" },
  { key: "ENTER_TEXT",           label: "Enter text",           category: "input",  needsElement: true,  value: "data-or-literal", tone: "green" },
  { key: "CLEAR",                label: "Clear input",          category: "input",  needsElement: true,  value: "none",            tone: "green" },
  { key: "PRESS_KEY",            label: "Press key",            category: "input",  needsElement: false, value: "literal-only",    literalLabel: "BACK | HOME | RECENTS | <keyCode>", tone: "green" },

  /* ─────────────  Wait  ───────────── */
  { key: "WAIT_FOR_VISIBLE",     label: "Wait for visible",     category: "wait",   needsElement: true,  value: "none",            hasTimeout: true, tone: "amber" },
  { key: "WAIT_FOR_INVISIBLE",   label: "Wait for invisible",   category: "wait",   needsElement: true,  value: "none",            hasTimeout: true, tone: "amber" },
  { key: "SLEEP",                label: "Sleep",                category: "wait",   needsElement: false, value: "literal-only",    literalLabel: "milliseconds", tone: "amber" },

  /* ─────────────  Verify · visibility & presence  ───────────── */
  { key: "ASSERT_VISIBLE",       label: "Verify visible",       category: "assert", needsElement: true,  value: "none",            tone: "violet" },
  { key: "ASSERT_NOT_VISIBLE",   label: "Verify hidden",        category: "assert", needsElement: true,  value: "none",            tone: "violet" },
  { key: "ASSERT_NOT_PRESENT",   label: "Verify not present",   category: "assert", needsElement: true,  value: "none",            tone: "violet" },

  /* ─────────────  Verify · interactive state  ───────────── */
  { key: "ASSERT_ENABLED",       label: "Verify enabled",       category: "assert", needsElement: true,  value: "none",            tone: "violet" },
  { key: "ASSERT_DISABLED",      label: "Verify disabled",      category: "assert", needsElement: true,  value: "none",            tone: "violet" },
  { key: "ASSERT_CHECKED",       label: "Verify checked",       category: "assert", needsElement: true,  value: "none",            tone: "violet" },
  { key: "ASSERT_UNCHECKED",     label: "Verify unchecked",     category: "assert", needsElement: true,  value: "none",            tone: "violet" },
  { key: "ASSERT_SELECTED",      label: "Verify selected",      category: "assert", needsElement: true,  value: "none",            tone: "violet" },
  { key: "ASSERT_FOCUSED",       label: "Verify focused",       category: "assert", needsElement: true,  value: "none",            tone: "violet" },

  /* ─────────────  Verify · text + value content  ───────────── */
  { key: "ASSERT_TEXT_EQUALS",   label: "Verify text equals",   category: "assert", needsElement: true,  value: "data-or-literal", tone: "violet" },
  { key: "ASSERT_TEXT_CONTAINS", label: "Verify text contains", category: "assert", needsElement: true,  value: "data-or-literal", tone: "violet" },
  { key: "ASSERT_TEXT_MATCHES",  label: "Verify text matches",  category: "assert", needsElement: true,  value: "data-or-literal", literalLabel: "regex (e.g. ^[A-Z]{3}\\d+$)", tone: "violet" },
  { key: "ASSERT_VALUE_EQUALS",  label: "Verify value equals",  category: "assert", needsElement: true,  value: "data-or-literal", literalLabel: "expected EditText value", tone: "violet" },
  { key: "ASSERT_ATTRIBUTE",     label: "Verify attribute",     category: "assert", needsElement: true,  value: "literal-only",    literalLabel: "attribute=value (e.g. enabled=true)", tone: "violet" },

  /* ─────────────  Util  ───────────── */
  { key: "SCREENSHOT",           label: "Screenshot",           category: "util",   needsElement: false, value: "literal-only",    literalLabel: "label (e.g. login-done)", tone: "gray" },
  { key: "COMMENT",              label: "Comment",              category: "util",   needsElement: false, value: "literal-only",    literalLabel: "free-form note", tone: "gray" },
];

export const STEP_ACTION_MAP: Record<StepAction, StepActionDef> = Object.fromEntries(
  STEP_ACTIONS.map((d) => [d.key, d]),
) as Record<StepAction, StepActionDef>;

/* ─────────────────  Locator generation from inspector  ───────────── */

/** Ordered list of candidate locators for a node — most reliable first. */
export function generateLocators(
  node: {
    className: string;
    resourceId?: string | null;
    text?: string | null;
    contentDescription?: string | null;
  },
  preferredXPath: string,
  absoluteXPath: string,
): { primary: Locator; fallbacks: Locator[] } {
  const candidates: Locator[] = [];
  if (node.resourceId)         candidates.push({ strategy: "RESOURCE_ID",      value: node.resourceId });
  if (node.contentDescription) candidates.push({ strategy: "ACCESSIBILITY_ID", value: node.contentDescription });
  if (node.text)               candidates.push({ strategy: "TEXT",             value: node.text });
  candidates.push({ strategy: "XPATH", value: preferredXPath });
  if (absoluteXPath !== preferredXPath) candidates.push({ strategy: "XPATH", value: absoluteXPath });
  return {
    primary: candidates[0] ?? { strategy: "XPATH", value: absoluteXPath },
    fallbacks: candidates.slice(1, 4),
  };
}

/** Suggest a kebab-case element name from a node's attributes. */
export function suggestElementName(node: {
  className: string;
  resourceId?: string | null;
  text?: string | null;
  contentDescription?: string | null;
}): string {
  const kebab = (s: string) =>
    s
      .toLowerCase()
      .normalize("NFD")
      .replace(/[̀-ͯ]/g, "")
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "")
      .slice(0, 60);

  if (node.resourceId) {
    const tail = node.resourceId.split("/").pop() ?? node.resourceId;
    return kebab(tail);
  }
  if (node.text) {
    const cls = lastSegment(node.className);
    return kebab(`${cls}-${node.text}`);
  }
  if (node.contentDescription) {
    return kebab(node.contentDescription);
  }
  return kebab(lastSegment(node.className)) + "-" + Math.random().toString(36).slice(2, 6);
}

function lastSegment(s: string): string {
  const i = s.lastIndexOf(".");
  return i < 0 ? s : s.slice(i + 1);
}

/** Crop the snapshot data URL to a node's bounds. Returns a smaller PNG data URL. */
export async function cropSnapshotForElement(
  snapshotDataUrl: string,
  bounds: [number, number, number, number],
  realWidth: number,
  realHeight: number,
  maxDim = 200,
): Promise<string | null> {
  try {
    const img = await loadImage(snapshotDataUrl);
    const [l, t, r, b] = bounds;
    const sx = (l / realWidth)  * img.naturalWidth;
    const sy = (t / realHeight) * img.naturalHeight;
    const sw = ((r - l) / realWidth)  * img.naturalWidth;
    const sh = ((b - t) / realHeight) * img.naturalHeight;
    if (sw <= 0 || sh <= 0) return null;
    const scale = Math.min(1, maxDim / Math.max(sw, sh));
    const canvas = document.createElement("canvas");
    canvas.width  = Math.max(1, Math.round(sw * scale));
    canvas.height = Math.max(1, Math.round(sh * scale));
    const ctx = canvas.getContext("2d");
    if (!ctx) return null;
    ctx.drawImage(img, sx, sy, sw, sh, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL("image/png");
  } catch {
    return null;
  }
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((res, rej) => {
    const img = new Image();
    img.onload = () => res(img);
    img.onerror = rej;
    img.src = src;
  });
}
