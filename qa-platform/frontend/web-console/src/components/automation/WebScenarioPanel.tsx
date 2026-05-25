import { Fragment, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import {
  FileCheck2, History, Pencil, Play, Plus, Trash2, X,
} from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import ActionPicker from "@/components/automation/ActionPicker";
import { cn } from "@/lib/cn";
import {
  browserApi, webElementApi, webRunApi, webScenarioApi, webTestDataApi,
  WEB_STEP_ACTIONS, WEB_STEP_ACTION_MAP,
  type BrowserProfile, type WebElementView, type WebScenarioUpdate,
  type WebStepAction, type WebStepCreate, type WebStepUpdate, type WebStepView, type WebTestDataView,
} from "@/lib/webAutomation";

/** One-click quick picks above the popover — cover the bulk of typical
 *  Playwright-style web scripts. Anything else stays one search away. */
const WEB_QUICK_PICKS: WebStepAction[] = [
  "GOTO", "CLICK", "FILL", "WAIT_FOR_SELECTOR", "ASSERT_VISIBLE", "ASSERT_TEXT_CONTAINS",
];

function humaniseWebCategory(key: string): string {
  switch (key) {
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

  // addingAt holds the 0-based insert index when the user has opened a "new
  // step" editor — null means no editor is open. Insertion at index N pushes
  // every step previously at order≥N one slot down, so any value from 0 to
  // steps.length is a valid drop point.
  const [addingAt, setAddingAt] = useState<number | null>(null);
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

      {/* ── Steps ────────────────────────────────────────────────────── */}
      {steps.length === 0 && addingAt == null ? (
        <Card>
          <EmptyState
            icon={<Plus size={20} />}
            title="No steps yet"
            description="Add the first step. You can pick an element from the repository and a value from test data."
            action={
              <Button variant="primary" size="sm" leftIcon={<Plus size={14} />} onClick={() => setAddingAt(0)}>
                Add step
              </Button>
            }
          />
        </Card>
      ) : (
        <div className="space-y-2">
          {/* Prepend affordance — present once there's at least one step. */}
          {steps.length > 0 && (
            <InsertHereLine
              active={addingAt === 0}
              onClick={() => setAddingAt(0)}
            />
          )}
          {addingAt === 0 && (
            <NewStepCard
              busy={addStep.isPending}
              onCancel={() => setAddingAt(null)}
              onSubmit={(b) => addStep.mutate(
                { ...(b as WebStepCreate), position: 0 },
                { onSuccess: () => setAddingAt(null) },
              )}
            />
          )}
          {steps.map((step, idx) => (
            <Fragment key={step.id}>
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
              {/* Insert-here line between step idx and idx+1 — last step uses
                  the always-visible "Add step" pill below instead. */}
              {idx < steps.length - 1 && (
                <InsertHereLine
                  active={addingAt === idx + 1}
                  onClick={() => setAddingAt(idx + 1)}
                />
              )}
              {addingAt === idx + 1 && idx < steps.length - 1 && (
                <NewStepCard
                  busy={addStep.isPending}
                  onCancel={() => setAddingAt(null)}
                  onSubmit={(b) => addStep.mutate(
                    { ...(b as WebStepCreate), position: idx + 1 },
                    { onSuccess: () => setAddingAt(null) },
                  )}
                />
              )}
            </Fragment>
          ))}

          {/* Always-visible "Add step" pill at the bottom. Appends when
              activated (position omitted = end of list). */}
          {addingAt === steps.length ? (
            <NewStepCard
              busy={addStep.isPending}
              onCancel={() => setAddingAt(null)}
              onSubmit={(b) => addStep.mutate(
                b as WebStepCreate,
                { onSuccess: () => setAddingAt(null) },
              )}
            />
          ) : (
            <button
              onClick={() => setAddingAt(steps.length)}
              className="w-full inline-flex items-center justify-center gap-1.5 h-9 rounded-md border border-dashed border-surface-border text-ink-secondary hover:text-ink-primary hover:border-brand-500/40 hover:bg-surface-muted/40 transition-colors text-xs"
            >
              <Plus size={12} /> Add step
            </button>
          )}
        </div>
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
            <span className={cn(
              "inline-flex items-center text-[10px] font-medium uppercase tracking-wider rounded px-1.5 py-0.5 border",
              def?.tone === "blue"   && "border-brand-500/40   bg-brand-500/10   text-brand-300",
              def?.tone === "green"  && "border-success-500/40 bg-success-500/10 text-success-500",
              def?.tone === "amber"  && "border-warning-500/40 bg-warning-500/10 text-warning-500",
              def?.tone === "violet" && "border-danger-500/30  bg-danger-500/10  text-danger-500",
              def?.tone === "gray"   && "border-surface-border bg-surface-muted  text-ink-secondary",
            )}>
              {def?.label ?? step.action}
            </span>
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

  const def = WEB_STEP_ACTION_MAP[action];
  const elementsQ = useQuery({ queryKey: ["web-elements"], queryFn: webElementApi.list });
  const dataQ     = useQuery({ queryKey: ["web-test-data"], queryFn: webTestDataApi.list });

  function submit() {
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

      {def.needsSelector && (
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

      {def.needsValue && (
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

      <div>
        <span className="label block mb-1.5">Timeout (ms)</span>
        <input type="number" min={0} value={timeoutMs} onChange={(e) => setTimeoutMs(Number(e.target.value) || 0)}
               className="input text-xs font-mono w-32" />
      </div>

      <div className="flex justify-end gap-2 pt-1">
        <Button variant="secondary" size="sm" onClick={onCancel}>Cancel</Button>
        <Button variant="primary" size="sm" loading={busy} onClick={submit}
                disabled={
                  (def.needsSelector && selectorMode === "literal" && !selector.trim()) ||
                  (def.needsSelector && selectorMode === "catalog" && targetElementId == null) ||
                  (def.needsValue && valueMode === "literal" && !value.trim()) ||
                  (def.needsValue && valueMode === "catalog" && dataId == null)
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
