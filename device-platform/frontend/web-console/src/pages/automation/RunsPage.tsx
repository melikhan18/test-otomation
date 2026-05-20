import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import {
  AlertOctagon, CheckCircle2, Clock, Hourglass, PauseCircle, RefreshCcw, XCircle,
} from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { runApi, type RunStatus, type RunSummary } from "@/lib/automation";

/**
 * Recent runs list. Polls every 3s so newly-finished runs surface without manual
 * refresh; falls back to a slower cadence when nothing is in flight.
 */
export default function RunsPage() {
  const nav = useNavigate();
  const [params, setParams] = useSearchParams();
  const scenarioId = params.get("scenarioId") ? Number(params.get("scenarioId")) : undefined;

  const runsQ = useQuery({
    queryKey: ["automation-runs", scenarioId ?? null],
    queryFn: () => runApi.list(scenarioId),
    refetchInterval: (q) => {
      const live = (q.state.data ?? []).some((r) => r.status === "QUEUED" || r.status === "RUNNING");
      return live ? 1500 : 6000;
    },
    refetchOnWindowFocus: false,
  });

  const runs = runsQ.data ?? [];
  const liveCount = useMemo(
    () => runs.filter((r) => r.status === "QUEUED" || r.status === "RUNNING").length,
    [runs],
  );

  function clearFilter() {
    const next = new URLSearchParams(params);
    next.delete("scenarioId");
    setParams(next, { replace: true });
  }

  return (
    <>
      <TopBar
        crumbs={[{ label: "Automation", to: "/automation" }, { label: "Runs" }]}
        actions={
          <Button
            variant="ghost"
            size="sm"
            leftIcon={<RefreshCcw size={12} className={runsQ.isFetching ? "animate-spin" : undefined} />}
            onClick={() => runsQ.refetch()}
          >
            Refresh
          </Button>
        }
      />

      <div className="px-6 py-6 space-y-4">
        <Card className="p-4">
          <div className="flex items-center gap-3 flex-wrap text-xs">
            <span className="text-ink-secondary">
              <strong className="text-ink-primary text-sm">{runs.length}</strong> recent run{runs.length === 1 ? "" : "s"}
            </span>
            {liveCount > 0 && (
              <StatusBadge tone="info">{liveCount} live</StatusBadge>
            )}
            {scenarioId != null && (
              <button
                onClick={clearFilter}
                className="inline-flex items-center gap-1 px-2 h-6 rounded-md border border-brand-500/40 bg-brand-500/10 text-brand-300 hover:bg-brand-500/15"
                title="Clear scenario filter"
              >
                scenario #{scenarioId} ×
              </button>
            )}
          </div>
        </Card>

        {runsQ.isLoading ? (
          <div className="text-ink-muted text-sm flex items-center gap-2"><Spinner /> Loading runs…</div>
        ) : runs.length === 0 ? (
          <Card>
            <EmptyState
              icon={<Hourglass size={20} />}
              title="No runs yet"
              description="Open a scenario in the workspace and hit Run to execute it on a device."
              action={
                <Button variant="primary" size="sm" onClick={() => nav("/automation/workspace")}>
                  Open workspace
                </Button>
              }
            />
          </Card>
        ) : (
          <div className="space-y-1.5">
            {runs.map((r) => <RunRow key={r.id} run={r} />)}
          </div>
        )}
      </div>
    </>
  );
}

function RunRow({ run }: { run: RunSummary }) {
  const tone = statusTone(run.status);
  const Icon = statusIcon(run.status);
  const total = run.totalSteps || 0;
  const passPct = total > 0 ? (run.passedSteps / total) * 100 : 0;
  const failPct = total > 0 ? (run.failedSteps / total) * 100 : 0;
  return (
    <Link
      to={`/automation/runs/${run.id}`}
      className="block rounded-md border border-surface-border bg-surface hover:border-brand-500/30 hover:bg-surface-muted/40 transition-colors"
    >
      <div className="px-4 py-3 flex items-center gap-4 flex-wrap">
        <div className="flex items-center gap-2 min-w-0 flex-1">
          <Icon size={16} className={statusColor(run.status)} />
          <div className="min-w-0">
            <div className="text-sm font-medium truncate">
              {run.scenarioName ?? "(scenario deleted)"}
              <span className="text-[10px] text-ink-muted font-mono ml-2">#{run.id}</span>
            </div>
            <div className="text-[11px] text-ink-muted flex items-center gap-2.5 mt-0.5 flex-wrap">
              <span>Device #{run.deviceId ?? "?"}</span>
              <span>env <code className="font-mono">{run.environment}</code></span>
              <span>{new Date(run.createdAt).toLocaleString()}</span>
              {run.durationMs != null && (
                <span className="inline-flex items-center gap-1">
                  <Clock size={10} /> {(run.durationMs / 1000).toFixed(1)}s
                </span>
              )}
            </div>
          </div>
        </div>

        <div className="flex items-center gap-3 shrink-0">
          <div className="hidden sm:flex flex-col items-end">
            <div className="text-[11px] text-ink-muted font-mono">
              {run.passedSteps}/{total} passed{run.failedSteps > 0 ? ` · ${run.failedSteps} failed` : ""}
            </div>
            {total > 0 && (
              <div className="mt-1 h-1 w-32 rounded-full bg-surface-muted overflow-hidden flex">
                <div className="bg-success-500" style={{ width: `${passPct}%` }} />
                <div className="bg-danger-500"  style={{ width: `${failPct}%` }} />
              </div>
            )}
          </div>
          <StatusBadge tone={tone}>{run.status}</StatusBadge>
        </div>
      </div>
    </Link>
  );
}

/* ─────────────────────────────  helpers  ───────────────────────────── */

function statusTone(s: RunStatus): "success" | "warning" | "danger" | "info" | "neutral" {
  if (s === "PASSED")  return "success";
  if (s === "FAILED" || s === "ERROR") return "danger";
  if (s === "RUNNING") return "info";
  if (s === "QUEUED")  return "warning";
  return "neutral";
}

function statusIcon(s: RunStatus) {
  switch (s) {
    case "PASSED":    return CheckCircle2;
    case "FAILED":    return XCircle;
    case "ERROR":     return AlertOctagon;
    case "RUNNING":   return Hourglass;
    case "QUEUED":    return PauseCircle;
    case "CANCELLED": return XCircle;
  }
}

function statusColor(s: RunStatus): string {
  switch (s) {
    case "PASSED":    return "text-success-500";
    case "FAILED":    return "text-danger-500";
    case "ERROR":     return "text-danger-500";
    case "RUNNING":   return "text-brand-400 animate-pulse";
    case "QUEUED":    return "text-warning-500";
    case "CANCELLED": return "text-ink-muted";
  }
}
