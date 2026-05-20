import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  AlertOctagon, ArrowLeft, CheckCircle2, Clock, Hourglass, MinusCircle, PauseCircle, RefreshCcw, X, XCircle,
} from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Spinner } from "@/components/ui/Spinner";
import { StatusBadge } from "@/components/ui/StatusBadge";
import {
  runApi, STEP_ACTION_MAP, type RunStatus, type RunView, type StepResultStatus, type StepResultView,
} from "@/lib/automation";
import { cn } from "@/lib/cn";

export default function RunDetailPage() {
  const { runId } = useParams();
  const id = Number(runId);
  const nav = useNavigate();

  // Poll while the run is in flight; back off once it's terminal.
  const runQ = useQuery({
    queryKey: ["automation-run", id],
    queryFn: () => runApi.get(id),
    enabled: !Number.isNaN(id),
    refetchOnWindowFocus: false,
    refetchInterval: (q) => {
      const s = q.state.data?.status;
      return s === "QUEUED" || s === "RUNNING" ? 1500 : false;
    },
  });

  useEffect(() => { /* page is polling */ }, [runQ.data?.status]);

  if (runQ.isLoading) {
    return <>
      <TopBar crumbs={[{ label: "Automation", to: "/automation" }, { label: "Runs" }, { label: "Loading…" }]} />
      <div className="p-6 text-ink-muted text-sm flex items-center gap-2"><Spinner /> Loading run…</div>
    </>;
  }
  if (runQ.error || !runQ.data) {
    return <>
      <TopBar crumbs={[{ label: "Automation", to: "/automation" }, { label: "Runs" }, { label: "Not found" }]} />
      <div className="p-6 text-danger-500">Run not found</div>
    </>;
  }
  const run = runQ.data;

  return (
    <>
      <TopBar
        crumbs={[
          { label: "Automation", to: "/automation" },
          { label: "Runs",       to: "/automation/runs" },
          { label: `Run ${run.id}` },
        ]}
        actions={
          <>
            <Button variant="ghost" size="sm" leftIcon={<ArrowLeft size={14} />} onClick={() => nav("/automation/runs")}>
              Back
            </Button>
            <Button variant="ghost" size="sm"
              leftIcon={<RefreshCcw size={12} className={runQ.isFetching ? "animate-spin" : undefined} />}
              onClick={() => runQ.refetch()}>
              Refresh
            </Button>
          </>
        }
      />

      <div className="px-6 py-6 space-y-4">
        <RunHeader run={run} />
        {run.videoUrl && <RunVideo url={run.videoUrl} />}
        <StepTimeline run={run} />
      </div>
    </>
  );
}

/* ─────────────────────────────  Video  ─────────────────────────────── */

function RunVideo({ url }: { url: string }) {
  return (
    <Card className="overflow-hidden">
      <div className="px-4 py-2 border-b border-surface-border flex items-center justify-between">
        <span className="text-[10px] uppercase tracking-wider font-semibold text-ink-muted">
          Recording
        </span>
        <a href={url} target="_blank" rel="noreferrer"
           className="text-[10px] text-ink-muted hover:text-brand-400 font-mono">
          open ↗
        </a>
      </div>
      {/* `controls` + `preload="metadata"` lets the browser fetch the moov atom (which
          ffmpeg moved to the front via +faststart) without downloading the whole file. */}
      <video
        src={url}
        controls
        preload="metadata"
        className="w-full max-h-[480px] bg-black object-contain"
      />
    </Card>
  );
}

/* ─────────────────────────────  Header  ────────────────────────────── */

function RunHeader({ run }: { run: RunView }) {
  const passRate = run.totalSteps > 0 ? (run.passedSteps / run.totalSteps) * 100 : 0;
  const tone = statusTone(run.status);
  return (
    <Card className="p-5">
      <div className="flex items-start justify-between gap-3 flex-wrap">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 text-[10px] uppercase tracking-wider font-semibold text-ink-muted">
            Run #{run.id}
            <StatusBadge tone={tone}>{run.status}</StatusBadge>
          </div>
          <div className="text-lg font-semibold mt-1 truncate">
            {run.scenarioName ?? "(scenario deleted)"}{" "}
            {run.scenarioVersion != null && <span className="text-[10px] text-ink-muted font-mono ml-1">v{run.scenarioVersion}</span>}
          </div>
          <div className="text-xs text-ink-muted mt-1 flex items-center gap-3 flex-wrap">
            <span>Device #{run.deviceId}</span>
            <span>env <code className="font-mono">{run.environment}</code></span>
            <span>
              pacing{" "}
              {run.adaptiveWait
                ? <code className="font-mono">adaptive</code>
                : <code className="font-mono">{run.interStepDelayMs}ms</code>}
            </span>
            <span>created {new Date(run.createdAt).toLocaleTimeString()}</span>
            {run.durationMs != null && <span>· {(run.durationMs / 1000).toFixed(1)}s</span>}
          </div>
        </div>
        <div className="grid grid-cols-3 gap-3 text-right">
          <Stat label="Total"  value={run.totalSteps}  />
          <Stat label="Passed" value={run.passedSteps} tone="success" />
          <Stat label="Failed" value={run.failedSteps} tone="danger" />
        </div>
      </div>

      {run.totalSteps > 0 && (
        <div className="mt-4 h-1.5 rounded-full bg-surface overflow-hidden flex">
          <div className="bg-success-500" style={{ width: `${(run.passedSteps / run.totalSteps) * 100}%` }} />
          <div className="bg-danger-500"  style={{ width: `${(run.failedSteps / run.totalSteps) * 100}%` }} />
          <div className="bg-surface-muted" style={{ width: `${100 - passRate - (run.failedSteps / run.totalSteps) * 100}%` }} />
        </div>
      )}

      {run.errorSummary && (
        <div className="mt-3 rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs flex items-start gap-2">
          <AlertOctagon size={12} className="mt-0.5" />
          <span>{run.errorSummary}</span>
        </div>
      )}
    </Card>
  );
}

function Stat({ label, value, tone }: { label: string; value: number; tone?: "success" | "danger" }) {
  const color = tone === "success" ? "text-success-500" : tone === "danger" ? "text-danger-500" : "text-ink-primary";
  return (
    <div className="min-w-0">
      <div className="text-[10px] uppercase tracking-wider text-ink-muted">{label}</div>
      <div className={cn("text-lg font-semibold mt-0.5", color)}>{value}</div>
    </div>
  );
}

/* ─────────────────────────────  Steps  ────────────────────────────── */

function StepTimeline({ run }: { run: RunView }) {
  const [lightbox, setLightbox] = useState<string | null>(null);
  return (
    <>
      <div className="space-y-2">
        {run.stepResults.map((sr) => <StepRow key={sr.id} sr={sr} onPreview={setLightbox} />)}
        {run.stepResults.length === 0 && (
          <Card className="p-6 text-center text-ink-muted text-sm">No step results recorded.</Card>
        )}
      </div>
      {lightbox && <ScreenshotLightbox url={lightbox} onClose={() => setLightbox(null)} />}
    </>
  );
}

function StepRow({ sr, onPreview }: { sr: StepResultView; onPreview: (url: string) => void }) {
  const def = STEP_ACTION_MAP[sr.action];
  const Icon  = statusIcon(sr.status);
  const color = statusColor(sr.status);
  return (
    <div className={cn(
      "rounded-md border border-surface-border bg-surface flex items-stretch",
      sr.status === "FAILED" || sr.status === "ERROR" ? "border-l-2 border-l-danger-500" : "",
      sr.status === "PASSED"  ? "border-l-2 border-l-success-500" : "",
      sr.status === "RUNNING" ? "border-l-2 border-l-brand-500" : "",
    )}>
      <div className="px-3 flex items-center justify-center text-[11px] font-mono text-ink-muted border-r border-surface-border w-10 shrink-0">
        {sr.orderIndex + 1}
      </div>
      <div className="px-3 py-2 flex items-center justify-center shrink-0">
        <Icon size={14} className={color} />
      </div>
      <div className="flex-1 min-w-0 px-3 py-2">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-[10px] font-mono uppercase tracking-wider font-semibold text-ink-secondary">
            {def?.label ?? sr.action}
          </span>
          {sr.resolvedLocator && (
            <span className="text-[10px] text-ink-muted font-mono">via {sr.resolvedLocator}</span>
          )}
          {sr.durationMs != null && (
            <span className="text-[10px] text-ink-muted ml-auto inline-flex items-center gap-1">
              <Clock size={10} /> {sr.durationMs}ms
            </span>
          )}
        </div>
        {sr.errorMessage && (
          <div className="mt-1 text-xs text-danger-500 font-mono break-all">{sr.errorMessage}</div>
        )}
      </div>
      {sr.screenshotUrl && (
        <button
          onClick={() => onPreview(sr.screenshotUrl!)}
          className="shrink-0 border-l border-surface-border hover:bg-surface-muted transition-colors w-16 flex items-center justify-center group"
          title="Open screenshot"
        >
          <img
            src={sr.screenshotUrl}
            alt={`step ${sr.orderIndex + 1} screenshot`}
            className="max-h-12 object-contain rounded-sm group-hover:opacity-90"
            loading="lazy"
          />
        </button>
      )}
    </div>
  );
}

/* ─────────────────────────────  Lightbox  ──────────────────────────── */

function ScreenshotLightbox({ url, onClose }: { url: string; onClose: () => void }) {
  // Click anywhere outside the image (or hit Escape) to dismiss.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div
      onClick={onClose}
      className="fixed inset-0 z-50 bg-black/85 backdrop-blur-sm flex items-center justify-center p-6 animate-fade-in"
    >
      <button
        onClick={onClose}
        className="absolute top-4 right-4 text-white/80 hover:text-white p-2 rounded-full hover:bg-white/10"
        title="Close (Esc)"
      >
        <X size={18} />
      </button>
      <img
        src={url}
        alt="step screenshot"
        onClick={(e) => e.stopPropagation()}
        className="max-w-full max-h-full object-contain rounded-md shadow-2xl"
      />
    </div>
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

function statusIcon(s: StepResultStatus) {
  switch (s) {
    case "PASSED":  return CheckCircle2;
    case "FAILED":  return XCircle;
    case "ERROR":   return AlertOctagon;
    case "SKIPPED": return MinusCircle;
    case "RUNNING": return Hourglass;
    case "PENDING": return PauseCircle;
  }
}

function statusColor(s: StepResultStatus): string {
  switch (s) {
    case "PASSED":  return "text-success-500";
    case "FAILED":  return "text-danger-500";
    case "ERROR":   return "text-danger-500";
    case "SKIPPED": return "text-ink-muted";
    case "RUNNING": return "text-brand-400 animate-pulse";
    case "PENDING": return "text-ink-muted";
  }
}
