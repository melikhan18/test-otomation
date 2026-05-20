import { Link, useNavigate, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  AlertOctagon, ArrowLeft, CheckCircle2, ChevronRight, Clock, Hourglass, MinusCircle,
  PauseCircle, RefreshCcw, XCircle,
} from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Spinner } from "@/components/ui/Spinner";
import { StatusBadge } from "@/components/ui/StatusBadge";
import {
  suiteRunApi, type RunStatus, type SuiteRunChild, type SuiteRunStatus, type SuiteRunView,
} from "@/lib/automation";
import { cn } from "@/lib/cn";

export default function SuiteRunDetailPage() {
  const { suiteRunId } = useParams();
  const id = Number(suiteRunId);
  const nav = useNavigate();

  // Poll while the suite is in flight; back off once it's terminal.
  const q = useQuery({
    queryKey: ["automation-suite-run", id],
    queryFn: () => suiteRunApi.get(id),
    enabled: !Number.isNaN(id),
    refetchOnWindowFocus: false,
    refetchInterval: (qq) => {
      const s = qq.state.data?.status;
      return s === "QUEUED" || s === "RUNNING" ? 1500 : false;
    },
  });

  if (q.isLoading) {
    return <>
      <TopBar crumbs={[{ label: "Automation", to: "/automation" }, { label: "Suite runs" }, { label: "Loading…" }]} />
      <div className="p-6 text-ink-muted text-sm flex items-center gap-2"><Spinner /> Loading suite run…</div>
    </>;
  }
  if (q.error || !q.data) {
    return <>
      <TopBar crumbs={[{ label: "Automation", to: "/automation" }, { label: "Suite runs" }, { label: "Not found" }]} />
      <div className="p-6 text-danger-500">Suite run not found</div>
    </>;
  }
  const sr = q.data;

  return (
    <>
      <TopBar
        crumbs={[
          { label: "Automation", to: "/automation" },
          { label: "Suite runs" },
          { label: `Suite run #${sr.id}` },
        ]}
        actions={
          <>
            <Button variant="ghost" size="sm" leftIcon={<ArrowLeft size={14} />} onClick={() => nav("/automation")}>
              Back
            </Button>
            <Button variant="ghost" size="sm"
              leftIcon={<RefreshCcw size={12} className={q.isFetching ? "animate-spin" : undefined} />}
              onClick={() => q.refetch()}>
              Refresh
            </Button>
          </>
        }
      />

      <div className="px-6 py-6 space-y-4">
        <Header sr={sr} />
        <ChildList sr={sr} />
      </div>
    </>
  );
}

/* ─────────────────────────────  Header  ────────────────────────────── */

function Header({ sr }: { sr: SuiteRunView }) {
  const tone = suiteTone(sr.status);
  return (
    <Card className="p-5">
      <div className="flex items-start justify-between gap-3 flex-wrap">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 text-[10px] uppercase tracking-wider font-semibold text-ink-muted">
            Suite run #{sr.id}
            <StatusBadge tone={tone}>{sr.status}</StatusBadge>
          </div>
          <div className="text-lg font-semibold mt-1 truncate">
            {sr.suiteName ?? "(suite deleted)"}
          </div>
          <div className="text-xs text-ink-muted mt-1 flex items-center gap-3 flex-wrap">
            <span>Device #{sr.deviceId}</span>
            <span>env <code className="font-mono">{sr.environment}</code></span>
            <span>created {new Date(sr.createdAt).toLocaleTimeString()}</span>
            {sr.durationMs != null && <span>· {(sr.durationMs / 1000).toFixed(1)}s</span>}
          </div>
        </div>
        <div className="grid grid-cols-3 gap-3 text-right">
          <Stat label="Total"  value={sr.totalScenarios} />
          <Stat label="Passed" value={sr.passedScenarios} tone="success" />
          <Stat label="Failed" value={sr.failedScenarios} tone="danger" />
        </div>
      </div>

      {sr.totalScenarios > 0 && (
        <div className="mt-4 h-1.5 rounded-full bg-surface overflow-hidden flex">
          <div className="bg-success-500"
               style={{ width: `${(sr.passedScenarios / sr.totalScenarios) * 100}%` }} />
          <div className="bg-danger-500"
               style={{ width: `${(sr.failedScenarios / sr.totalScenarios) * 100}%` }} />
        </div>
      )}

      {sr.errorSummary && (
        <div className="mt-3 rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs flex items-start gap-2">
          <AlertOctagon size={12} className="mt-0.5" />
          <span>{sr.errorSummary}</span>
        </div>
      )}
    </Card>
  );
}

function Stat({ label, value, tone }: { label: string; value: number; tone?: "success" | "danger" }) {
  const color = tone === "success" ? "text-success-500"
              : tone === "danger"  ? "text-danger-500"
              : "text-ink-primary";
  return (
    <div className="min-w-0">
      <div className="text-[10px] uppercase tracking-wider text-ink-muted">{label}</div>
      <div className={cn("text-lg font-semibold mt-0.5", color)}>{value}</div>
    </div>
  );
}

/* ─────────────────────────────  Child list  ────────────────────────── */

function ChildList({ sr }: { sr: SuiteRunView }) {
  return (
    <div className="space-y-2">
      {sr.runs.map((r, i) => <ChildRow key={r.id} i={i} r={r} />)}
      {sr.runs.length === 0 && (
        <Card className="p-6 text-center text-ink-muted text-sm">No child runs yet.</Card>
      )}
    </div>
  );
}

function ChildRow({ i, r }: { i: number; r: SuiteRunChild }) {
  const Icon = runStatusIcon(r.status);
  const color = runStatusColor(r.status);
  return (
    <Link
      to={`/automation/runs/${r.id}`}
      className={cn(
        "rounded-md border border-surface-border bg-surface flex items-stretch hover:border-brand-500/30 transition-colors",
        r.status === "FAILED" || r.status === "ERROR" ? "border-l-2 border-l-danger-500" : "",
        r.status === "PASSED"  ? "border-l-2 border-l-success-500" : "",
        r.status === "RUNNING" ? "border-l-2 border-l-brand-500" : "",
      )}
    >
      <div className="px-3 flex items-center justify-center text-[11px] font-mono text-ink-muted border-r border-surface-border w-10 shrink-0">
        {i + 1}
      </div>
      <div className="px-3 py-2 flex items-center justify-center shrink-0">
        <Icon size={14} className={color} />
      </div>
      <div className="flex-1 min-w-0 px-3 py-2">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-sm font-medium truncate">
            {r.scenarioName ?? "(scenario deleted)"}
          </span>
          <span className="text-[10px] text-ink-muted font-mono">
            {r.passedSteps}/{r.totalSteps} passed
          </span>
          {r.durationMs != null && (
            <span className="text-[10px] text-ink-muted ml-auto inline-flex items-center gap-1">
              <Clock size={10} /> {(r.durationMs / 1000).toFixed(1)}s
            </span>
          )}
        </div>
      </div>
      <div className="px-3 flex items-center text-ink-muted">
        <ChevronRight size={14} />
      </div>
    </Link>
  );
}

/* ─────────────────────────────  helpers  ───────────────────────────── */

function suiteTone(s: SuiteRunStatus): "success" | "warning" | "danger" | "info" | "neutral" {
  if (s === "PASSED")  return "success";
  if (s === "FAILED" || s === "ERROR") return "danger";
  if (s === "RUNNING") return "info";
  if (s === "QUEUED")  return "warning";
  return "neutral";
}

function runStatusIcon(s: RunStatus) {
  switch (s) {
    case "PASSED":    return CheckCircle2;
    case "FAILED":    return XCircle;
    case "ERROR":     return AlertOctagon;
    case "CANCELLED": return MinusCircle;
    case "RUNNING":   return Hourglass;
    case "QUEUED":    return PauseCircle;
  }
}

function runStatusColor(s: RunStatus): string {
  switch (s) {
    case "PASSED":    return "text-success-500";
    case "FAILED":    return "text-danger-500";
    case "ERROR":     return "text-danger-500";
    case "CANCELLED": return "text-ink-muted";
    case "RUNNING":   return "text-brand-400 animate-pulse";
    case "QUEUED":    return "text-ink-muted";
  }
}
