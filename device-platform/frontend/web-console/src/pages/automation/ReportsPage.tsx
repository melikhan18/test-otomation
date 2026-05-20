import { useMemo } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import {
  AlertOctagon, CheckCircle2, ChevronRight, Clock, Film, Hourglass, Layers,
  PauseCircle, RefreshCcw, Tag as TagIcon, XCircle,
} from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { StatusBadge } from "@/components/ui/StatusBadge";
import TagEditor from "@/components/automation/TagEditor";
import {
  runApi, suiteRunApi,
  type RunStatus, type RunSummary, type SuiteRunStatus, type SuiteRunSummary,
} from "@/lib/automation";
import {
  distinctTags, matchesTagFilter, useReportFeed, type ReportItem,
} from "@/lib/reports";
import { cn } from "@/lib/cn";

/**
 * Unified report feed. Click a row → detail page (run or suite). Tag chips inline +
 * a filter bar above. Filter state lives in the URL so users can share a "smoke-only"
 * link with the rest of the team.
 */
export default function ReportsPage() {
  const [params, setParams] = useSearchParams();
  const scenarioId = params.get("scenarioId") ? Number(params.get("scenarioId")) : undefined;
  const selectedTags = useMemo(
    () => (params.get("tags") ?? "").split(",").map((s) => s.trim()).filter(Boolean),
    [params],
  );

  const feed = useReportFeed({ scenarioId });
  const allTags = useMemo(() => distinctTags(feed.items), [feed.items]);
  const filtered = useMemo(
    () => feed.items.filter((f) => matchesTagFilter(f, selectedTags)),
    [feed.items, selectedTags],
  );
  const liveCount = useMemo(
    () => filtered.filter((f) => f.item.status === "RUNNING" || f.item.status === "QUEUED").length,
    [filtered],
  );

  function setTags(next: string[]) {
    const np = new URLSearchParams(params);
    if (next.length === 0) np.delete("tags");
    else np.set("tags", next.join(","));
    setParams(np, { replace: true });
  }
  function toggleTag(t: string) {
    setTags(selectedTags.includes(t) ? selectedTags.filter((x) => x !== t) : [...selectedTags, t]);
  }
  function clearScenarioFilter() {
    const np = new URLSearchParams(params); np.delete("scenarioId"); setParams(np, { replace: true });
  }

  return (
    <>
      <TopBar
        crumbs={[{ label: "Automation", to: "/automation" }, { label: "Reports" }]}
        actions={
          <Button
            variant="ghost" size="sm"
            leftIcon={<RefreshCcw size={12} className={feed.isFetching ? "animate-spin" : undefined} />}
            onClick={() => feed.refetch()}
          >
            Refresh
          </Button>
        }
      />

      <div className="px-6 py-6 space-y-3">
        {/* Tag filter bar — clickable chips, sticky filter via URL. */}
        {allTags.length > 0 && (
          <div className="flex items-start gap-2 flex-wrap">
            <span className="inline-flex items-center gap-1 text-[10px] uppercase tracking-wider text-ink-muted h-6">
              <TagIcon size={11} /> Filter
            </span>
            {allTags.map((t) => {
              const active = selectedTags.includes(t);
              return (
                <button
                  key={t}
                  onClick={() => toggleTag(t)}
                  className={cn(
                    "h-6 px-2 rounded-full border text-[11px] font-mono transition-colors",
                    active
                      ? "border-brand-500/60 bg-brand-500/15 text-brand-300"
                      : "border-surface-border bg-surface-muted/40 text-ink-secondary hover:text-ink-primary hover:border-brand-500/30",
                  )}
                >
                  {t}
                </button>
              );
            })}
            {selectedTags.length > 0 && (
              <button
                onClick={() => setTags([])}
                className="h-6 px-2 rounded-full text-[11px] text-ink-muted hover:text-ink-primary"
              >
                clear
              </button>
            )}
          </div>
        )}

        <div className="flex items-center gap-3 flex-wrap text-xs">
          <span className="text-ink-secondary">
            <strong className="text-ink-primary text-sm">{filtered.length}</strong>{" "}
            report{filtered.length === 1 ? "" : "s"}
            {filtered.length !== feed.items.length && (
              <span className="text-ink-muted ml-1">of {feed.items.length}</span>
            )}
          </span>
          {liveCount > 0 && <StatusBadge tone="info">{liveCount} live</StatusBadge>}
          {scenarioId != null && (
            <button
              onClick={clearScenarioFilter}
              className="inline-flex items-center gap-1 px-2 h-6 rounded-md border border-brand-500/40 bg-brand-500/10 text-brand-300 hover:bg-brand-500/15"
            >
              scenario #{scenarioId} ×
            </button>
          )}
          {scenarioId != null && (
            <span className="text-[10px] text-ink-muted">Suite reports hidden while filtering by scenario.</span>
          )}
        </div>

        {feed.isLoading ? (
          <div className="text-ink-muted text-sm flex items-center gap-2"><Spinner /> Loading reports…</div>
        ) : filtered.length === 0 ? (
          <Card>
            <EmptyState
              icon={<Hourglass size={20} />}
              title={feed.items.length === 0 ? "No reports yet" : "Nothing matches this filter"}
              description={
                feed.items.length === 0
                  ? "Open a scenario or suite in the workspace and hit Run to start generating reports."
                  : "Adjust the tag filter above or clear it to see all reports."
              }
            />
          </Card>
        ) : (
          <div className="space-y-1.5">
            {filtered.map((f) =>
              f.kind === "suite"
                ? <SuiteRow key={`s-${f.item.id}`} sr={f.item} suggestions={allTags} />
                : <RunRow   key={`r-${f.item.id}`} run={f.item} suggestions={allTags} />,
            )}
          </div>
        )}
      </div>
    </>
  );
}

/* ─────────────────────────────  Rows  ──────────────────────────────── */

function SuiteRow({ sr, suggestions }: { sr: SuiteRunSummary; suggestions: string[] }) {
  const qc = useQueryClient();
  const mut = useMutation({
    mutationFn: (tags: string[]) => suiteRunApi.updateTags(sr.id, tags),
    onSuccess: () => {
      // Refresh the feed so the new tag appears in the filter bar and on neighbours.
      qc.invalidateQueries({ queryKey: ["automation-suite-runs"] });
      qc.invalidateQueries({ queryKey: ["automation-suite-run-detail", sr.id] });
    },
  });

  const tone = suiteTone(sr.status);
  const total = sr.totalScenarios || 0;
  const passPct = total > 0 ? (sr.passedScenarios / total) * 100 : 0;
  const failPct = total > 0 ? (sr.failedScenarios / total) * 100 : 0;

  return (
    <Link
      to={`/automation/suite-runs/${sr.id}`}
      className={cn(
        "block rounded-md border border-surface-border bg-surface hover:border-brand-500/30 hover:bg-surface-muted/40 transition-colors",
        sr.status === "FAILED" || sr.status === "ERROR" ? "border-l-2 border-l-danger-500" : "",
        sr.status === "PASSED"  ? "border-l-2 border-l-success-500" : "",
        sr.status === "RUNNING" ? "border-l-2 border-l-brand-500" : "",
      )}
    >
      <div className="px-4 py-3 flex items-center gap-4 flex-wrap">
        <Layers size={16} className="text-brand-300 shrink-0" />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-medium truncate">
              {sr.suiteName ?? "(suite deleted)"}
            </span>
            <span className="text-[10px] text-ink-muted font-mono">#{sr.id}</span>
            <span className="text-[10px] uppercase tracking-wider rounded px-1.5 py-0.5 border border-brand-500/30 text-brand-300">
              suite
            </span>
          </div>
          <div className="text-[11px] text-ink-muted flex items-center gap-2.5 mt-0.5 flex-wrap">
            <span>Device #{sr.deviceId ?? "?"}</span>
            <span>env <code className="font-mono">{sr.environment}</code></span>
            <span>{new Date(sr.createdAt).toLocaleString()}</span>
            {sr.durationMs != null && (
              <span className="inline-flex items-center gap-1">
                <Clock size={10} /> {(sr.durationMs / 1000).toFixed(1)}s
              </span>
            )}
          </div>
          <div className="mt-1.5" onClick={(e) => e.preventDefault()}>
            <TagEditor
              tags={sr.tags ?? []}
              suggestions={suggestions}
              size="sm"
              onChange={(next) => mut.mutateAsync(next).then(() => undefined)}
            />
          </div>
        </div>
        <div className="flex items-center gap-3 shrink-0">
          <div className="hidden sm:flex flex-col items-end">
            <div className="text-[11px] text-ink-muted font-mono">
              {sr.passedScenarios}/{total} passed
              {sr.failedScenarios > 0 ? ` · ${sr.failedScenarios} failed` : ""}
            </div>
            {total > 0 && (
              <div className="mt-1 h-1 w-32 rounded-full bg-surface-muted overflow-hidden flex">
                <div className="bg-success-500" style={{ width: `${passPct}%` }} />
                <div className="bg-danger-500"  style={{ width: `${failPct}%` }} />
              </div>
            )}
          </div>
          <StatusBadge tone={tone}>{sr.status}</StatusBadge>
          <ChevronRight size={14} className="text-ink-muted" />
        </div>
      </div>
    </Link>
  );
}

function RunRow({ run, suggestions }: { run: RunSummary; suggestions: string[] }) {
  const qc = useQueryClient();
  const mut = useMutation({
    mutationFn: (tags: string[]) => runApi.updateTags(run.id, tags),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["automation-runs"] });
      qc.invalidateQueries({ queryKey: ["automation-run", run.id] });
    },
  });

  const tone = runTone(run.status);
  const Icon = runIcon(run.status);
  const total = run.totalSteps || 0;
  const passPct = total > 0 ? (run.passedSteps / total) * 100 : 0;
  const failPct = total > 0 ? (run.failedSteps / total) * 100 : 0;
  return (
    <Link
      to={`/automation/runs/${run.id}`}
      className={cn(
        "block rounded-md border border-surface-border bg-surface hover:border-brand-500/30 hover:bg-surface-muted/40 transition-colors",
        run.status === "FAILED" || run.status === "ERROR" ? "border-l-2 border-l-danger-500" : "",
        run.status === "PASSED"  ? "border-l-2 border-l-success-500" : "",
        run.status === "RUNNING" ? "border-l-2 border-l-brand-500" : "",
      )}
    >
      <div className="px-4 py-3 flex items-center gap-4 flex-wrap">
        <Icon size={16} className={cn("shrink-0", runColor(run.status))} />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-medium truncate">
              {run.scenarioName ?? "(scenario deleted)"}
            </span>
            <span className="text-[10px] text-ink-muted font-mono">#{run.id}</span>
            {run.videoUrl && (
              <span className="inline-flex items-center gap-0.5 text-[10px] text-ink-muted" title="Recording available">
                <Film size={10} />
              </span>
            )}
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
          <div className="mt-1.5" onClick={(e) => e.preventDefault()}>
            <TagEditor
              tags={run.tags ?? []}
              suggestions={suggestions}
              size="sm"
              onChange={(next) => mut.mutateAsync(next).then(() => undefined)}
            />
          </div>
        </div>
        <div className="flex items-center gap-3 shrink-0">
          <div className="hidden sm:flex flex-col items-end">
            <div className="text-[11px] text-ink-muted font-mono">
              {run.passedSteps}/{total} passed
              {run.failedSteps > 0 ? ` · ${run.failedSteps} failed` : ""}
            </div>
            {total > 0 && (
              <div className="mt-1 h-1 w-32 rounded-full bg-surface-muted overflow-hidden flex">
                <div className="bg-success-500" style={{ width: `${passPct}%` }} />
                <div className="bg-danger-500"  style={{ width: `${failPct}%` }} />
              </div>
            )}
          </div>
          <StatusBadge tone={tone}>{run.status}</StatusBadge>
          <ChevronRight size={14} className="text-ink-muted" />
        </div>
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
function runTone(s: RunStatus): "success" | "warning" | "danger" | "info" | "neutral" {
  if (s === "PASSED")  return "success";
  if (s === "FAILED" || s === "ERROR") return "danger";
  if (s === "RUNNING") return "info";
  if (s === "QUEUED")  return "warning";
  return "neutral";
}
function runIcon(s: RunStatus) {
  switch (s) {
    case "PASSED":    return CheckCircle2;
    case "FAILED":    return XCircle;
    case "ERROR":     return AlertOctagon;
    case "RUNNING":   return Hourglass;
    case "QUEUED":    return PauseCircle;
    case "CANCELLED": return XCircle;
  }
}
function runColor(s: RunStatus): string {
  switch (s) {
    case "PASSED":    return "text-success-500";
    case "FAILED":    return "text-danger-500";
    case "ERROR":     return "text-danger-500";
    case "RUNNING":   return "text-brand-400 animate-pulse";
    case "QUEUED":    return "text-warning-500";
    case "CANCELLED": return "text-ink-muted";
  }
}
