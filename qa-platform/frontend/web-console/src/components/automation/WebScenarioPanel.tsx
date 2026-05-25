import { Fragment, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import {
  ChevronDown, ChevronRight, FileCheck2, GitBranch, History, Pencil, Play, Plus, Trash2, X,
} from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import ActionPicker, { iconFor } from "@/components/automation/ActionPicker";
import { cn } from "@/lib/cn";
import {
  browserApi, webElementApi, webRunApi, webScenarioApi, webTestDataApi,
  WEB_STEP_ACTIONS, WEB_STEP_ACTION_MAP,
  type BrowserProfile, type WebElementView, type WebScenarioUpdate,
  type WebStepAction, type WebStepCondition, type WebStepCreate, type WebStepUpdate, type WebStepView, type WebTestDataView,
} from "@/lib/webAutomation";

/** One-click quick picks above the popover — cover the bulk of typical
 *  Playwright-style web scripts. Anything else stays one search away. */
const WEB_QUICK_PICKS: WebStepAction[] = [
  "GOTO", "CLICK", "FILL", "WAIT_FOR_SELECTOR", "ASSERT_VISIBLE", "ASSERT_TEXT_CONTAINS",
];

/** Identifies WHERE in the step tree the user is about to insert a new
 *  step. `parentId` + `branch` both null = root level (legacy/flat
 *  scenarios). Both set = inside an IF's then/else lane. */
type InsertSlot = {
  parentId: number | null;
  branch: "then" | "else" | null;
  position: number;
};

function sameSlot(a: InsertSlot | null, b: { parentId: number | null; branch: "then" | "else" | null; position: number }): boolean {
  return a != null && a.parentId === b.parentId && a.branch === b.branch && a.position === b.position;
}

function humaniseWebCategory(key: string): string {
  switch (key) {
    case "control":     return "Control flow";
    case "navigation":  return "Navigation";
    case "interaction": return "Interaction";
    case "wait":        return "Wait";
    case "assert":      return "Verify";
    case "util":        return "Util";
    default:            return key.charAt(0).toUpperCase() + key.slice(1);
  }
}

type Props = {
  scenarioId: number;
  onAfterDelete: () => void;
  onMutated: () => void;
};

/**
 * Visual mirror of Android's {@code ScenarioPanel}. Header card (name +
 * description + tags + version + step count + Run/Edit/Delete actions),
 * step list with inline editor, add-step card at the bottom. Skips
 * Android-only pieces (drag-and-drop reorder, parent-suite chips,
 * Inspector integration) — v1 web doesn't need them.
 */
export default function WebScenarioPanel({ scenarioId, onAfterDelete, onMutated }: Props) {
  const qc = useQueryClient();
  const nav = useNavigate();

  const scenarioQ = useQuery({
    queryKey: ["web-scenario", scenarioId],
    queryFn: () => webScenarioApi.get(scenarioId),
    refetchOnWindowFocus: false,
  });

  function applyScenario(_: unknown) {
    qc.invalidateQueries({ queryKey: ["web-scenario", scenarioId] });
    qc.invalidateQueries({ queryKey: ["web-workspace-tree"] });
    onMutated();
  }

  const addStep = useMutation({
    mutationFn: (b: WebStepCreate) => webScenarioApi.addStep(scenarioId, b),
    onSuccess: applyScenario,
  });
  const updateStep = useMutation({
    mutationFn: ({ stepId, b }: { stepId: number; b: WebStepUpdate }) =>
      webScenarioApi.updateStep(scenarioId, stepId, b),
    onSuccess: applyScenario,
  });
  const deleteStep = useMutation({
    mutationFn: (stepId: number) => webScenarioApi.deleteStep(scenarioId, stepId),
    onSuccess: applyScenario,
  });
  const updateMeta = useMutation({
    mutationFn: (b: WebScenarioUpdate) => webScenarioApi.update(scenarioId, b),
    onSuccess: applyScenario,
  });
  const remove = useMutation({
    mutationFn: () => webScenarioApi.delete(scenarioId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["web-workspace-tree"] });
      onAfterDelete();
    },
  });

  // Scope-aware insert slot. Tracks WHICH branch the user is currently
  // inserting into and at WHAT position within that branch.
  //  parentId / branch null = root level (legacy/flat scenario behaviour).
  //  parentId / branch set  = inside an IF's "then" or "else" lane.
  const [addingAt, setAddingAt] = useState<InsertSlot | null>(null);
  const [editingStepId, setEditingStepId] = useState<number | null>(null);
  const [editingMeta, setEditingMeta] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [running, setRunning] = useState(false);

  if (scenarioQ.isLoading) {
    return <div className="p-6 text-ink-muted flex items-center gap-2 text-sm"><Spinner /> Loading scenario…</div>;
  }
  if (scenarioQ.error || !scenarioQ.data) return <div className="p-6 text-danger-500">Scenario not found</div>;

  const scenario = scenarioQ.data;
  const steps = scenario.steps;

  return (
    <div className="space-y-3">
      {/* ── Header card — mirrors Android's scenario header ──────────── */}
      <Card className="p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2 text-brand-300">
              <FileCheck2 size={14} />
              <span className="text-[10px] uppercase tracking-wider font-semibold">Scenario</span>
            </div>
            <div className="text-lg font-semibold truncate mt-0.5">{scenario.name}</div>
            {scenario.description && <div className="text-xs text-ink-muted mt-1">{scenario.description}</div>}
            <div className="flex items-center gap-2 mt-2 flex-wrap">
              {scenario.tags.map((t) => (
                <span key={t} className="text-[10px] uppercase tracking-wider rounded px-1.5 py-0.5 border border-surface-border text-ink-secondary">{t}</span>
              ))}
              <span className="text-[10px] text-ink-muted font-mono">v{scenario.version}</span>
              <span className="text-[10px] text-ink-muted font-mono">{steps.length} steps</span>
            </div>
          </div>
          <div className="flex items-center gap-1.5">
            <Button
              variant="primary"
              size="sm"
              leftIcon={<Play size={12} />}
              disabled={steps.length === 0}
              onClick={() => setRunning(true)}
              title={steps.length === 0 ? "Add steps before running" : "Run on a browser profile"}
            >
              Run
            </Button>
            <button
              onClick={() => nav(`/automation/reports?tab=runs&scenarioId=${scenarioId}`)}
              className="p-1.5 rounded text-ink-muted hover:text-ink-primary hover:bg-surface-muted"
              title="View runs for this scenario"
            >
              <History size={13} />
            </button>
            <button onClick={() => setEditingMeta(true)} className="p-1.5 rounded text-ink-muted hover:text-ink-primary hover:bg-surface-muted" title="Edit scenario">
              <Pencil size={13} />
            </button>
            <button
              onClick={() => setConfirmingDelete(true)}
              className="p-1.5 rounded text-ink-muted hover:text-danger-500 hover:bg-surface-muted"
              title="Delete scenario"
            >
              <Trash2 size={13} />
            </button>
          </div>
        </div>
      </Card>

      {/* ── Steps (tree-aware) ───────────────────────────────────────── */}
      {steps.length === 0 && addingAt == null ? (
        <Card>
          <EmptyState
            icon={<Plus size={20} />}
            title="No steps yet"
            description="Add the first step. You can pick an element from the repository and a value from test data."
            action={
              <Button variant="primary" size="sm" leftIcon={<Plus size={14} />}
                      onClick={() => setAddingAt({ parentId: null, branch: null, position: 0 })}>
                Add step
              </Button>
            }
          />
        </Card>
      ) : (
        <StepListSection
          steps={steps}
          parentId={null}
          branch={null}
          addingAt={addingAt}
          setAddingAt={setAddingAt}
          editingStepId={editingStepId}
          setEditingStepId={setEditingStepId}
          addStep={addStep}
          updateStep={updateStep}
          deleteStep={deleteStep}
        />
      )}

      {editingMeta && (
        <ScenarioMetaDialog
          initial={{ name: scenario.name, description: scenario.description, tags: scenario.tags }}
          busy={updateMeta.isPending}
          onClose={() => setEditingMeta(false)}
          onSubmit={(b) => updateMeta.mutate(b, { onSuccess: () => setEditingMeta(false) })}
        />
      )}

      {confirmingDelete && (
        <ConfirmDelete
          name={scenario.name}
          busy={remove.isPending}
          onCancel={() => setConfirmingDelete(false)}
          onConfirm={() => remove.mutate()}
        />
      )}

      {running && (
        <ScenarioRunDialog
          scenarioId={scenarioId}
          scenarioName={scenario.name}
          onClose={() => setRunning(false)}
        />
      )}
    </div>
  );
}

/* ───────────────────────────  Step row + editor  ─────────────────────── */

function StepRow({
  step, isEditing, onEdit, onCancelEdit, onDelete, onSubmitEdit, busy,
}: {
  step: WebStepView;
  isEditing: boolean;
  onEdit: () => void;
  onCancelEdit: () => void;
  onDelete: () => void;
  onSubmitEdit: (b: WebStepUpdate) => void;
  busy: boolean;
}) {
  const def = WEB_STEP_ACTION_MAP[step.action];
  if (isEditing) {
    return (
      <Card className="border-l-2 border-l-brand-500 bg-brand-500/5 p-3">
        <div className="text-[10px] uppercase tracking-wider font-semibold text-brand-300 mb-3">Edit step #{step.orderIndex + 1}</div>
        <StepEditor
          initial={step}
          busy={busy}
          submitLabel="Save"
          onCancel={onCancelEdit}
          onSubmit={onSubmitEdit}
        />
      </Card>
    );
  }
  return (
    <Card className="p-3 group">
      <div className="flex items-start gap-3">
        <div className="text-[10px] text-ink-muted w-6 text-right pt-0.5 font-mono">{step.orderIndex + 1}</div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            {/* Action badge — picks up the icon the picker showed when this
                step was created so the visual cue stays consistent. */}
            {(() => {
              const Icon = iconFor(def?.iconName);
              return (
                <span className={cn(
                  "inline-flex items-center gap-1.5 text-[10px] font-medium uppercase tracking-wider rounded px-1.5 py-0.5 border",
                  def?.tone === "blue"   && "border-brand-500/40   bg-brand-500/10   text-brand-300",
                  def?.tone === "green"  && "border-success-500/40 bg-success-500/10 text-success-500",
                  def?.tone === "amber"  && "border-warning-500/40 bg-warning-500/10 text-warning-500",
                  def?.tone === "violet" && "border-danger-500/30  bg-danger-500/10  text-danger-500",
                  def?.tone === "gray"   && "border-surface-border bg-surface-muted  text-ink-secondary",
                )}>
                  <Icon size={11} />
                  {def?.label ?? step.action}
                </span>
              );
            })()}
            {step.targetElementId != null && (
              <span className="text-[10px] text-brand-300">→ element #{step.targetElementId}</span>
            )}
            {step.dataId != null && (
              <span className="text-[10px] text-warning-500">⇒ data #{step.dataId}</span>
            )}
          </div>
          {step.selector && (
            <div className="text-[11px] text-ink-secondary mt-1 font-mono break-all">{step.selector}</div>
          )}
          {step.value && (
            <div className="text-[11px] text-ink-muted mt-0.5 font-mono break-all">value: {step.value}</div>
          )}
        </div>
        <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
          <button onClick={onEdit} className="p-1.5 rounded text-ink-muted hover:text-ink-primary hover:bg-surface-muted" title="Edit">
            <Pencil size={13} />
          </button>
          <button onClick={onDelete} className="p-1.5 rounded text-ink-muted hover:text-danger-500 hover:bg-surface-muted" title="Delete">
            <Trash2 size={13} />
          </button>
        </div>
      </div>
    </Card>
  );
}

function StepEditor({ initial, busy, submitLabel, onCancel, onSubmit }: {
  initial?: Partial<WebStepView>;
  busy: boolean;
  submitLabel: string;
  onCancel: () => void;
  onSubmit: (b: WebStepCreate | WebStepUpdate) => void;
}) {
  const [action, setAction] = useState<WebStepAction>(initial?.action ?? "GOTO");
  const [selectorMode, setSelectorMode] = useState<"literal" | "catalog">(
    initial?.targetElementId != null ? "catalog" : "literal",
  );
  const [selector, setSelector] = useState(initial?.selector ?? "");
  const [targetElementId, setTargetElementId] = useState<number | null>(initial?.targetElementId ?? null);
  const [valueMode, setValueMode] = useState<"literal" | "catalog">(
    initial?.dataId != null ? "catalog" : "literal",
  );
  const [value, setValue] = useState(initial?.value ?? "");
  const [dataId, setDataId] = useState<number | null>(initial?.dataId ?? null);
  const [timeoutMs, setTimeoutMs] = useState<number>(initial?.timeoutMs ?? 5000);
  // For IF rows: the predicate. Initialised from the existing step when
  // editing, or a sensible default when creating a new IF.
  const [condition, setCondition] = useState<WebStepCondition | null>(
    initial?.condition ?? null,
  );

  const def = WEB_STEP_ACTION_MAP[action];
  const isIf = action === "IF";
  const elementsQ = useQuery({ queryKey: ["web-elements"], queryFn: webElementApi.list });
  const dataQ     = useQuery({ queryKey: ["web-test-data"], queryFn: webTestDataApi.list });

  function submit() {
    if (isIf) {
      if (!condition) return;
      onSubmit({ action, condition });
      return;
    }
    if (def.needsSelector && selectorMode === "literal" && !selector.trim()) return;
    if (def.needsSelector && selectorMode === "catalog" && targetElementId == null) return;
    if (def.needsValue && valueMode === "literal" && !value.trim()) return;
    if (def.needsValue && valueMode === "catalog" && dataId == null) return;

    onSubmit({
      action,
      selector:        def.needsSelector && selectorMode === "literal" ? selector.trim() : null,
      targetElementId: def.needsSelector && selectorMode === "catalog" ? targetElementId : null,
      value:           def.needsValue && valueMode === "literal" ? value.trim() : null,
      dataId:          def.needsValue && valueMode === "catalog" ? dataId : null,
      timeoutMs,
    });
  }

  // Web action catalog → generic picker options. Category is humanised here so
  // the popover headings read "Navigation" not "navigation".
  const pickerOptions = WEB_STEP_ACTIONS.map((a) => ({
    key: a.key,
    label: a.label,
    category: humaniseWebCategory(a.category),
    description: a.description,
    iconName: a.iconName,
    tone: a.tone,
  }));

  return (
    <div className="space-y-3">
      <div>
        <span className="label block mb-1.5">Action</span>
        <ActionPicker
          value={action}
          onChange={(k) => setAction(k as WebStepAction)}
          options={pickerOptions}
          quickPickKeys={WEB_QUICK_PICKS}
        />
      </div>

      {/* IF rows show a condition builder instead of selector/value/timeout. */}
      {isIf && (
        <ConditionBuilder
          value={condition}
          onChange={setCondition}
          elements={elementsQ.data ?? []}
          testData={dataQ.data ?? []}
        />
      )}

      {!isIf && def.needsSelector && (
        <div>
          <div className="flex items-center justify-between mb-1.5">
            <span className="label">Selector</span>
            <ModeToggle mode={selectorMode} onChange={setSelectorMode} catalogLabel="From elements" />
          </div>
          {selectorMode === "literal" ? (
            <input value={selector} onChange={(e) => setSelector(e.target.value)}
                   placeholder="CSS / XPath / text= / role= (Playwright syntax)"
                   className="input text-xs font-mono" />
          ) : (
            <select value={targetElementId ?? ""} onChange={(e) => setTargetElementId(e.target.value ? Number(e.target.value) : null)}
                    className="input text-xs">
              <option value="">{elementsQ.data?.length === 0 ? "No elements — save one on the Elements page" : "Pick an element…"}</option>
              {(elementsQ.data ?? []).map((el: WebElementView) => (
                <option key={el.id} value={el.id}>{el.name} · {el.primaryStrategy}</option>
              ))}
            </select>
          )}
        </div>
      )}

      {!isIf && def.needsValue && (
        <div>
          <div className="flex items-center justify-between mb-1.5">
            <span className="label">Value</span>
            <ModeToggle mode={valueMode} onChange={setValueMode} catalogLabel="From test data" />
          </div>
          {valueMode === "literal" ? (
            <input value={value} onChange={(e) => setValue(e.target.value)}
                   placeholder={def.valueLabel ?? ""}
                   className="input text-xs font-mono" />
          ) : (
            <select value={dataId ?? ""} onChange={(e) => setDataId(e.target.value ? Number(e.target.value) : null)}
                    className="input text-xs">
              <option value="">{dataQ.data?.length === 0 ? "No test data — save one on the Test data page" : "Pick test data…"}</option>
              {(dataQ.data ?? []).map((td: WebTestDataView) => (
                <option key={td.id} value={td.id}>{td.name} · {td.environment}{td.sensitive ? " 🔒" : ""}</option>
              ))}
            </select>
          )}
        </div>
      )}

      {!isIf && (
        <div>
          <span className="label block mb-1.5">Timeout (ms)</span>
          <input type="number" min={0} value={timeoutMs} onChange={(e) => setTimeoutMs(Number(e.target.value) || 0)}
                 className="input text-xs font-mono w-32" />
        </div>
      )}

      <div className="flex justify-end gap-2 pt-1">
        <Button variant="secondary" size="sm" onClick={onCancel}>Cancel</Button>
        <Button variant="primary" size="sm" loading={busy} onClick={submit}
                disabled={
                  (isIf && condition == null) ||
                  (!isIf && def.needsSelector && selectorMode === "literal" && !selector.trim()) ||
                  (!isIf && def.needsSelector && selectorMode === "catalog" && targetElementId == null) ||
                  (!isIf && def.needsValue && valueMode === "literal" && !value.trim()) ||
                  (!isIf && def.needsValue && valueMode === "catalog" && dataId == null)
                }>
          {submitLabel}
        </Button>
      </div>
    </div>
  );
}

function ModeToggle({ mode, onChange, catalogLabel }: {
  mode: "literal" | "catalog";
  onChange: (m: "literal" | "catalog") => void;
  catalogLabel: string;
}) {
  return (
    <div className="flex border border-surface-border rounded-md overflow-hidden">
      <button onClick={() => onChange("literal")}
              className={cn("px-2 h-5 text-[9px] uppercase tracking-wider transition-colors",
                mode === "literal" ? "bg-brand-500/15 text-brand-300" : "text-ink-muted hover:text-ink-primary")}>
        Literal
      </button>
      <button onClick={() => onChange("catalog")}
              className={cn("px-2 h-5 text-[9px] uppercase tracking-wider transition-colors border-l border-surface-border",
                mode === "catalog" ? "bg-brand-500/15 text-brand-300" : "text-ink-muted hover:text-ink-primary")}>
        {catalogLabel}
      </button>
    </div>
  );
}

/* ─────────────────────────  Condition builder  ──────────────────────── */

/**
 * Triple-dropdown UI for the IF predicate: subject type → subject ref →
 * operator → value. Outputs a {@link WebStepCondition} or null when the
 * selection is incomplete (the parent disables Submit accordingly).
 *
 * Two subject types in v1:
 *  - element_state: pick a WebElement, then a Playwright-friendly state
 *    predicate ("is visible", "text contains …").
 *  - test_data_compare: pick a WebTestData row, then a value comparison.
 *
 * Raw JS expression is deliberately omitted — keeps the predicates
 * statically analysable and matches the design recommendation from the
 * conditional-logic research.
 */
function ConditionBuilder({
  value, onChange, elements, testData,
}: {
  value: WebStepCondition | null;
  onChange: (c: WebStepCondition | null) => void;
  elements: WebElementView[];
  testData: WebTestDataView[];
}) {
  const type = value?.type ?? "element_state";

  // Tracks the currently-picked subject id even across type swaps so a
  // typo on the operator doesn't blank the selection out.
  function setType(next: "element_state" | "test_data_compare") {
    if (next === "element_state") {
      onChange({ type: "element_state", subjectId: elements[0]?.id ?? 0, operator: "is_visible", value: null });
    } else {
      onChange({ type: "test_data_compare", subjectId: testData[0]?.id ?? 0, operator: "equals", value: "" });
    }
  }

  return (
    <div className="space-y-2 rounded-md border border-warning-500/30 bg-warning-500/5 p-3">
      <div className="flex items-center justify-between">
        <span className="label">Condition</span>
        <div className="flex border border-surface-border rounded-md overflow-hidden">
          <button
            type="button"
            onClick={() => setType("element_state")}
            className={cn("px-2 h-5 text-[9px] uppercase tracking-wider transition-colors",
              type === "element_state" ? "bg-warning-500/20 text-warning-500" : "text-ink-muted hover:text-ink-primary")}
          >
            Element state
          </button>
          <button
            type="button"
            onClick={() => setType("test_data_compare")}
            className={cn("px-2 h-5 text-[9px] uppercase tracking-wider transition-colors border-l border-surface-border",
              type === "test_data_compare" ? "bg-warning-500/20 text-warning-500" : "text-ink-muted hover:text-ink-primary")}
          >
            Test data
          </button>
        </div>
      </div>

      {type === "element_state" ? (
        <ElementStateRow value={value as any} onChange={onChange} elements={elements} />
      ) : (
        <TestDataCompareRow value={value as any} onChange={onChange} testData={testData} />
      )}
    </div>
  );
}

function ElementStateRow({
  value, onChange, elements,
}: {
  value: Extract<WebStepCondition, { type: "element_state" }> | null;
  onChange: (c: WebStepCondition | null) => void;
  elements: WebElementView[];
}) {
  const subjectId = value?.subjectId ?? elements[0]?.id ?? 0;
  const operator  = value?.operator ?? "is_visible";
  const literal   = value?.value ?? "";
  const needsValue = operator === "text_contains" || operator === "text_equals";

  function emit(next: Partial<Extract<WebStepCondition, { type: "element_state" }>>) {
    onChange({
      type: "element_state",
      subjectId, operator, value: literal,
      ...next,
    });
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-[1fr_140px_1fr] gap-2 items-start">
      <select
        value={subjectId} onChange={(e) => emit({ subjectId: Number(e.target.value) })}
        className="input text-xs"
      >
        <option value="">{elements.length === 0 ? "No elements — save one first" : "Pick an element…"}</option>
        {elements.map((el) => (
          <option key={el.id} value={el.id}>{el.name} · {el.primaryStrategy}</option>
        ))}
      </select>

      <select
        value={operator} onChange={(e) => emit({ operator: e.target.value as any })}
        className="input text-xs"
      >
        <option value="is_visible">is visible</option>
        <option value="is_hidden">is hidden</option>
        <option value="exists">exists</option>
        <option value="text_contains">text contains</option>
        <option value="text_equals">text equals</option>
      </select>

      {needsValue ? (
        <input
          type="text" value={literal ?? ""} onChange={(e) => emit({ value: e.target.value })}
          placeholder="expected text"
          className="input text-xs font-mono"
        />
      ) : (
        <span className="text-[10px] text-ink-muted italic self-center pl-1">no value needed</span>
      )}
    </div>
  );
}

function TestDataCompareRow({
  value, onChange, testData,
}: {
  value: Extract<WebStepCondition, { type: "test_data_compare" }> | null;
  onChange: (c: WebStepCondition | null) => void;
  testData: WebTestDataView[];
}) {
  const subjectId = value?.subjectId ?? testData[0]?.id ?? 0;
  const operator  = value?.operator ?? "equals";
  const literal   = value?.value ?? "";

  function emit(next: Partial<Extract<WebStepCondition, { type: "test_data_compare" }>>) {
    onChange({
      type: "test_data_compare",
      subjectId, operator, value: literal,
      ...next,
    });
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-[1fr_140px_1fr] gap-2 items-start">
      <select
        value={subjectId} onChange={(e) => emit({ subjectId: Number(e.target.value) })}
        className="input text-xs"
      >
        <option value="">{testData.length === 0 ? "No test data — save one first" : "Pick test data…"}</option>
        {testData.map((td) => (
          <option key={td.id} value={td.id}>{td.name} · {td.environment}{td.sensitive ? " 🔒" : ""}</option>
        ))}
      </select>

      <select
        value={operator} onChange={(e) => emit({ operator: e.target.value as any })}
        className="input text-xs"
      >
        <option value="equals">equals</option>
        <option value="not_equals">not equals</option>
        <option value="contains">contains</option>
        <option value="greater_than">greater than</option>
        <option value="less_than">less than</option>
      </select>

      <input
        type="text" value={literal} onChange={(e) => emit({ value: e.target.value })}
        placeholder="expected value"
        className="input text-xs font-mono"
      />
    </div>
  );
}

/* ─────────────────────────  Scenario meta editor  ────────────────────── */

function ScenarioMetaDialog({ initial, busy, onClose, onSubmit }: {
  initial: { name: string; description: string | null; tags: string[] };
  busy: boolean;
  onClose: () => void;
  onSubmit: (b: WebScenarioUpdate) => void;
}) {
  const [name, setName] = useState(initial.name);
  const [description, setDescription] = useState(initial.description ?? "");
  const [tagsInput, setTagsInput] = useState(initial.tags.join(", "));
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-lg">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div className="text-sm font-semibold">Edit scenario</div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted"><X size={14} /></button>
        </div>
        <div className="p-5 space-y-4">
          <label className="block">
            <span className="label block mb-1.5">Name</span>
            <input value={name} onChange={(e) => setName(e.target.value)} className="input" autoFocus />
          </label>
          <label className="block">
            <span className="label block mb-1.5">Description</span>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} className="input resize-y" />
          </label>
          <label className="block">
            <span className="label block mb-1.5">Tags (comma-separated)</span>
            <input value={tagsInput} onChange={(e) => setTagsInput(e.target.value)} className="input" />
          </label>
        </div>
        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={busy} disabled={!name.trim()}
                  onClick={() => onSubmit({
                    name: name.trim(),
                    description: description.trim() || null,
                    tags: tagsInput.split(",").map((t) => t.trim()).filter(Boolean),
                  })}>
            Save
          </Button>
        </div>
      </Card>
    </div>
  );
}

function ConfirmDelete({ name, busy, onCancel, onConfirm }: {
  name: string; busy: boolean; onCancel: () => void; onConfirm: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-md p-5">
        <div className="text-sm font-semibold">Delete scenario?</div>
        <div className="text-xs text-ink-muted mt-1">
          <code className="font-mono">{name}</code> and all its steps will be removed.
        </div>
        <div className="flex justify-end gap-2 mt-5">
          <Button variant="secondary" onClick={onCancel}>Cancel</Button>
          <Button variant="danger" loading={busy} onClick={onConfirm}>Delete</Button>
        </div>
      </Card>
    </div>
  );
}

/* ─────────────────────────  Run dialog  ────────────────────────────── */

function ScenarioRunDialog({ scenarioId, scenarioName, onClose }: { scenarioId: number; scenarioName: string; onClose: () => void }) {
  const qc = useQueryClient();
  const browsersQ = useQuery({ queryKey: ["browsers"], queryFn: browserApi.list });
  const [profileId, setProfileId] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () => webRunApi.create({ scenarioId, browserProfileId: profileId! }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["web-runs"] }); onClose(); },
  });
  const err = (create.error as any)?.response?.data?.detail;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-2xl flex flex-col max-h-[90vh]">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div>
            <div className="text-sm font-semibold flex items-center gap-2">
              <Play size={14} className="text-success-500" />
              Run scenario
            </div>
            <div className="text-xs text-ink-muted mt-0.5">
              <code className="font-mono">{scenarioName}</code> will run once on the selected browser.
            </div>
          </div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted"><X size={14} /></button>
        </div>
        <div className="p-5 space-y-3 overflow-auto">
          <span className="label block mb-1.5">Browser profile</span>
          {browsersQ.isLoading && <div className="text-xs text-ink-muted flex items-center gap-2"><Spinner /> Loading…</div>}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            {(browsersQ.data ?? []).map((b: BrowserProfile) => (
              <button key={b.id} onClick={() => setProfileId(b.id)}
                      className={cn("text-left px-3 py-2 rounded-md border transition-colors",
                        profileId === b.id ? "border-brand-500/50 bg-brand-500/10" : "border-surface-border hover:border-brand-500/30 bg-surface")}>
                <div className="text-sm font-medium">{b.displayName}</div>
                <div className="text-[10px] text-ink-muted">{b.engine} · {b.width}×{b.height}{b.isMobile && " · mobile"}</div>
              </button>
            ))}
          </div>
          {err && (
            <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">{err}</div>
          )}
        </div>
        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="primary" leftIcon={<Play size={12} />} disabled={!profileId} loading={create.isPending} onClick={() => create.mutate()}>
            Run
          </Button>
        </div>
      </Card>
    </div>
  );
}

/* ─────────────────────────  Insert-between affordance  ─────────────────
 * Thin idle line that becomes a clickable "+ Insert" pill on hover. Sits
 * between every pair of step rows (and above the first) so users can splice
 * a new step at any position instead of only appending to the end. When
 * `active` is true the line stays highlighted because the new-step card is
 * rendered immediately below.
 */
function InsertHereLine({ active, onClick }: { active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "group relative w-full h-3 -my-1 flex items-center justify-center",
        "outline-none focus-visible:ring-2 focus-visible:ring-brand-500/40 rounded",
      )}
      title="Insert step here"
    >
      <span
        aria-hidden
        className={cn(
          "absolute inset-x-0 top-1/2 -translate-y-1/2 h-px transition-colors",
          active ? "bg-brand-500/60" : "bg-transparent group-hover:bg-brand-500/40",
        )}
      />
      <span
        aria-hidden
        className={cn(
          "relative inline-flex items-center gap-1 text-[10px] uppercase tracking-wider font-semibold",
          "px-2 py-0.5 rounded-full border bg-surface transition-opacity",
          active
            ? "border-brand-500/60 text-brand-300 opacity-100"
            : "border-surface-border text-ink-muted opacity-0 group-hover:opacity-100",
        )}
      >
        <Plus size={10} /> Insert
      </span>
    </button>
  );
}

function NewStepCard({
  busy, onCancel, onSubmit,
}: {
  busy?: boolean;
  onCancel: () => void;
  onSubmit: (b: WebStepCreate) => void;
}) {
  return (
    <Card className="border-l-2 border-l-brand-500 bg-brand-500/5 p-3">
      <div className="text-[10px] uppercase tracking-wider font-semibold text-brand-300 mb-3">New step</div>
      <StepEditor
        busy={busy ?? false}
        submitLabel="Add step"
        onCancel={onCancel}
        onSubmit={(b) => onSubmit(b as WebStepCreate)}
      />
    </Card>
  );
}

/* ─────────────────────────  Recursive step list  ─────────────────────── */

type StepListMutations = {
  addStep: ReturnType<typeof useMutation<WebStepView, unknown, WebStepCreate>>;
  updateStep: ReturnType<typeof useMutation<WebStepView, unknown, { stepId: number; b: WebStepUpdate }>>;
  deleteStep: ReturnType<typeof useMutation<void, unknown, number>>;
};

/**
 * Renders one block of steps (root scenario body, or one branch inside an
 * IF). Recursion: when this section encounters an IF step it renders an
 * IfStepCard which itself contains two StepListSection instances (one
 * per branch). Insert-here lines call addStep with the scope's parentId +
 * branch, so the backend slots the new step into the right place.
 */
function StepListSection({
  steps, parentId, branch,
  addingAt, setAddingAt, editingStepId, setEditingStepId,
  addStep, updateStep, deleteStep,
}: {
  steps: WebStepView[];
  parentId: number | null;
  branch: "then" | "else" | null;
  addingAt: InsertSlot | null;
  setAddingAt: (s: InsertSlot | null) => void;
  editingStepId: number | null;
  setEditingStepId: (id: number | null) => void;
} & StepListMutations) {
  const slot = (position: number) => ({ parentId, branch, position });
  return (
    <div className="space-y-2">
      {/* Prepend affordance — only for non-empty branches. When the branch
          is empty, slot(0) collides with slot(steps.length), and the bottom
          "Add step" pill already covers position 0. Rendering the prepend
          NewStepCard here too would show two editors at once. */}
      {steps.length > 0 && (
        <>
          <InsertHereLine
            active={sameSlot(addingAt, slot(0))}
            onClick={() => setAddingAt(slot(0))}
          />
          {sameSlot(addingAt, slot(0)) && (
            <NewStepCard
              busy={addStep.isPending}
              onCancel={() => setAddingAt(null)}
              onSubmit={(b) => addStep.mutate(
                { ...b, position: 0, parentStepId: parentId, branchLabel: branch },
                { onSuccess: () => setAddingAt(null) },
              )}
            />
          )}
        </>
      )}
      {steps.map((step, idx) => (
        <Fragment key={step.id}>
          {step.action === "IF" ? (
            <IfStepCard
              step={step}
              isEditingMeta={editingStepId === step.id}
              onEditMeta={() => setEditingStepId(step.id)}
              onCancelEditMeta={() => setEditingStepId(null)}
              onDelete={() => { if (confirm(`Delete IF block (and its children)?`)) deleteStep.mutate(step.id); }}
              onSubmitMeta={(b) => updateStep.mutate(
                { stepId: step.id, b: b as WebStepUpdate },
                { onSuccess: () => setEditingStepId(null) },
              )}
              addingAt={addingAt}
              setAddingAt={setAddingAt}
              editingStepId={editingStepId}
              setEditingStepId={setEditingStepId}
              addStep={addStep}
              updateStep={updateStep}
              deleteStep={deleteStep}
              busy={updateStep.isPending}
            />
          ) : (
            <StepRow
              step={step}
              isEditing={editingStepId === step.id}
              onEdit={() => setEditingStepId(step.id)}
              onCancelEdit={() => setEditingStepId(null)}
              onDelete={() => { if (confirm(`Delete step #${step.orderIndex + 1}?`)) deleteStep.mutate(step.id); }}
              onSubmitEdit={(b) => updateStep.mutate(
                { stepId: step.id, b: b as WebStepUpdate },
                { onSuccess: () => setEditingStepId(null) },
              )}
              busy={updateStep.isPending}
            />
          )}
          {idx < steps.length - 1 && (
            <InsertHereLine
              active={sameSlot(addingAt, slot(idx + 1))}
              onClick={() => setAddingAt(slot(idx + 1))}
            />
          )}
          {sameSlot(addingAt, slot(idx + 1)) && idx < steps.length - 1 && (
            <NewStepCard
              busy={addStep.isPending}
              onCancel={() => setAddingAt(null)}
              onSubmit={(b) => addStep.mutate(
                { ...b, position: idx + 1, parentStepId: parentId, branchLabel: branch },
                { onSuccess: () => setAddingAt(null) },
              )}
            />
          )}
        </Fragment>
      ))}

      {/* Always-visible "Add step" pill at the bottom of every branch. */}
      {sameSlot(addingAt, slot(steps.length)) ? (
        <NewStepCard
          busy={addStep.isPending}
          onCancel={() => setAddingAt(null)}
          onSubmit={(b) => addStep.mutate(
            { ...b, parentStepId: parentId, branchLabel: branch },
            { onSuccess: () => setAddingAt(null) },
          )}
        />
      ) : (
        <button
          onClick={() => setAddingAt(slot(steps.length))}
          className="w-full inline-flex items-center justify-center gap-1.5 h-9 rounded-md border border-dashed border-surface-border text-ink-secondary hover:text-ink-primary hover:border-brand-500/40 hover:bg-surface-muted/40 transition-colors text-xs"
        >
          <Plus size={12} /> Add step
        </button>
      )}
    </div>
  );
}

/* ─────────────────────────  IF block card  ──────────────────────────── */

/**
 * Renders an IF step as a collapsible card with two lanes: the "then"
 * branch (green-tinted) and the "else" branch (red-tinted). Each lane
 * renders its children via StepListSection — the recursion that makes
 * nested IFs work.
 *
 * The condition itself is shown as a one-line summary in the header.
 * Clicking the pencil opens an inline editor focused on the condition;
 * the action is locked (you can't turn an IF into a CLICK because that
 * would orphan the children).
 */
function IfStepCard({
  step, isEditingMeta, onEditMeta, onCancelEditMeta, onDelete, onSubmitMeta,
  addingAt, setAddingAt, editingStepId, setEditingStepId,
  addStep, updateStep, deleteStep, busy,
}: {
  step: WebStepView;
  isEditingMeta: boolean;
  onEditMeta: () => void;
  onCancelEditMeta: () => void;
  onDelete: () => void;
  onSubmitMeta: (b: WebStepUpdate) => void;
  addingAt: InsertSlot | null;
  setAddingAt: (s: InsertSlot | null) => void;
  editingStepId: number | null;
  setEditingStepId: (id: number | null) => void;
  busy: boolean;
} & StepListMutations) {
  const [collapsed, setCollapsed] = useState(false);
  const thenChildren = step.children.filter((c) => c.branchLabel === "then");
  const elseChildren = step.children.filter((c) => c.branchLabel === "else");
  const summary = step.condition ? describeCondition(step.condition) : "no condition";

  if (isEditingMeta) {
    return (
      <Card className="border-l-2 border-l-warning-500 bg-warning-500/5 p-3">
        <div className="text-[10px] uppercase tracking-wider font-semibold text-warning-500 mb-3 inline-flex items-center gap-1.5">
          <X size={11} className="cursor-pointer" onClick={onCancelEditMeta} /> Edit IF condition
        </div>
        <StepEditor
          initial={step}
          busy={busy}
          submitLabel="Save"
          onCancel={onCancelEditMeta}
          onSubmit={onSubmitMeta}
        />
      </Card>
    );
  }

  return (
    <Card className="border-l-2 border-l-warning-500 bg-warning-500/5 p-3 group">
      <div className="flex items-center gap-2">
        <button
          onClick={() => setCollapsed((v) => !v)}
          className="p-0.5 rounded text-warning-500 hover:text-ink-primary"
          title={collapsed ? "Expand" : "Collapse"}
        >
          {collapsed ? <ChevronRight size={14} /> : <ChevronDown size={14} />}
        </button>
        <span className="inline-flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider text-warning-500 rounded border border-warning-500/40 bg-warning-500/10 px-1.5 py-0.5">
          <GitBranch size={11} />
          IF
        </span>
        <span className="text-[11px] text-ink-secondary truncate font-mono">{summary}</span>
        <div className="ml-auto flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
          <button onClick={onEditMeta} className="p-1.5 rounded text-ink-muted hover:text-ink-primary hover:bg-surface-muted" title="Edit condition">
            <Pencil size={13} />
          </button>
          <button onClick={onDelete} className="p-1.5 rounded text-ink-muted hover:text-danger-500 hover:bg-surface-muted" title="Delete IF (and children)">
            <Trash2 size={13} />
          </button>
        </div>
      </div>

      {!collapsed && (
        <div className="mt-3 space-y-3">
          {/* THEN lane — always render StepListSection. With empty
              children it shows just the bottom "Add step" pill (and
              opens the NewStepCard inline on click). */}
          <div className="pl-3 border-l-2 border-l-success-500/40">
            <div className="text-[10px] uppercase tracking-wider font-semibold text-success-500 mb-2">Then</div>
            <StepListSection
              steps={thenChildren}
              parentId={step.id}
              branch="then"
              addingAt={addingAt}
              setAddingAt={setAddingAt}
              editingStepId={editingStepId}
              setEditingStepId={setEditingStepId}
              addStep={addStep}
              updateStep={updateStep}
              deleteStep={deleteStep}
            />
          </div>

          {/* ELSE lane — only shown if it has children OR the user is adding
              into it. Otherwise show an "Add ELSE branch" hint button. */}
          {elseChildren.length === 0 && !(addingAt?.parentId === step.id && addingAt.branch === "else") ? (
            <button
              onClick={() => setAddingAt({ parentId: step.id, branch: "else", position: 0 })}
              className="w-full inline-flex items-center justify-center gap-1.5 h-9 rounded-md border border-dashed border-surface-border text-ink-muted hover:text-ink-primary hover:border-danger-500/40 hover:bg-danger-500/5 transition-colors text-xs"
            >
              <Plus size={12} /> Add ELSE branch
            </button>
          ) : (
            <div className="pl-3 border-l-2 border-l-danger-500/40">
              <div className="text-[10px] uppercase tracking-wider font-semibold text-danger-500 mb-2">Else</div>
              <StepListSection
                steps={elseChildren}
                parentId={step.id}
                branch="else"
                addingAt={addingAt}
                setAddingAt={setAddingAt}
                editingStepId={editingStepId}
                setEditingStepId={setEditingStepId}
                addStep={addStep}
                updateStep={updateStep}
                deleteStep={deleteStep}
              />
            </div>
          )}
        </div>
      )}
    </Card>
  );
}

/** One-line human description of an IF condition for the card header. */
function describeCondition(c: NonNullable<WebStepView["condition"]>): string {
  if (c.type === "element_state") {
    const op =
      c.operator === "is_visible"    ? "is visible"
    : c.operator === "is_hidden"     ? "is hidden"
    : c.operator === "exists"        ? "exists"
    : c.operator === "text_contains" ? `text contains "${c.value ?? ""}"`
    : c.operator === "text_equals"   ? `text equals "${c.value ?? ""}"`
    :                                  c.operator;
    return `element #${c.subjectId} ${op}`;
  }
  if (c.type === "test_data_compare") {
    const op =
      c.operator === "equals"        ? "=="
    : c.operator === "not_equals"    ? "!="
    : c.operator === "contains"      ? "contains"
    : c.operator === "greater_than"  ? ">"
    : c.operator === "less_than"     ? "<"
    :                                  c.operator;
    return `data #${c.subjectId} ${op} "${c.value}"`;
  }
  return "(unknown condition)";
}
