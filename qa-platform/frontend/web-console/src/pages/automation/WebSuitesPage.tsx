import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ChevronDown, ChevronRight, FolderKanban, Pencil, Play, Plus, Trash2, X,
} from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { cn } from "@/lib/cn";
import {
  browserApi, webScenarioApi, webSuiteApi, webSuiteRunApi,
  type BrowserProfile, type WebSuiteRunSummary, type WebSuiteSummary, type WebSuiteView,
} from "@/lib/webAutomation";

/**
 * Web platform's suite editor — analog of Android's suite section. Two
 * columns: left = suite list, right = selected suite's scenarios with
 * add/remove + run controls.
 */
export default function WebSuitesPage() {
  const qc = useQueryClient();
  const listQ = useQuery({ queryKey: ["web-suites"], queryFn: webSuiteApi.list });

  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [newName, setNewName] = useState("");
  const [runDialogOpen, setRunDialogOpen] = useState(false);

  const create = useMutation({
    mutationFn: () => webSuiteApi.create({ name: newName.trim() }),
    onSuccess: (s) => { setNewName(""); qc.invalidateQueries({ queryKey: ["web-suites"] }); setSelectedId(s.id); },
  });
  const remove = useMutation({
    mutationFn: (id: number) => webSuiteApi.delete(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["web-suites"] }); setSelectedId(null); },
  });

  return (
    <div className="p-4 lg:p-6 max-w-7xl mx-auto space-y-4">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold flex items-center gap-2">
            <FolderKanban size={20} className="text-brand-400" />
            Suites
          </h1>
          <p className="text-xs text-ink-muted mt-1">
            Sequential bundles of scenarios — single click runs them on the same browser type back-to-back.
          </p>
        </div>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-[1fr,2fr] gap-4">
        <Card className="flex flex-col max-h-[600px]">
          <div className="px-4 py-3 border-b border-surface-border flex items-center justify-between">
            <div className="text-sm font-semibold">Suites</div>
            <span className="text-[10px] text-ink-muted">{listQ.data?.length ?? 0}</span>
          </div>
          <div className="p-3 border-b border-surface-border">
            <div className="flex gap-1.5">
              <input value={newName} onChange={(e) => setNewName(e.target.value)}
                     onKeyDown={(e) => { if (e.key === "Enter" && newName.trim()) create.mutate(); }}
                     placeholder="New suite name…"
                     className="flex-1 h-8 px-2.5 rounded-md border border-surface-border bg-surface text-xs focus:outline-none focus:ring-1 focus:ring-brand-500" />
              <Button variant="primary" leftIcon={<Plus size={12} />} disabled={!newName.trim()} loading={create.isPending} onClick={() => create.mutate()}>
                Add
              </Button>
            </div>
          </div>
          <div className="flex-1 overflow-auto">
            {listQ.isLoading && <div className="p-4 text-xs text-ink-muted"><Spinner /> Loading…</div>}
            {listQ.data?.length === 0 && (
              <div className="p-4 text-xs text-ink-muted">No suites yet.</div>
            )}
            <ul className="divide-y divide-surface-border">
              {(listQ.data ?? []).map((s: WebSuiteSummary) => (
                <li key={s.id} onClick={() => setSelectedId(s.id)}
                    className={cn("px-3 py-2 cursor-pointer hover:bg-surface-muted/60 flex items-center justify-between gap-2",
                      selectedId === s.id && "bg-brand-500/10")}>
                  <div className="min-w-0">
                    <div className="text-sm truncate">{s.name}</div>
                    <div className="text-[10px] text-ink-muted">{s.scenarioCount} scenario{s.scenarioCount === 1 ? "" : "s"}</div>
                  </div>
                  <button onClick={(e) => { e.stopPropagation(); if (confirm(`Delete "${s.name}"?`)) remove.mutate(s.id); }}
                          className="text-ink-muted hover:text-danger-500 p-1 rounded hover:bg-danger-500/10"
                          title="Delete"><Trash2 size={12} /></button>
                </li>
              ))}
            </ul>
          </div>
        </Card>

        <SuiteDetailPanel suiteId={selectedId} onRun={() => setRunDialogOpen(true)} />
      </div>

      <SuiteRunsPanel suiteId={selectedId} />

      {runDialogOpen && selectedId != null && (
        <SuiteRunDialog suiteId={selectedId} onClose={() => setRunDialogOpen(false)} />
      )}
    </div>
  );
}

/* ────────────────────────  Suite detail (scenarios)  ──────────────────── */

function SuiteDetailPanel({ suiteId, onRun }: { suiteId: number | null; onRun: () => void }) {
  const qc = useQueryClient();
  const detailQ = useQuery({
    queryKey: ["web-suite", suiteId],
    queryFn: () => webSuiteApi.get(suiteId!),
    enabled: suiteId != null,
  });
  const scenariosQ = useQuery({
    queryKey: ["web-scenarios"],
    queryFn: webScenarioApi.list,
    enabled: suiteId != null,
  });

  const [addId, setAddId] = useState<string>("");

  const add = useMutation({
    mutationFn: () => webSuiteApi.addScenario(suiteId!, Number(addId)),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["web-suite", suiteId] }); setAddId(""); },
  });
  const rm = useMutation({
    mutationFn: (scenarioId: number) => webSuiteApi.removeScenario(suiteId!, scenarioId),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["web-suite", suiteId] }); },
  });

  if (suiteId == null) {
    return <Card className="h-[600px] flex items-center justify-center text-xs text-ink-muted">Pick or create a suite on the left.</Card>;
  }
  if (detailQ.isLoading) return <Card className="h-[600px] flex items-center justify-center"><Spinner /></Card>;
  if (!detailQ.data) return null;

  const suite: WebSuiteView = detailQ.data;
  const candidates = (scenariosQ.data ?? []).filter((sc) => !suite.scenarios.some((ref) => ref.scenarioId === sc.id));

  return (
    <Card className="flex flex-col max-h-[600px]">
      <div className="px-4 py-3 border-b border-surface-border flex items-center justify-between">
        <div>
          <div className="text-sm font-semibold">{suite.name}</div>
          <div className="text-[10px] text-ink-muted">{suite.scenarios.length} scenario{suite.scenarios.length === 1 ? "" : "s"}</div>
        </div>
        <Button variant="primary" leftIcon={<Play size={12} />} disabled={suite.scenarios.length === 0} onClick={onRun}>
          Run suite
        </Button>
      </div>

      <div className="flex-1 overflow-auto p-3 space-y-2">
        {suite.scenarios.map((ref) => (
          <div key={ref.scenarioId} className="flex items-center gap-2 p-2.5 rounded-md border border-surface-border bg-surface">
            <div className="text-[10px] text-ink-muted w-5 text-right font-mono">{ref.orderIndex + 1}</div>
            <div className="flex-1 min-w-0">
              <div className="text-sm truncate">{ref.name}</div>
              <div className="text-[10px] text-ink-muted">{ref.stepCount} step{ref.stepCount === 1 ? "" : "s"}</div>
            </div>
            <button onClick={() => rm.mutate(ref.scenarioId)}
                    className="text-ink-muted hover:text-danger-500 p-1 rounded hover:bg-danger-500/10"
                    title="Remove from suite"><Trash2 size={12} /></button>
          </div>
        ))}

        {candidates.length > 0 && (
          <div className="flex items-center gap-1.5 pt-2 border-t border-surface-border mt-2">
            <select value={addId} onChange={(e) => setAddId(e.target.value)}
                    className="flex-1 h-7 px-2 rounded-md border border-surface-border bg-surface text-xs focus:outline-none focus:ring-1 focus:ring-brand-500">
              <option value="">Add scenario to suite…</option>
              {candidates.map((sc) => <option key={sc.id} value={sc.id}>{sc.name}</option>)}
            </select>
            <Button variant="primary" leftIcon={<Plus size={11} />} disabled={!addId} loading={add.isPending} onClick={() => add.mutate()}>
              Add
            </Button>
          </div>
        )}
      </div>
    </Card>
  );
}

/* ─────────────────────────  Suite runs panel  ────────────────────────── */

function SuiteRunsPanel({ suiteId }: { suiteId: number | null }) {
  const [expanded, setExpanded] = useState<number | null>(null);
  const listQ = useQuery({
    queryKey: ["web-suite-runs", suiteId],
    queryFn: () => webSuiteRunApi.list(suiteId ?? undefined),
    refetchInterval: 5000,
  });

  return (
    <Card>
      <div className="px-4 py-3 border-b border-surface-border flex items-center justify-between">
        <div className="text-sm font-semibold">
          Suite runs {suiteId != null && <span className="text-[10px] text-ink-muted font-normal">(filtered)</span>}
        </div>
        <span className="text-[10px] text-ink-muted">{listQ.data?.length ?? 0}</span>
      </div>
      {listQ.data?.length === 0 && (
        <div className="px-4 py-6 text-xs text-ink-muted">No suite runs yet.</div>
      )}
      <ul className="divide-y divide-surface-border">
        {(listQ.data ?? []).map((sr: WebSuiteRunSummary) => (
          <SuiteRunRow key={sr.id} sr={sr} expanded={expanded === sr.id}
                       onToggle={() => setExpanded(expanded === sr.id ? null : sr.id)} />
        ))}
      </ul>
    </Card>
  );
}

function SuiteRunRow({ sr, expanded, onToggle }: { sr: WebSuiteRunSummary; expanded: boolean; onToggle: () => void }) {
  const detailQ = useQuery({
    queryKey: ["web-suite-run", sr.id],
    queryFn: () => webSuiteRunApi.get(sr.id),
    enabled: expanded,
    refetchInterval: expanded && (sr.status === "QUEUED" || sr.status === "RUNNING") ? 2000 : false,
  });
  return (
    <li>
      <button onClick={onToggle} className="w-full px-4 py-2.5 flex items-center gap-3 hover:bg-surface-muted/60 text-left">
        {expanded ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
        <StatusBadge tone={toneOf(sr.status)}>{sr.status}</StatusBadge>
        <div className="flex-1 min-w-0">
          <div className="text-sm truncate">
            {sr.suiteName ?? `Suite #${sr.suiteId}`}
            <span className="text-[10px] text-ink-muted ml-2">on {sr.browserProfileId}</span>
          </div>
          <div className="text-[10px] text-ink-muted">
            {sr.passedScenarios}/{sr.totalScenarios} passed
            {sr.durationMs != null && <> · {(sr.durationMs / 1000).toFixed(1)}s</>}
            {sr.finishedAt && <> · {new Date(sr.finishedAt).toLocaleString()}</>}
          </div>
        </div>
      </button>
      {expanded && detailQ.data && (
        <div className="px-4 pb-3 pt-1 bg-surface/50 border-t border-surface-border">
          <ul className="space-y-1">
            {detailQ.data.runs.map((r) => (
              <li key={r.id} className="flex items-center gap-2 text-xs">
                <StatusBadge tone={toneOf(r.status)}>{r.status}</StatusBadge>
                <span className="font-medium truncate">{r.scenarioName ?? `#${r.scenarioId}`}</span>
                <span className="text-ink-muted text-[10px] ml-auto">
                  {r.passedSteps}/{r.totalSteps} · {r.durationMs != null ? `${r.durationMs}ms` : ""}
                </span>
                {r.videoUrl && <a href={r.videoUrl} target="_blank" rel="noreferrer" onClick={(e) => e.stopPropagation()} className="text-brand-400">video</a>}
                {r.traceUrl && <a href={r.traceUrl} target="_blank" rel="noreferrer" onClick={(e) => e.stopPropagation()} className="text-brand-400">trace</a>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </li>
  );
}

function toneOf(status: string): "success" | "danger" | "warning" | "neutral" {
  switch (status) {
    case "PASSED": return "success";
    case "FAILED":
    case "ERROR":  return "danger";
    case "RUNNING":
    case "QUEUED": return "warning";
    default:       return "neutral";
  }
}

/* ────────────────────────  Suite run dialog  ─────────────────────────── */

function SuiteRunDialog({ suiteId, onClose }: { suiteId: number; onClose: () => void }) {
  const qc = useQueryClient();
  const browsersQ = useQuery({ queryKey: ["browsers"], queryFn: browserApi.list });
  const [profileId, setProfileId] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () => webSuiteRunApi.create({ suiteId, browserProfileId: profileId! }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["web-suite-runs"] }); onClose(); },
  });
  const err = (create.error as any)?.response?.data?.detail;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-2xl flex flex-col max-h-[90vh]">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div className="text-sm font-semibold flex items-center gap-2">
            <Play size={14} className="text-success-500" /> Run suite
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
            Run suite
          </Button>
        </div>
      </Card>
    </div>
  );
}
