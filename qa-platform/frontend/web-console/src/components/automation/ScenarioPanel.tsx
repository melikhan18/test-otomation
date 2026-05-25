import { Fragment, useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  DndContext, KeyboardSensor, PointerSensor, closestCenter,
  useSensor, useSensors, type DragEndEvent,
} from "@dnd-kit/core";
import {
  SortableContext, arrayMove, sortableKeyboardCoordinates, verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { restrictToVerticalAxis, restrictToParentElement } from "@dnd-kit/modifiers";
import {
  AlertTriangle, ChevronDown, ChevronRight, FileCheck2, FolderKanban, GitBranch,
  History, Link2, Pencil, Play, Plus, Trash2,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import RunDialog from "@/components/automation/RunDialog";
import StepCard from "@/components/automation/StepCard";
import StepEditor from "@/components/automation/StepEditor";
import {
  elementApi, scenarioApi, testDataApi,
  type ScenarioSummary, type ScenarioUpdate, type ScenarioView,
  type StepCreate, type StepUpdate, type StepView,
} from "@/lib/automation";

type Props = {
  scenarioId: number;
  onAfterDelete: () => void;
  onMutated: () => void;
  /** Click on a parent-suite chip jumps the workspace selection to that suite. */
  onSelectSuite?: (suiteId: number) => void;
};

/** Identifies WHERE in the step tree the user is about to insert. Both
 *  parentId + branch null = root level. Both set = inside an IF's then/else
 *  lane. position is the order_index within that scope. */
type InsertSlot = {
  parentId: number | null;
  branch: "then" | "else" | null;
  position: number;
};

function sameSlot(
  a: InsertSlot | null,
  b: { parentId: number | null; branch: "then" | "else" | null; position: number },
): boolean {
  return a != null && a.parentId === b.parentId && a.branch === b.branch && a.position === b.position;
}

export default function ScenarioPanel({ scenarioId, onAfterDelete, onMutated, onSelectSuite }: Props) {
  const qc = useQueryClient();
  const nav = useNavigate();

  const scenarioQ = useQuery({
    queryKey: ["automation-scenario", scenarioId],
    queryFn: () => scenarioApi.get(scenarioId),
    refetchOnWindowFocus: false,
  });
  const elementsQ = useQuery({ queryKey: ["automation-elements"], queryFn: () => elementApi.list() });
  const dataQ     = useQuery({ queryKey: ["automation-test-data-all"], queryFn: () => testDataApi.list() });

  const [localSteps, setLocalSteps] = useState<StepView[] | null>(null);
  const steps = localSteps ?? scenarioQ.data?.steps ?? [];

  function applyScenario(view: ScenarioView) {
    qc.setQueryData(["automation-scenario", scenarioId], view);
    setLocalSteps(null);
    onMutated();
  }

  const addStep    = useMutation({
    mutationFn: (b: StepCreate) => scenarioApi.addStep(scenarioId, b),
    onSuccess: applyScenario,
  });
  const updateStep = useMutation({
    mutationFn: ({ stepId, b }: { stepId: number; b: StepUpdate }) => scenarioApi.updateStep(scenarioId, stepId, b),
    onSuccess: applyScenario,
  });
  const deleteStep = useMutation({
    mutationFn: (stepId: number) => scenarioApi.deleteStep(scenarioId, stepId),
    onSuccess: applyScenario,
  });
  const reorder    = useMutation({
    mutationFn: (ids: number[]) => scenarioApi.reorderSteps(scenarioId, ids),
    onSuccess: applyScenario,
    onError: () => setLocalSteps(null),
  });
  const update = useMutation({
    mutationFn: (b: ScenarioUpdate) => scenarioApi.update(scenarioId, b),
    onSuccess: applyScenario,
  });
  const remove = useMutation({
    mutationFn: () => scenarioApi.delete(scenarioId),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["automation-workspace-tree"] }); onAfterDelete(); },
  });

  // Scope-aware insert slot for the tree editor. Tracks WHICH branch the
  // user is currently inserting into and at WHAT position within that
  // branch. parentId + branch both null = root level (legacy/flat scenario
  // behaviour). Both set = inside an IF's then/else lane.
  const [addingAt, setAddingAt] = useState<InsertSlot | null>(null);
  const [editingStepId, setEditingStepId] = useState<number | null>(null);
  const [editingMeta, setEditingMeta] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [running, setRunning] = useState(false);

  useEffect(() => {
    setLocalSteps(null);
    setAddingAt(null);
    setEditingStepId(null);
    setEditingMeta(false);
    setRunning(false);
  }, [scenarioId]);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  // Per-branch drag-and-drop is handled inside StepListSection — each
  // branch wraps its own DndContext + SortableContext so a step can't
  // drag across the IF boundary by accident.

  if (scenarioQ.isLoading) return <div className="p-6 text-ink-muted flex items-center gap-2 text-sm"><Spinner /> Loading scenario…</div>;
  if (scenarioQ.error || !scenarioQ.data) return <div className="p-6 text-danger-500">Scenario not found</div>;
  const scenario = scenarioQ.data;

  return (
    <div className="grid grid-cols-1 xl:grid-cols-[1fr_320px] gap-4">
      <div className="space-y-3 min-w-0">
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

              {/* Parent-suite linkage — shown when the scenario is referenced by ≥1 suite.
                  Reminds the user that edits propagate to every listed suite. */}
              {scenario.parentSuites.length > 0 && (
                <div className="mt-3 pt-3 border-t border-surface-border">
                  <div className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider font-semibold text-ink-muted mb-1.5">
                    <Link2 size={11} />
                    Used in {scenario.parentSuites.length} {scenario.parentSuites.length === 1 ? "suite" : "suites"}
                    <span className="text-warning-500 normal-case tracking-normal font-normal ml-1">
                      · edits affect all of them
                    </span>
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {scenario.parentSuites.map((p) => (
                      <button
                        key={p.id}
                        onClick={() => onSelectSuite ? onSelectSuite(p.id) : nav(`/automation/workspace?suite=${p.id}`)}
                        className="inline-flex items-center gap-1.5 text-[11px] px-2 py-1 rounded-md border border-warning-500/30 bg-warning-500/10 text-warning-500 hover:bg-warning-500/15 transition-colors"
                        title="Jump to this suite"
                      >
                        <FolderKanban size={11} />
                        <span className="font-medium">{p.name}</span>
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
            <div className="flex items-center gap-1.5">
              <Button
                variant="primary"
                size="sm"
                leftIcon={<Play size={12} />}
                disabled={steps.length === 0}
                onClick={() => setRunning(true)}
                title={steps.length === 0 ? "Add steps before running" : "Run on a device"}
              >
                Run
              </Button>
              <button
                onClick={() => nav(`/automation/runs?scenarioId=${scenarioId}`)}
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
            reorder={reorder}
            setLocalSteps={setLocalSteps}
            sensors={sensors}
            elements={elementsQ.data ?? []}
            testData={dataQ.data ?? []}
          />
        )}
      </div>

      <div className="space-y-3 min-w-0">
        <Card className="p-4">
          <div className="text-xs font-semibold text-ink-primary mb-1">Pseudocode</div>
          <div className="text-[11px] text-ink-muted mb-3">
            Human-readable Given/When/Then view.
          </div>
          <pre className="text-[11px] font-mono leading-relaxed whitespace-pre-wrap text-ink-secondary bg-surface border border-surface-border rounded-md p-3 max-h-[60vh] overflow-auto">
            {renderPseudocode(scenario)}
          </pre>
        </Card>
      </div>

      {editingMeta && (
        <ScenarioMetaEditor
          initial={{ ...scenario, stepCount: steps.length } as any}
          busy={update.isPending}
          onClose={() => setEditingMeta(false)}
          onSubmit={(b) => update.mutate(b, { onSuccess: () => setEditingMeta(false) })}
        />
      )}

      {confirmingDelete && (
        <DeleteScenarioDialog
          scenario={scenario}
          busy={remove.isPending}
          onCancel={() => setConfirmingDelete(false)}
          onConfirm={() => remove.mutate()}
        />
      )}

      {running && (
        <RunDialog
          scenarioId={scenarioId}
          scenarioName={scenario.name}
          onClose={() => setRunning(false)}
        />
      )}
    </div>
  );
}

/* ─────────────  Delete confirmation that surfaces blast radius  ─────────── */

function DeleteScenarioDialog({
  scenario, busy, onCancel, onConfirm,
}: {
  scenario: ScenarioView;
  busy?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const hasParents = scenario.parentSuites.length > 0;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-lg">
        <div className="px-5 py-4 border-b border-surface-border flex items-center gap-2">
          <AlertTriangle size={16} className="text-danger-500" />
          <div className="text-sm font-semibold">Delete scenario?</div>
        </div>
        <div className="p-5 space-y-3">
          <div className="text-sm text-ink-primary">
            <code className="font-mono">{scenario.name}</code> and its{" "}
            <strong>{scenario.steps.length} step{scenario.steps.length === 1 ? "" : "s"}</strong> will be removed.
          </div>
          {hasParents ? (
            <div className="rounded-md border border-warning-500/30 bg-warning-500/10 px-3 py-2 text-xs space-y-2">
              <div className="text-warning-500 font-semibold inline-flex items-center gap-1.5">
                <Link2 size={11} />
                This scenario is referenced by {scenario.parentSuites.length}{" "}
                {scenario.parentSuites.length === 1 ? "suite" : "suites"}:
              </div>
              <ul className="space-y-0.5">
                {scenario.parentSuites.map((p) => (
                  <li key={p.id} className="text-ink-secondary inline-flex items-center gap-1.5">
                    <FolderKanban size={10} className="text-warning-500" />
                    <span className="font-mono">{p.name}</span>
                  </li>
                ))}
              </ul>
              <div className="text-ink-muted text-[11px]">
                Suites will keep working but lose this scenario from their list.
              </div>
            </div>
          ) : (
            <div className="text-xs text-ink-muted">
              No suites reference this scenario — only the scenario row itself goes away.
            </div>
          )}
        </div>
        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel}>Cancel</Button>
          <Button variant="danger" loading={busy} onClick={onConfirm}>Delete scenario</Button>
        </div>
      </Card>
    </div>
  );
}

function renderPseudocode(s: ScenarioView): string {
  const lines: string[] = [];
  lines.push(`Scenario: ${s.name}`);
  if (s.preconditions) lines.push(`  Given ${s.preconditions}`);
  s.steps.forEach((st, i) => {
    const verb = i === 0 ? "When" : "And";
    lines.push(`  ${verb} ${describeStep(st)}`);
    if (st.expectedResult) {
      lines.push(`    → expected: ${st.expectedResult}`);
    }
  });
  return lines.join("\n");
}

function describeStep(st: StepView): string {
  const el = st.targetElement?.name ? `"${st.targetElement.name}"` : "(no element)";
  const v = () => st.data?.name ? `"${st.data.name}"` : JSON.stringify(st.literalValue ?? "");
  switch (st.action) {
    case "IF":                   return st.condition ? `if ${describeIfCondition(st.condition)}` : `if (no condition)`;
    case "CLICK":                return `tap ${el}`;
    case "LONG_PRESS":           return `long press ${el}` + (st.literalValue ? ` for ${st.literalValue}ms` : "");
    case "SWIPE":                return `swipe ${el}` + (st.literalValue ? ` ${st.literalValue}` : "");
    case "ENTER_TEXT":           return `type ${v()} into ${el}`;
    case "CLEAR":                return `clear ${el}`;
    case "PRESS_KEY":            return `press ${st.literalValue}`;
    case "WAIT_FOR_VISIBLE":     return `wait for ${el} to be visible (${st.timeoutMs}ms)`;
    case "WAIT_FOR_INVISIBLE":   return `wait for ${el} to disappear (${st.timeoutMs}ms)`;
    case "SLEEP":                return `sleep ${st.literalValue}ms`;
    // visibility / presence
    case "ASSERT_VISIBLE":       return `${el} is visible`;
    case "ASSERT_NOT_VISIBLE":   return `${el} is hidden`;
    case "ASSERT_NOT_PRESENT":   return `${el} is not present`;
    // state
    case "ASSERT_ENABLED":       return `${el} is enabled`;
    case "ASSERT_DISABLED":      return `${el} is disabled`;
    case "ASSERT_CHECKED":       return `${el} is checked`;
    case "ASSERT_UNCHECKED":     return `${el} is unchecked`;
    case "ASSERT_SELECTED":      return `${el} is selected`;
    case "ASSERT_FOCUSED":       return `${el} is focused`;
    // text + value
    case "ASSERT_TEXT_EQUALS":   return `${el} text equals ${v()}`;
    case "ASSERT_TEXT_CONTAINS": return `${el} text contains ${v()}`;
    case "ASSERT_TEXT_MATCHES":  return `${el} text matches ${v()}`;
    case "ASSERT_VALUE_EQUALS":  return `${el} value equals ${v()}`;
    case "ASSERT_ATTRIBUTE":     return `${el} attribute ${st.literalValue}`;
    // util
    case "SCREENSHOT":           return `screenshot "${st.literalValue ?? ""}"`;
    case "COMMENT":              return `# ${st.literalValue ?? ""}`;
  }
}

/** Pseudocode-friendly rendering of an IF predicate. */
function describeIfCondition(c: NonNullable<StepView["condition"]>): string {
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

function ScenarioMetaEditor({
  initial, busy, onClose, onSubmit,
}: {
  initial: ScenarioSummary;
  busy?: boolean;
  onClose: () => void;
  onSubmit: (body: ScenarioUpdate) => void;
}) {
  const [name, setName] = useState(initial.name);
  const [description, setDescription] = useState(initial.description ?? "");
  const [tagsInput, setTagsInput] = useState((initial.tags ?? []).join(", "));
  const [preconditions, setPreconditions] = useState((initial as any).preconditions ?? "");

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-lg">
        <div className="px-5 py-4 border-b border-surface-border">
          <div className="text-sm font-semibold">Edit scenario</div>
        </div>
        <div className="p-5 space-y-4">
          <label className="block">
            <span className="label block mb-1.5">Name</span>
            <input autoFocus value={name} onChange={(e) => setName(e.target.value)} className="input" />
          </label>
          <label className="block">
            <span className="label block mb-1.5">Description</span>
            <textarea value={description ?? ""} onChange={(e) => setDescription(e.target.value)}
              rows={3} className="input resize-y" />
          </label>
          <label className="block">
            <span className="label block mb-1.5">Preconditions (Given …)</span>
            <textarea value={preconditions ?? ""} onChange={(e) => setPreconditions(e.target.value)}
              rows={2} className="input resize-y" />
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
              description: description?.trim() || null,
              preconditions: preconditions?.trim() || null,
              tags: tagsInput.split(",").map((t) => t.trim()).filter(Boolean),
            })}
          >Save</Button>
        </div>
      </Card>
    </div>
  );
}

/* ─────────────────────────  Insert-between affordance  ─────────────────
 * A thin idle line that becomes a clickable "+ Insert step" pill on hover.
 * Sits between every pair of step cards (and above the first) so users can
 * splice a new step at any position instead of only appending to the end.
 * When `active` is true the line stays highlighted because the editor card
 * is rendered immediately below it.
 */
function InsertHereLine({ active, onClick }: { active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={
        "group relative w-full h-3 -my-1 flex items-center justify-center " +
        "outline-none focus-visible:ring-2 focus-visible:ring-brand-500/40 rounded"
      }
      title="Insert step here"
    >
      <span
        aria-hidden
        className={
          "absolute inset-x-0 top-1/2 -translate-y-1/2 h-px transition-colors " +
          (active ? "bg-brand-500/60" : "bg-transparent group-hover:bg-brand-500/40")
        }
      />
      <span
        aria-hidden
        className={
          "relative inline-flex items-center gap-1 text-[10px] uppercase tracking-wider font-semibold " +
          "px-2 py-0.5 rounded-full border bg-surface transition-opacity " +
          (active
            ? "border-brand-500/60 text-brand-300 opacity-100"
            : "border-surface-border text-ink-muted opacity-0 group-hover:opacity-100")
        }
      >
        <Plus size={10} /> Insert
      </span>
    </button>
  );
}

/* ─────────────────────────  New-step editor card  ─────────────────────── */

function NewStepCard({
  busy, elements, testData, onCancel, onSubmit,
}: {
  busy?: boolean;
  elements: any[];
  testData: any[];
  onCancel: () => void;
  onSubmit: (body: StepCreate) => void;
}) {
  return (
    <Card className="border-l-2 border-l-brand-500 bg-brand-500/5 p-3">
      <div className="text-[10px] uppercase tracking-wider font-semibold text-brand-300 mb-3">New step</div>
      <StepEditor
        elements={elements}
        testData={testData}
        busy={busy}
        submitLabel="Add step"
        onCancel={onCancel}
        onSubmit={(b) => onSubmit(b as StepCreate)}
      />
    </Card>
  );
}

/* ─────────────────────────  Recursive step list  ─────────────────────── */

type StepListMutations = {
  addStep:    ReturnType<typeof useMutation<ScenarioView, Error, StepCreate>>;
  updateStep: ReturnType<typeof useMutation<ScenarioView, Error, { stepId: number; b: StepUpdate }>>;
  deleteStep: ReturnType<typeof useMutation<ScenarioView, Error, number>>;
  reorder:    ReturnType<typeof useMutation<ScenarioView, Error, number[]>>;
};

/**
 * Renders one block of steps (root scenario body, or one branch of an IF).
 * Each section owns its own DndContext + SortableContext so DnD can't
 * cross the branch boundary — dnd-kit prevents drops into another
 * context's items entirely. IF rows render an IfStepCard which itself
 * contains two StepListSection instances (one per branch).
 */
function StepListSection({
  steps, parentId, branch,
  addingAt, setAddingAt, editingStepId, setEditingStepId,
  addStep, updateStep, deleteStep, reorder,
  setLocalSteps,
  sensors,
  elements, testData,
}: {
  steps: StepView[];
  parentId: number | null;
  branch: "then" | "else" | null;
  addingAt: InsertSlot | null;
  setAddingAt: (s: InsertSlot | null) => void;
  editingStepId: number | null;
  setEditingStepId: (id: number | null) => void;
  setLocalSteps: (s: StepView[] | null) => void;
  sensors: ReturnType<typeof useSensors>;
  elements: any[];
  testData: any[];
} & StepListMutations) {
  const slot = (position: number) => ({ parentId, branch, position });

  // Branch-scoped drag-and-drop. arrayMove + reorder.mutate operate on
  // THIS branch's siblings only — dnd-kit's SortableContext owns the
  // items array so over.id is guaranteed to be in this scope.
  function onDragEnd(e: DragEndEvent) {
    const { active, over } = e;
    if (!over || active.id === over.id) return;
    const oldIndex = steps.findIndex((s) => s.id === active.id);
    const newIndex = steps.findIndex((s) => s.id === over.id);
    if (oldIndex < 0 || newIndex < 0) return;
    const reordered = arrayMove(steps, oldIndex, newIndex);
    // Optimistic UI: paint the new order immediately, then let the
    // mutation reconcile. (Only the root-level setLocalSteps fires;
    // nested branches re-fetch via the scenario query on success.)
    if (parentId == null) {
      setLocalSteps(reordered.map((s, i) => ({ ...s, orderIndex: i })));
    }
    reorder.mutate(reordered.map((s) => s.id));
  }

  return (
    <div className="space-y-2">
      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}
                  modifiers={[restrictToVerticalAxis, restrictToParentElement]}>
        <SortableContext items={steps.map((s) => s.id)} strategy={verticalListSortingStrategy}>
          {/* Prepend insert-here line — only when there's at least one step.
              When empty, the bottom "Add step" pill covers position 0. */}
          {steps.length > 0 && (
            <>
              <InsertHereLine
                active={sameSlot(addingAt, slot(0))}
                onClick={() => setAddingAt(slot(0))}
              />
              {sameSlot(addingAt, slot(0)) && (
                <NewStepCard
                  busy={addStep.isPending}
                  elements={elements}
                  testData={testData}
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
                    { stepId: step.id, b: b as StepUpdate },
                    { onSuccess: () => setEditingStepId(null) },
                  )}
                  addingAt={addingAt}
                  setAddingAt={setAddingAt}
                  editingStepId={editingStepId}
                  setEditingStepId={setEditingStepId}
                  addStep={addStep}
                  updateStep={updateStep}
                  deleteStep={deleteStep}
                  reorder={reorder}
                  setLocalSteps={setLocalSteps}
                  sensors={sensors}
                  elements={elements}
                  testData={testData}
                />
              ) : (
                <StepCard
                  step={step}
                  isEditing={editingStepId === step.id}
                  onEdit={() => setEditingStepId(step.id)}
                  onCancelEdit={() => setEditingStepId(null)}
                  onDelete={() => { if (confirm(`Delete step #${step.orderIndex + 1}?`)) deleteStep.mutate(step.id); }}
                  editForm={
                    <StepEditor
                      initial={{
                        action: step.action,
                        targetElementId: step.targetElement?.id ?? null,
                        dataId: step.data?.id ?? null,
                        literalValue: step.literalValue,
                        expectedResult: step.expectedResult,
                        timeoutMs: step.timeoutMs,
                        retryCount: step.retryCount,
                        screenshotAfter: step.screenshotAfter,
                      }}
                      elements={elements}
                      testData={testData}
                      busy={updateStep.isPending}
                      submitLabel="Save"
                      onCancel={() => setEditingStepId(null)}
                      onSubmit={(b) => updateStep.mutate(
                        { stepId: step.id, b: b as StepUpdate },
                        { onSuccess: () => setEditingStepId(null) },
                      )}
                    />
                  }
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
                  elements={elements}
                  testData={testData}
                  onCancel={() => setAddingAt(null)}
                  onSubmit={(b) => addStep.mutate(
                    { ...b, position: idx + 1, parentStepId: parentId, branchLabel: branch },
                    { onSuccess: () => setAddingAt(null) },
                  )}
                />
              )}
            </Fragment>
          ))}
        </SortableContext>
      </DndContext>

      {/* Always-visible "Add step" pill at the bottom — also handles the
          empty-branch case (position 0 / slot(steps.length) collapse). */}
      {sameSlot(addingAt, slot(steps.length)) ? (
        <NewStepCard
          busy={addStep.isPending}
          elements={elements}
          testData={testData}
          onCancel={() => setAddingAt(null)}
          onSubmit={(b) => addStep.mutate(
            { ...b, parentStepId: parentId, branchLabel: branch },
            { onSuccess: () => setAddingAt(null) },
          )}
        />
      ) : (
        <button
          onClick={() => setAddingAt(slot(steps.length))}
          className="w-full h-10 rounded-md border border-dashed border-surface-border text-ink-secondary hover:text-ink-primary hover:border-brand-500/40 hover:bg-surface-muted/40 transition-colors text-xs inline-flex items-center justify-center gap-1.5"
        >
          <Plus size={13} /> Add step
        </button>
      )}
    </div>
  );
}

/* ─────────────────────────  IF block card  ──────────────────────────── */

function IfStepCard({
  step, isEditingMeta, onEditMeta, onCancelEditMeta, onDelete, onSubmitMeta,
  addingAt, setAddingAt, editingStepId, setEditingStepId,
  addStep, updateStep, deleteStep, reorder, setLocalSteps,
  sensors, elements, testData,
}: {
  step: StepView;
  isEditingMeta: boolean;
  onEditMeta: () => void;
  onCancelEditMeta: () => void;
  onDelete: () => void;
  onSubmitMeta: (b: StepUpdate) => void;
  addingAt: InsertSlot | null;
  setAddingAt: (s: InsertSlot | null) => void;
  editingStepId: number | null;
  setEditingStepId: (id: number | null) => void;
  setLocalSteps: (s: StepView[] | null) => void;
  sensors: ReturnType<typeof useSensors>;
  elements: any[];
  testData: any[];
} & StepListMutations) {
  const [collapsed, setCollapsed] = useState(false);
  const thenChildren = step.children.filter((c) => c.branchLabel === "then");
  const elseChildren = step.children.filter((c) => c.branchLabel === "else");
  const summary = step.condition ? describeIfCondition(step.condition) : "no condition";

  if (isEditingMeta) {
    return (
      <Card className="border-l-2 border-l-warning-500 bg-warning-500/5 p-3">
        <div className="text-[10px] uppercase tracking-wider font-semibold text-warning-500 mb-3 inline-flex items-center gap-1.5">
          Edit IF condition
        </div>
        <StepEditor
          initial={{
            action: step.action,
            targetElementId: step.targetElement?.id ?? null,
            dataId: step.data?.id ?? null,
            literalValue: step.literalValue,
            expectedResult: step.expectedResult,
            timeoutMs: step.timeoutMs,
            retryCount: step.retryCount,
            screenshotAfter: step.screenshotAfter,
            condition: step.condition,
          }}
          elements={elements}
          testData={testData}
          busy={updateStep.isPending}
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
          {/* THEN lane — always render. Its own StepListSection handles the
              empty case with its bottom "Add step" pill. */}
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
              reorder={reorder}
              setLocalSteps={setLocalSteps}
              sensors={sensors}
              elements={elements}
              testData={testData}
            />
          </div>

          {/* ELSE lane — opt-in. If empty + not currently being added to,
              show a pill to create the else branch. */}
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
                reorder={reorder}
                setLocalSteps={setLocalSteps}
                sensors={sensors}
                elements={elements}
                testData={testData}
              />
            </div>
          )}
        </div>
      )}
    </Card>
  );
}
