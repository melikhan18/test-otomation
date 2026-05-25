import { useEffect, useRef } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertOctagon, CheckCircle2, ChevronRight, Clock, Hourglass, MinusCircle,
  PauseCircle, RefreshCcw, XCircle,
} from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Spinner } from "@/components/ui/Spinner";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { LiveIndicator } from "@/components/LiveIndicator";
import ReportNav from "@/components/automation/ReportNav";
import TagEditor from "@/components/automation/TagEditor";
import {
  suiteRunApi, type RunStatus, type SuiteRunChild, type SuiteRunStatus, type SuiteRunView,
} from "@/lib/automation";
import { distinctTags, fetchSuiteRunView, platformSupportsRunTagsAndCancel, useReportFeed } from "@/lib/reports";
import { useAuthStore } from "@/store/auth";
import { cn } from "@/lib/cn";

export default function SuiteRunDetailPage() {
  const { suiteRunId } = useParams();
  const id = Number(suiteRunId);
  const nav = useNavigate();
  const activeCompanyId = useAuthStore((s) => s.activeCompanyId);
  const activeProjectId = useAuthStore((s) => s.activeProjectId);
  const platform       = useAuthStore((s) => s.activePlatform);

  // Suite-run id is project-scoped — bounce to Reports on tenancy switch so the
  // user doesn't land on a permanent "Suite run not found" page.
  const tenancySnapshot = useRef<{ c: number | null; p: number | null }>(
    { c: activeCompanyId ?? null, p: activeProjectId ?? null },
  );
  useEffect(() => {
    const prev = tenancySnapshot.current;
    const next = { c: activeCompanyId ?? null, p: activeProjectId ?? null };
    if (prev.c !== next.c || prev.p !== next.p) {
      tenancySnapshot.current = next;
      nav("/automation/reports", { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeCompanyId, activeProjectId]);

  // Poll while the suite is in flight; back off once it's terminal. The
  // fetcher is platform-aware — on WEB we hit /api/suite-runs (gateway-routed
  // to the web runner) and adapt the response into the unified SuiteRunView
  // so the rest of the page renders the same way for both stacks.
  const q = useQuery({
    queryKey: ["automation-suite-run", platform, activeCompanyId ?? null, id],
    queryFn: () => fetchSuiteRunView(platform, id),
    enabled: !Number.isNaN(id) && activeCompanyId != null,
    refetchOnWindowFocus: false,
    refetchInterval: (qq) => {
      const s = qq.state.data?.status;
      return s === "QUEUED" || s === "RUNNING" ? 1500 : false;
    },
  });

  const feed = useReportFeed();
  const suggestions = distinctTags(feed.items);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.target instanceof HTMLElement &&
          (e.target.tagName === "INPUT" || e.target.tagName === "TEXTAREA" || e.target.isContentEditable)) return;
      const cur = feed.items.findIndex((f) => f.kind === "suite" && f.item.id === id);
      if (cur < 0) return;
      if (e.key === "j") {
        const p = feed.items[cur - 1];
        if (p) nav(p.kind === "suite" ? `/automation/suite-runs/${p.item.id}` : `/automation/runs/${p.item.id}`);
      } else if (e.key === "k") {
        const n = feed.items[cur + 1];
        if (n) nav(n.kind === "suite" ? `/automation/suite-runs/${n.item.id}` : `/automation/runs/${n.item.id}`);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [feed.items, id, nav]);

  if (q.isLoading) {
    return <>
      <TopBar crumbs={[{ label: "Automation", to: "/automation" }, { label: "Reports", to: "/automation/reports" }, { label: "Loading…" }]} />
      <div className="p-6 text-ink-muted text-sm flex items-center gap-2"><Spinner /> Loading report…</div>
    </>;
  }
  if (q.error || !q.data) {
    return <>
      <TopBar crumbs={[{ label: "Automation", to: "/automation" }, { label: "Reports", to: "/automation/reports" }, { label: "Not found" }]} />
      <div className="p-6 text-danger-500">Report not found</div>
    </>;
  }
  const sr = q.data;

  return (
    <>
      <TopBar
        crumbs={[
          { label: "Automation", to: "/automation" },
          { label: "Reports",    to: "/automation/reports" },
          { label: `Suite #${sr.id}` },
        ]}
        actions={
          <>
            <ReportNav current={{ kind: "suite", id }} />
            <Button variant="ghost" size="sm"
              leftIcon={<RefreshCcw size={12} className={q.isFetching ? "animate-spin" : undefined} />}
              onClick={() => q.refetch()}>
              Refresh
            </Button>
          </>
        }
      />

      <div className="px-6 py-6 space-y-4">
        <Header sr={sr} suggestions={suggestions} />
        <ChildList sr={sr} />
      </div>
    </>
  );
}

/* ─────────────────────────────  Header  ────────────────────────────── */

function Header({ sr, suggestions }: { sr: SuiteRunView; suggestions: string[] }) {
  const platform = useAuthStore((s) => s.activePlatform);
  const supportsTagsCancel = platformSupportsRunTagsAndCancel(platform);
  const qc = useQueryClient();
  const tagsMut = useMutation({
    mutationFn: (tags: string[]) => suiteRunApi.updateTags(sr.id, tags),
    onSuccess: (next) => {
      qc.setQueryData(["automation-suite-run", sr.id], next);
      qc.invalidateQueries({ queryKey: ["report-suite-runs"] });
    },
  });
  const tone = suiteTone(sr.status);
  return (
    <Card className="p-5">
      <div className="flex items-start justify-between gap-3 flex-wrap">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 text-[10px] uppercase tracking-wider font-semibold text-ink-muted">
            Suite run #{sr.id}
            <StatusBadge tone={tone}>{sr.status}</StatusBadge>
            {(sr.status === "RUNNING" || sr.status === "QUEUED") && (
              <LiveIndicator variant="bars" tone={sr.status === "RUNNING" ? "running" : "queued"} />
            )}
          </div>
          <div className="text-lg font-semibold mt-1 truncate">
            {sr.suiteName ?? "(suite deleted)"}
          </div>
          <div className="text-xs text-ink-muted mt-1 flex items-center gap-3 flex-wrap">
            {platform === "WEB"
              ? <span>browser run</span>
              : <span>Device #{sr.deviceId}</span>}
            <span>env <code className="font-mono">{sr.environment}</code></span>
            <span>created {new Date(sr.createdAt).toLocaleTimeString()}</span>
            {sr.durationMs != null && <span>· {(sr.durationMs / 1000).toFixed(1)}s</span>}
          </div>
          {supportsTagsCancel && (
            <div className="mt-2">
              <TagEditor
                tags={sr.tags ?? []}
                suggestions={suggestions}
                onChange={(next) => tagsMut.mutateAsync(next).then(() => undefined)}
              />
            </div>
          )}
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
  const isLive = r.status === "RUNNING" || r.status === "QUEUED";
  const liveTone = r.status === "RUNNING" ? "running" : "queued";
  return (
    <Link
      to={`/automation/runs/${r.id}`}
      className={cn(
        "rounded-md border border-surface-border bg-surface flex items-stretch hover:border-brand-500/30 transition-colors",
        r.status === "FAILED" || r.status === "ERROR" ? "border-l-2 border-l-danger-500" : "",
        r.status === "PASSED"  ? "border-l-2 border-l-success-500" : "",
        r.status === "RUNNING" ? "border-l-2 border-l-brand-500  bg-brand-500/[0.03]" : "",
        r.status === "QUEUED"  ? "border-l-2 border-l-warning-500 bg-warning-500/[0.03]" : "",
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
          {r.appPrepStatus && r.appPrepStatus !== "NOT_REQUESTED" && (
            <span
              className={
                "text-[9px] font-medium uppercase tracking-wider px-1.5 py-0.5 rounded border " +
                (r.appPrepStatus === "FAILED"
                  ? "border-danger-500/30 bg-danger-500/10 text-danger-500"
                  : r.appPrepStatus === "INSTALLED" || r.appPrepStatus === "UPDATED"
                    ? "border-success-500/30 bg-success-500/10 text-success-500"
                    : "border-brand-500/30 bg-brand-500/10 text-brand-400")
              }
              title="App preparation outcome"
            >
              app: {r.appPrepStatus.toLowerCase().replace("_", " ")}
            </span>
          )}
          {r.durationMs != null && (
            <span className="text-[10px] text-ink-muted ml-auto inline-flex items-center gap-1">
              <Clock size={10} /> {(r.durationMs / 1000).toFixed(1)}s
            </span>
          )}
        </div>
      </div>
      {isLive && (
        <div className="px-3 flex items-center">
          <LiveIndicator variant="bars" tone={liveTone} />
        </div>
      )}
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
