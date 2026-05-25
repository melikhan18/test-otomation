import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertOctagon, CheckCircle2, Clock, Hourglass, MinusCircle, Package, PauseCircle, RefreshCcw, X, XCircle,
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
  runApi, STEP_ACTION_MAP, type RunStatus, type RunView, type StepResultStatus, type StepResultView,
} from "@/lib/automation";
import { distinctTags, fetchRunView, platformSupportsRunTagsAndCancel, useReportFeed } from "@/lib/reports";
import { useAuthStore } from "@/store/auth";
import { sessionApi } from "@/lib/sessions";
import DeviceVideoPlayer from "@/components/DeviceVideoPlayer";
import { Radio } from "lucide-react";
import { cn } from "@/lib/cn";

export default function RunDetailPage() {
  const { runId } = useParams();
  const id = Number(runId);
  const nav = useNavigate();
  const activeCompanyId = useAuthStore((s) => s.activeCompanyId);
  const activeProjectId = useAuthStore((s) => s.activeProjectId);
  const platform       = useAuthStore((s) => s.activePlatform);

  // The run id in the URL belongs to whatever project was active when the user
  // landed here. If they switch tenancy in the sidebar, this id is suddenly in
  // someone else's workspace — bounce back to Reports rather than render a
  // permanent "Run not found" panel.
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

  // Poll while the run is in flight; back off once it's terminal. The fetcher
  // is platform-aware — on WEB we hit /api/runs (gateway-routed to the web
  // runner) and adapt the response back into the unified RunView shape so the
  // rest of the page renders the same way for both stacks.
  const runQ = useQuery({
    queryKey: ["automation-run", platform, activeCompanyId ?? null, id],
    queryFn: () => fetchRunView(platform, id),
    enabled: !Number.isNaN(id) && activeCompanyId != null,
    refetchOnWindowFocus: false,
    refetchInterval: (q) => {
      const s = q.state.data?.status;
      return s === "QUEUED" || s === "RUNNING" ? 1500 : false;
    },
  });

  // Pre-warm the feed cache so ReportNav's prev/next render instantly when arriving here
  // via a direct URL (refresh, deep link). Cheap when cache is already populated.
  const feed = useReportFeed();
  const suggestions = distinctTags(feed.items);

  // J / K keyboard shortcuts for fast browsing.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.target instanceof HTMLElement &&
          (e.target.tagName === "INPUT" || e.target.tagName === "TEXTAREA" || e.target.isContentEditable)) return;
      const cur = feed.items.findIndex((f) => f.kind === "run" && f.item.id === id);
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

  if (runQ.isLoading) {
    return <>
      <TopBar crumbs={[{ label: "Automation", to: "/automation" }, { label: "Reports", to: "/automation/reports" }, { label: "Loading…" }]} />
      <div className="p-6 text-ink-muted text-sm flex items-center gap-2"><Spinner /> Loading report…</div>
    </>;
  }
  if (runQ.error || !runQ.data) {
    return <>
      <TopBar crumbs={[{ label: "Automation", to: "/automation" }, { label: "Reports", to: "/automation/reports" }, { label: "Not found" }]} />
      <div className="p-6 text-danger-500">Report not found</div>
    </>;
  }
  const run = runQ.data;

  return (
    <>
      <TopBar
        crumbs={[
          { label: "Automation", to: "/automation" },
          { label: "Reports",    to: "/automation/reports" },
          { label: `#${run.id}` },
        ]}
        actions={
          <>
            <ReportNav current={{ kind: "run", id }} />
            <Button variant="ghost" size="sm"
              leftIcon={<RefreshCcw size={12} className={runQ.isFetching ? "animate-spin" : undefined} />}
              onClick={() => runQ.refetch()}>
              Refresh
            </Button>
          </>
        }
      />

      <div className="px-6 py-6 space-y-4">
        <RunHeader run={run} suggestions={suggestions} />

        {run.targetAppVersionId != null && <AppPrepBanner run={run} />}

        {/* Two columns on lg+: steps on the left, live/recording media on the right.
         *  On smaller widths the media stacks above the steps so phones/tablets get
         *  the most informative panel first.                                        */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
          <div className="lg:col-span-7 order-2 lg:order-1">
            <StepTimeline run={run} />
          </div>
          <div className="lg:col-span-5 order-1 lg:order-2">
            <div className="lg:sticky lg:top-4">
              <RunMedia run={run} />
            </div>
          </div>
        </div>
      </div>
    </>
  );
}

/* ─────────────────────────────  Media (live / recording)  ─────────── */

/**
 * Right-hand panel that decides what to show based on the run's state:
 *  - RUNNING / QUEUED → live, view-only stream from the device session
 *  - Terminal + videoUrl → final recording (MP4, seekable)
 *  - Terminal + no videoUrl → empty (rare: device crash, upload failed)
 */
function RunMedia({ run }: { run: RunView }) {
  const isLive = run.status === "RUNNING" || run.status === "QUEUED";

  if (isLive && run.sessionId != null) {
    return <RunLiveView sessionId={run.sessionId} status={run.status} />;
  }
  if (run.videoUrl) {
    return <RunRecording url={run.videoUrl} />;
  }
  // Nothing to show — keep the column visually quiet rather than render a noisy
  // empty state next to the steps. Step rows already carry per-step screenshots
  // for terminal runs without a full video.
  return null;
}

/**
 * View-only live preview while the run is in flight. We reuse {@link DeviceVideoPlayer}
 * (the same canvas + H.264 decoder pipeline as SessionPage), but on its own — no touch
 * forwarding, no control socket. The user just watches.
 *
 * SessionApi.get requires the caller to be the session owner (or platform admin), so
 * teammates without ownership see a friendly fallback explaining that.
 */
function RunLiveView({ sessionId, status }: { sessionId: number; status: RunStatus }) {
  const sessionQ = useQuery({
    queryKey: ["session-for-run", sessionId],
    queryFn: () => sessionApi.get(sessionId),
    // Token TTL is short; refetch every 60s while we're live so a long run
    // keeps the WebSocket reconnect-able if the token expires mid-stream.
    refetchInterval: 60_000,
    refetchOnWindowFocus: false,
    retry: false,
  });

  const token = sessionQ.data?.sessionToken ?? null;
  const sessionEnded = sessionQ.data && sessionQ.data.status !== "ACTIVE";

  return (
    <Card className="overflow-hidden">
      <div className="px-4 py-2 border-b border-surface-border flex items-center justify-between">
        <span className="text-[10px] uppercase tracking-wider font-semibold text-ink-muted inline-flex items-center gap-2">
          <Radio size={11} className="text-brand-400" />
          Live · {status === "RUNNING" ? "running" : "waiting for device"}
        </span>
        <span className="text-[10px] text-ink-muted">view-only</span>
      </div>
      <div className="bg-black aspect-[9/16] max-h-[60vh] flex items-center justify-center">
        {sessionQ.isError ? (
          <div className="text-xs text-ink-muted px-4 py-6 text-center">
            Live preview is only visible to the user who started the run.
          </div>
        ) : !token ? (
          <div className="text-xs text-ink-muted">Connecting…</div>
        ) : sessionEnded ? (
          <div className="text-xs text-ink-muted">Session has ended — waiting for the recording.</div>
        ) : (
          <DeviceVideoPlayer
            sessionId={sessionId}
            sessionToken={token}
            className="w-full h-full"
          />
        )}
      </div>
    </Card>
  );
}

function RunRecording({ url }: { url: string }) {
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
        className="w-full max-h-[60vh] bg-black object-contain"
      />
    </Card>
  );
}

/* ─────────────────────────────  App Prep banner  ────────────────────── */

/**
 * Shown above the step timeline when the run had a target APK. Surfaces whether the
 * orchestrator skipped install (latest already on device), installed, updated, or
 * failed — and how long the prep phase took. Failure detail expands inline because
 * users debugging "why didn't the test start" need to see the agent's error
 * (INSTALL_FAILED_UPDATE_INCOMPATIBLE, sha256 mismatch, etc.) right away.
 */
function AppPrepBanner({ run }: { run: RunView }) {
  const status = run.appPrepStatus ?? "NOT_REQUESTED";
  const tone = appPrepTone(status);
  const target = run.targetApp;
  return (
    <Card className="px-4 py-3 flex items-start gap-3">
      <Package size={14} className="mt-0.5 text-ink-muted shrink-0" />
      <div className="flex-1 min-w-0 space-y-1">
        <div className="flex items-center gap-2 flex-wrap text-xs">
          <span className="font-semibold">App preparation</span>
          <StatusBadge tone={tone}>{status}</StatusBadge>
          {run.appPrepDurationMs != null && (
            <span className="text-[10px] text-ink-muted font-mono">{run.appPrepDurationMs}ms</span>
          )}
        </div>
        {target ? (
          <div className="text-[11px] text-ink-muted">
            Target: <span className="font-mono">{target.packageName}</span>{" "}
            v{target.versionName ?? target.versionCode} (vc {target.versionCode})
          </div>
        ) : (
          <div className="text-[11px] text-warning-500 italic">
            Target APK version was deleted after the run started.
          </div>
        )}
        {run.appPrepError && (
          <details className="mt-1.5 text-[11px] text-danger-500">
            <summary className="cursor-pointer font-medium">Error detail</summary>
            <pre className="mt-1 whitespace-pre-wrap break-words font-mono text-[10px]">{run.appPrepError}</pre>
          </details>
        )}
      </div>
    </Card>
  );
}

function appPrepTone(status: string): "success" | "warning" | "danger" | "neutral" | "info" {
  switch (status) {
    case "ALREADY_LATEST": return "info";
    case "INSTALLED":      return "success";
    case "UPDATED":        return "success";
    case "FAILED":         return "danger";
    case "NOT_REQUESTED":  return "neutral";
    default:               return "neutral";
  }
}

/* ─────────────────────────────  Header  ────────────────────────────── */

function RunHeader({ run, suggestions }: { run: RunView; suggestions: string[] }) {
  const platform = useAuthStore((s) => s.activePlatform);
  const supportsTagsCancel = platformSupportsRunTagsAndCancel(platform);
  const qc = useQueryClient();
  const tagsMut = useMutation({
    mutationFn: (tags: string[]) => runApi.updateTags(run.id, tags),
    onSuccess: (next) => {
      qc.setQueryData(["automation-run", run.id], next);
      qc.invalidateQueries({ queryKey: ["report-runs"] });
    },
  });
  const passRate = run.totalSteps > 0 ? (run.passedSteps / run.totalSteps) * 100 : 0;
  const tone = statusTone(run.status);
  return (
    <Card className="p-5">
      <div className="flex items-start justify-between gap-3 flex-wrap">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 text-[10px] uppercase tracking-wider font-semibold text-ink-muted">
            Run #{run.id}
            <StatusBadge tone={tone}>{run.status}</StatusBadge>
            {(run.status === "RUNNING" || run.status === "QUEUED") && (
              <LiveIndicator variant="bars" tone={run.status === "RUNNING" ? "running" : "queued"} />
            )}
          </div>
          <div className="text-lg font-semibold mt-1 truncate">
            {run.scenarioName ?? "(scenario deleted)"}{" "}
            {run.scenarioVersion != null && <span className="text-[10px] text-ink-muted font-mono ml-1">v{run.scenarioVersion}</span>}
          </div>
          <div className="text-xs text-ink-muted mt-1 flex items-center gap-3 flex-wrap">
            {platform === "WEB"
              ? <span>browser run</span>
              : <span>Device #{run.deviceId}</span>}
            <span>env <code className="font-mono">{run.environment}</code></span>
            {platform !== "WEB" && (
              <span>
                pacing{" "}
                {run.adaptiveWait
                  ? <code className="font-mono">adaptive</code>
                  : <code className="font-mono">{run.interStepDelayMs}ms</code>}
              </span>
            )}
            <span>created {new Date(run.createdAt).toLocaleTimeString()}</span>
            {run.durationMs != null && <span>· {(run.durationMs / 1000).toFixed(1)}s</span>}
          </div>
          {supportsTagsCancel && (
            <div className="mt-2">
              <TagEditor
                tags={run.tags ?? []}
                suggestions={suggestions}
                onChange={(next) => tagsMut.mutateAsync(next).then(() => undefined)}
              />
            </div>
          )}
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
