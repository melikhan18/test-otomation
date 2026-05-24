import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertTriangle, Camera, ChevronDown, ChevronRight, Globe, Play, Plus, Trash2, X,
} from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Spinner } from "@/components/ui/Spinner";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { cn } from "@/lib/cn";
import {
  browserApi, webRunApi, webScenarioApi, WEB_STEP_ACTIONS, WEB_STEP_ACTION_MAP,
  type BrowserProfile, type WebRunSummary, type WebScenarioSummary, type WebStepAction, type WebStepCreate, type WebStepView,
} from "@/lib/webAutomation";

/**
 * Consolidated web-platform page — single screen that handles scenario
 * browsing + step editing + run triggering + run inspection for the WEB
 * stack. Lighter than Android's multi-page workspace because v1 has no
 * suites, no element catalog, no test data. Once the WEB feature set
 * grows we'll split this into dedicated pages mirroring Android's
 * WorkspacePage / ScenarioPanel / RunDetailPage.
 */
export default function WebAutomationPage() {
  const [selectedScenarioId, setSelectedScenarioId] = useState<number | null>(null);
  const [runDialogOpen, setRunDialogOpen] = useState(false);
  const [expandedRunId, setExpandedRunId] = useState<number | null>(null);

  return (
    <div className="p-4 lg:p-6 space-y-4 max-w-7xl mx-auto">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold flex items-center gap-2">
            <Globe size={20} className="text-brand-400" />
            Web automation
          </h1>
          <p className="text-xs text-ink-muted mt-1">
            Playwright-driven browser tests. Scenarios run on server-side Chromium / Firefox / WebKit.
          </p>
        </div>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-[1fr,2fr] gap-4">
        <ScenariosPanel
          selectedId={selectedScenarioId}
          onSelect={setSelectedScenarioId}
        />
        <ScenarioDetailPanel
          scenarioId={selectedScenarioId}
          onRun={() => setRunDialogOpen(true)}
        />
      </div>

      <RunsPanel
        scenarioId={selectedScenarioId}
        expandedRunId={expandedRunId}
        onExpand={setExpandedRunId}
      />

      {runDialogOpen && selectedScenarioId != null && (
        <RunDialog
          scenarioId={selectedScenarioId}
          onClose={() => setRunDialogOpen(false)}
        />
      )}
    </div>
  );
}

/* ────────────────────────  Scenarios panel  ─────────────────────────── */

function ScenariosPanel({ selectedId, onSelect }: { selectedId: number | null; onSelect: (id: number) => void }) {
  const qc = useQueryClient();
  const listQ = useQuery({ queryKey: ["web-scenarios"], queryFn: webScenarioApi.list });

  const [newName, setNewName] = useState("");
  const create = useMutation({
    mutationFn: () => webScenarioApi.create({ name: newName.trim() }),
    onSuccess: (s) => {
      setNewName("");
      qc.invalidateQueries({ queryKey: ["web-scenarios"] });
      onSelect(s.id);
    },
  });
  const remove = useMutation({
    mutationFn: (id: number) => webScenarioApi.delete(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["web-scenarios"] }); },
  });

  return (
    <Card className="flex flex-col max-h-[480px]">
      <div className="px-4 py-3 border-b border-surface-border flex items-center justify-between">
        <div className="text-sm font-semibold">Scenarios</div>
        <span className="text-[10px] text-ink-muted">{listQ.data?.length ?? 0} total</span>
      </div>

      <div className="p-3 border-b border-surface-border">
        <div className="flex gap-1.5">
          <input
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter" && newName.trim()) create.mutate(); }}
            placeholder="New scenario name…"
            className="flex-1 h-8 px-2.5 rounded-md border border-surface-border bg-surface text-xs focus:outline-none focus:ring-1 focus:ring-brand-500"
          />
          <Button
            variant="primary"
            leftIcon={<Plus size={12} />}
            disabled={!newName.trim()}
            loading={create.isPending}
            onClick={() => create.mutate()}
          >
            Add
          </Button>
        </div>
      </div>

      <div className="flex-1 overflow-auto">
        {listQ.isLoading && <div className="p-4 text-xs text-ink-muted flex items-center gap-2"><Spinner /> Loading…</div>}
        {listQ.data?.length === 0 && (
          <div className="p-4 text-xs text-ink-muted">No scenarios yet. Create one above.</div>
        )}
        <ul className="divide-y divide-surface-border">
          {(listQ.data ?? []).map((s: WebScenarioSummary) => (
            <li
              key={s.id}
              onClick={() => onSelect(s.id)}
              className={cn(
                "px-3 py-2 cursor-pointer hover:bg-surface-muted/60 flex items-center justify-between gap-2",
                selectedId === s.id && "bg-brand-500/10",
              )}
            >
              <div className="min-w-0">
                <div className="text-sm truncate">{s.name}</div>
                <div className="text-[10px] text-ink-muted">
                  {s.stepCount} step{s.stepCount === 1 ? "" : "s"} · v{s.version}
                </div>
              </div>
              <button
                onClick={(e) => { e.stopPropagation(); if (confirm(`Delete "${s.name}"?`)) remove.mutate(s.id); }}
                className="text-ink-muted hover:text-danger-500 p-1 rounded hover:bg-danger-500/10"
                title="Delete"
              >
                <Trash2 size={12} />
              </button>
            </li>
          ))}
        </ul>
      </div>
    </Card>
  );
}

/* ────────────────────  Scenario detail (steps)  ─────────────────────── */

function ScenarioDetailPanel({ scenarioId, onRun }: { scenarioId: number | null; onRun: () => void }) {
  const qc = useQueryClient();
  const scQ = useQuery({
    queryKey: ["web-scenario", scenarioId],
    queryFn: () => webScenarioApi.get(scenarioId!),
    enabled: scenarioId != null,
  });
  const addStep = useMutation({
    mutationFn: (b: WebStepCreate) => webScenarioApi.addStep(scenarioId!, b),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["web-scenario", scenarioId] });
                       qc.invalidateQueries({ queryKey: ["web-scenarios"] }); },
  });
  const delStep = useMutation({
    mutationFn: (stepId: number) => webScenarioApi.deleteStep(scenarioId!, stepId),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["web-scenario", scenarioId] });
                       qc.invalidateQueries({ queryKey: ["web-scenarios"] }); },
  });

  if (scenarioId == null) {
    return (
      <Card className="h-[480px] flex items-center justify-center text-xs text-ink-muted">
        Pick or create a scenario on the left.
      </Card>
    );
  }
  if (scQ.isLoading) {
    return <Card className="h-[480px] flex items-center justify-center"><Spinner /></Card>;
  }
  if (!scQ.data) return null;

  return (
    <Card className="flex flex-col max-h-[480px]">
      <div className="px-4 py-3 border-b border-surface-border flex items-center justify-between">
        <div>
          <div className="text-sm font-semibold">{scQ.data.name}</div>
          <div className="text-[10px] text-ink-muted">{scQ.data.steps.length} step{scQ.data.steps.length === 1 ? "" : "s"} · v{scQ.data.version}</div>
        </div>
        <Button
          variant="primary"
          leftIcon={<Play size={12} />}
          disabled={scQ.data.steps.length === 0}
          onClick={onRun}
        >
          Run
        </Button>
      </div>

      <div className="flex-1 overflow-auto p-3 space-y-2">
        {scQ.data.steps.map((st: WebStepView) => (
          <StepRow key={st.id} step={st} onDelete={() => delStep.mutate(st.id)} />
        ))}

        <AddStepForm onAdd={(body) => addStep.mutate(body)} busy={addStep.isPending} />
      </div>
    </Card>
  );
}

function StepRow({ step, onDelete }: { step: WebStepView; onDelete: () => void }) {
  const def = WEB_STEP_ACTION_MAP[step.action];
  return (
    <div className="flex items-start gap-2 p-2.5 rounded-md border border-surface-border bg-surface">
      <div className="text-[10px] text-ink-muted w-5 text-right pt-1 font-mono">{step.orderIndex + 1}</div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5">
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
        </div>
        {step.selector && (
          <div className="text-[11px] text-ink-secondary mt-1 font-mono break-all">{step.selector}</div>
        )}
        {step.value && (
          <div className="text-[11px] text-ink-muted mt-0.5 font-mono break-all">→ {step.value}</div>
        )}
      </div>
      <button onClick={onDelete} className="text-ink-muted hover:text-danger-500 p-1 rounded hover:bg-danger-500/10" title="Delete step">
        <Trash2 size={12} />
      </button>
    </div>
  );
}

function AddStepForm({ onAdd, busy }: { onAdd: (b: WebStepCreate) => void; busy: boolean }) {
  const [action, setAction] = useState<WebStepAction>("GOTO");
  const [selector, setSelector] = useState("");
  const [value, setValue] = useState("");
  const def = WEB_STEP_ACTION_MAP[action];

  const grouped = useMemo(() => {
    const cats: Record<string, typeof WEB_STEP_ACTIONS> = {};
    for (const a of WEB_STEP_ACTIONS) (cats[a.category] ??= []).push(a);
    return cats;
  }, []);

  function submit() {
    if (def.needsSelector && !selector.trim()) return;
    if (def.needsValue && !value.trim()) return;
    onAdd({
      action,
      selector: def.needsSelector ? selector.trim() : null,
      value:    def.needsValue ? value.trim() : null,
    });
    setSelector("");
    setValue("");
  }

  return (
    <Card className="border-dashed border-surface-border">
      <div className="p-3 space-y-2">
        <div className="text-[10px] uppercase tracking-wider text-ink-muted">Add step</div>

        {/* Action picker — grouped by category */}
        <div className="space-y-1.5">
          {Object.entries(grouped).map(([cat, actions]) => (
            <div key={cat}>
              <div className="text-[9px] uppercase tracking-wider text-ink-muted mb-1">{cat}</div>
              <div className="flex flex-wrap gap-1">
                {actions.map((a) => (
                  <button
                    key={a.key}
                    onClick={() => setAction(a.key)}
                    className={cn(
                      "px-2 h-6 rounded-md text-[10px] font-medium border transition-colors",
                      action === a.key
                        ? "bg-brand-500/15 border-brand-500/40 text-brand-300"
                        : "border-surface-border text-ink-secondary hover:text-ink-primary",
                    )}
                  >
                    {a.label}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>

        {def.needsSelector && (
          <div>
            <span className="label block mb-1">Selector</span>
            <input
              value={selector}
              onChange={(e) => setSelector(e.target.value)}
              placeholder="CSS / XPath / text= / role= (Playwright syntax)"
              className="w-full h-7 px-2 rounded-md border border-surface-border bg-surface text-xs font-mono focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
          </div>
        )}

        {def.needsValue && (
          <div>
            <span className="label block mb-1">Value</span>
            <input
              value={value}
              onChange={(e) => setValue(e.target.value)}
              placeholder={def.valueLabel ?? ""}
              className="w-full h-7 px-2 rounded-md border border-surface-border bg-surface text-xs font-mono focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
          </div>
        )}

        <Button
          variant="primary"
          leftIcon={<Plus size={12} />}
          loading={busy}
          disabled={(def.needsSelector && !selector.trim()) || (def.needsValue && !value.trim())}
          onClick={submit}
        >
          Add step
        </Button>
      </div>
    </Card>
  );
}

/* ──────────────────────────  Runs panel  ────────────────────────────── */

function RunsPanel({ scenarioId, expandedRunId, onExpand }: { scenarioId: number | null; expandedRunId: number | null; onExpand: (id: number | null) => void }) {
  const listQ = useQuery({
    queryKey: ["web-runs", scenarioId],
    queryFn: () => webRunApi.list(scenarioId ?? undefined),
    refetchInterval: 5000,
  });

  return (
    <Card>
      <div className="px-4 py-3 border-b border-surface-border flex items-center justify-between">
        <div className="text-sm font-semibold">
          Runs {scenarioId != null && <span className="text-[10px] text-ink-muted font-normal">(for selected scenario)</span>}
        </div>
        <span className="text-[10px] text-ink-muted">{listQ.data?.length ?? 0}</span>
      </div>
      {listQ.data?.length === 0 && (
        <div className="px-4 py-6 text-xs text-ink-muted">No runs yet.</div>
      )}
      <ul className="divide-y divide-surface-border">
        {(listQ.data ?? []).map((r: WebRunSummary) => (
          <RunRow key={r.id} run={r} expanded={expandedRunId === r.id}
                  onToggle={() => onExpand(expandedRunId === r.id ? null : r.id)} />
        ))}
      </ul>
    </Card>
  );
}

function RunRow({ run, expanded, onToggle }: { run: WebRunSummary; expanded: boolean; onToggle: () => void }) {
  const detailQ = useQuery({
    queryKey: ["web-run", run.id],
    queryFn: () => webRunApi.get(run.id),
    enabled: expanded,
    refetchInterval: expanded && (run.status === "QUEUED" || run.status === "RUNNING") ? 2000 : false,
  });

  return (
    <li>
      <button
        onClick={onToggle}
        className="w-full px-4 py-2.5 flex items-center gap-3 hover:bg-surface-muted/60 text-left"
      >
        {expanded ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
        <StatusBadge tone={toneOf(run.status)}>{run.status}</StatusBadge>
        <div className="flex-1 min-w-0">
          <div className="text-sm truncate">
            {run.scenarioName ?? `#${run.scenarioId}`}{" "}
            <span className="text-[10px] text-ink-muted">on {run.browserProfileId}</span>
          </div>
          <div className="text-[10px] text-ink-muted">
            {run.passedSteps}/{run.totalSteps} passed
            {run.durationMs != null && <> · {(run.durationMs / 1000).toFixed(1)}s</>}
            {run.finishedAt && <> · {new Date(run.finishedAt).toLocaleString()}</>}
          </div>
        </div>
        {run.videoUrl && (
          <a href={run.videoUrl} target="_blank" rel="noreferrer" onClick={(e) => e.stopPropagation()}
             className="text-[10px] text-brand-400 hover:text-brand-300">video</a>
        )}
        {run.traceUrl && (
          <a href={run.traceUrl} target="_blank" rel="noreferrer" onClick={(e) => e.stopPropagation()}
             className="text-[10px] text-brand-400 hover:text-brand-300">trace</a>
        )}
      </button>

      {expanded && (
        <div className="px-4 pb-3 pt-1 bg-surface/50 border-t border-surface-border">
          {detailQ.isLoading && <div className="text-xs text-ink-muted flex items-center gap-2 py-2"><Spinner /> Loading…</div>}
          {detailQ.data && (
            <>
              {detailQ.data.errorSummary && (
                <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs mb-2 flex items-center gap-2">
                  <AlertTriangle size={12} /> {detailQ.data.errorSummary}
                </div>
              )}
              <ul className="space-y-1">
                {detailQ.data.stepResults.map((s) => (
                  <li key={s.id} className="flex items-center gap-2 text-xs">
                    <StatusBadge tone={toneOf(s.status)}>{s.status}</StatusBadge>
                    <span className="text-ink-muted font-mono w-5 text-right">{s.orderIndex + 1}</span>
                    <span className="font-medium">{WEB_STEP_ACTION_MAP[s.action]?.label ?? s.action}</span>
                    {s.errorMessage && <span className="text-danger-500 text-[11px] truncate">— {s.errorMessage}</span>}
                    {s.durationMs != null && <span className="text-ink-muted text-[10px] ml-auto">{s.durationMs}ms</span>}
                    {s.screenshotUrl && (
                      <a href={s.screenshotUrl} target="_blank" rel="noreferrer"
                         className="text-brand-400 hover:text-brand-300" title="Screenshot at failure">
                        <Camera size={12} />
                      </a>
                    )}
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}
    </li>
  );
}

function toneOf(status: string): "success" | "danger" | "warning" | "neutral" {
  switch (status) {
    case "PASSED":    return "success";
    case "FAILED":
    case "ERROR":     return "danger";
    case "RUNNING":
    case "QUEUED":    return "warning";
    default:          return "neutral";
  }
}

/* ──────────────────────────  Run dialog  ────────────────────────────── */

function RunDialog({ scenarioId, onClose }: { scenarioId: number; onClose: () => void }) {
  const qc = useQueryClient();
  const browsersQ = useQuery({ queryKey: ["browsers"], queryFn: browserApi.list });
  const [profileId, setProfileId] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () => webRunApi.create({ scenarioId, browserProfileId: profileId! }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["web-runs"] });
      onClose();
    },
  });
  const err = (create.error as any)?.response?.data?.detail;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-2xl flex flex-col max-h-[90vh]">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div className="text-sm font-semibold flex items-center gap-2">
            <Play size={14} className="text-success-500" /> Run on browser
          </div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted"><X size={14} /></button>
        </div>

        <div className="p-5 space-y-3 overflow-auto">
          <span className="label block mb-1.5">Browser profile</span>
          {browsersQ.isLoading && <div className="text-xs text-ink-muted flex items-center gap-2"><Spinner /> Loading…</div>}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            {(browsersQ.data ?? []).map((b: BrowserProfile) => (
              <button
                key={b.id}
                onClick={() => setProfileId(b.id)}
                className={cn(
                  "text-left px-3 py-2 rounded-md border transition-colors",
                  profileId === b.id
                    ? "border-brand-500/50 bg-brand-500/10"
                    : "border-surface-border hover:border-brand-500/30 bg-surface",
                )}
              >
                <div className="text-sm font-medium">{b.displayName}</div>
                <div className="text-[10px] text-ink-muted">
                  {b.engine} · {b.width}×{b.height}{b.isMobile && " · mobile"}
                </div>
              </button>
            ))}
          </div>

          {err && (
            <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">
              {err}
            </div>
          )}
        </div>

        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="primary" leftIcon={<Play size={12} />} disabled={!profileId} loading={create.isPending}
                  onClick={() => create.mutate()}>
            Run
          </Button>
        </div>
      </Card>
    </div>
  );
}
