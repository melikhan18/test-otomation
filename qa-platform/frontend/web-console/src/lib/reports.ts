import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  runApi, suiteRunApi,
  type RunSummary, type RunView, type SuiteRunSummary, type SuiteRunView,
  type SuiteRunChild, type StepResultView,
} from "@/lib/automation";
import {
  webRunApi, webSuiteRunApi,
  type WebRunSummary, type WebRunView, type WebStepResultView,
  type WebSuiteRunSummary, type WebSuiteRunView, type WebSuiteRunChild,
} from "@/lib/webAutomation";
import { useAuthStore, type Platform } from "@/store/auth";

/* ───────────────────────────  Feed model  ────────────────────────────
 * One unified report stream: standalone runs (suiteRunId == null) plus suite
 * runs, sorted by createdAt desc. Both pages — list and detail — derive their
 * data from the SAME query keys, so prev/next navigation hits the cache
 * instantly and doesn't refetch.
 *
 * The feed is platform-aware. ANDROID uses /api/automation/runs (legacy
 * gateway route) and lives in the automation-service DB. WEB uses
 * /api/runs + /api/suite-runs (gateway dispatches via X-Platform header) and
 * lives in the web-runner-service DB. Shapes differ slightly — WEB runs
 * carry a browserProfileId where Android carries a deviceId, and WEB has no
 * tags/cancel endpoints yet — so we adapt the WEB responses into the unified
 * shape and stamp each item with `_platform` so the row renderer can branch.
 */

export type ReportItem =
  | { kind: "run";   at: number; item: RunSummary }
  | { kind: "suite"; at: number; item: SuiteRunSummary };

const POLL_LIVE_MS = 1500;
const POLL_IDLE_MS = 6000;

export function useReportFeed(opts?: { scenarioId?: number }) {
  const scenarioId = opts?.scenarioId;
  // No workspace → skip every poll. Otherwise the 1.5s/6s interval spams the
  // gateway with 403s before AppLayout's gate even gets a chance to swap views.
  const activeCompanyId = useAuthStore((s) => s.activeCompanyId);
  const platform = useAuthStore((s) => s.activePlatform);
  const tenancyReady = activeCompanyId != null;

  const runsQ = useQuery({
    // Platform is part of the key so swapping between WEB ⇄ ANDROID doesn't
    // serve stale data from the other stack's cache.
    queryKey: ["report-runs", platform, activeCompanyId ?? null, scenarioId ?? null],
    queryFn: async () => {
      if (platform === "WEB") {
        const rows = await webRunApi.list(scenarioId);
        return rows.map(adaptWebRun);
      }
      return runApi.list(scenarioId);
    },
    refetchInterval: (q) =>
      (q.state.data ?? []).some((r) => r.status === "QUEUED" || r.status === "RUNNING")
        ? POLL_LIVE_MS : POLL_IDLE_MS,
    refetchOnWindowFocus: false,
    enabled: tenancyReady,
  });

  // Suite-level rows are unrelated to a single scenario filter, so we omit
  // them when scenarioId is set — feed becomes pure run view in that mode.
  const suitesQ = useQuery({
    queryKey: ["report-suite-runs", platform, activeCompanyId ?? null],
    queryFn: async () => {
      if (platform === "WEB") {
        const rows = await webSuiteRunApi.list();
        return rows.map(adaptWebSuiteRun);
      }
      return suiteRunApi.list();
    },
    refetchInterval: (q) =>
      (q.state.data ?? []).some((r) => r.status === "QUEUED" || r.status === "RUNNING")
        ? POLL_LIVE_MS : POLL_IDLE_MS,
    refetchOnWindowFocus: false,
    enabled: tenancyReady && scenarioId == null,
  });

  const items: ReportItem[] = useMemo(() => {
    const standalone = (runsQ.data ?? [])
      .filter((r) => r.suiteRunId == null)
      .map<ReportItem>((r) => ({ kind: "run", at: Date.parse(r.createdAt), item: r }));
    const suites = (suitesQ.data ?? [])
      .map<ReportItem>((s) => ({ kind: "suite", at: Date.parse(s.createdAt), item: s }));
    return [...suites, ...standalone].sort((a, b) => b.at - a.at);
  }, [runsQ.data, suitesQ.data]);

  return {
    platform,
    items,
    isLoading: runsQ.isLoading || (scenarioId == null && suitesQ.isLoading),
    isFetching: runsQ.isFetching || suitesQ.isFetching,
    refetch: () => { runsQ.refetch(); if (scenarioId == null) suitesQ.refetch(); },
  };
}

/* ─────────────────────────  WEB ⇄ Android adapters  ───────────────────
 * Synthesize the missing Android-specific fields (deviceId, tags, suiteRunId,
 * targetAppVersionId, appPrepStatus) so the unified ReportsPage row renderer
 * doesn't need a discriminated union. The browserProfileId is carried in a
 * conventional location (deviceId is null + scenarioName tells us the rest)
 * — for v1 we just lose the chip; we can add a sibling field later when we
 * upgrade the row to show "Browser <profile>" instead of "Device #N".
 */

function adaptWebRun(r: WebRunSummary): RunSummary {
  return {
    id: r.id,
    scenarioId: r.scenarioId,
    scenarioName: r.scenarioName,
    deviceId: null,
    environment: r.environment,
    status: r.status,
    totalSteps: r.totalSteps,
    passedSteps: r.passedSteps,
    failedSteps: r.failedSteps,
    durationMs: r.durationMs,
    videoUrl: r.videoUrl,
    suiteRunId: null,
    tags: [],
    createdAt: r.createdAt,
    startedAt: r.startedAt,
    finishedAt: r.finishedAt,
    targetAppVersionId: null,
    appPrepStatus: null,
  };
}

function adaptWebSuiteRun(s: WebSuiteRunSummary): SuiteRunSummary {
  return {
    id: s.id,
    suiteId: s.suiteId,
    suiteName: s.suiteName,
    deviceId: null,
    environment: s.environment,
    status: s.status,
    totalScenarios: s.totalScenarios,
    passedScenarios: s.passedScenarios,
    failedScenarios: s.failedScenarios,
    durationMs: s.durationMs,
    tags: [],
    createdAt: s.createdAt,
    startedAt: s.startedAt,
    finishedAt: s.finishedAt,
  };
}

/* ──────────────────  Detail-view adapters (WEB → unified)  ───────────── */

function adaptWebStepResult(r: WebStepResultView, idx: number): StepResultView {
  return {
    id: r.id,
    stepId: null,
    orderIndex: idx,
    action: r.action as unknown as StepResultView["action"],
    status: r.status,
    startedAt: r.startedAt,
    finishedAt: r.finishedAt,
    durationMs: r.durationMs,
    errorMessage: r.errorMessage,
    screenshotUrl: r.screenshotUrl,
    resolvedLocator: null,
    retriesUsed: 0,
  };
}

export function adaptWebRunView(r: WebRunView): RunView {
  return {
    id: r.id,
    scenarioId: r.scenarioId,
    scenarioName: r.scenarioName,
    scenarioVersion: r.scenarioVersion,
    deviceId: null,
    sessionId: null,
    environment: r.environment,
    status: r.status,
    triggerType: "MANUAL",
    triggeredByUserId: r.triggeredByUserId,
    startedAt: r.startedAt,
    finishedAt: r.finishedAt,
    durationMs: r.durationMs,
    totalSteps: r.totalSteps,
    passedSteps: r.passedSteps,
    failedSteps: r.failedSteps,
    errorSummary: r.errorSummary,
    interStepDelayMs: 0,
    adaptiveWait: false,
    videoUrl: r.videoUrl,
    tags: [],
    createdAt: r.createdAt,
    stepResults: r.stepResults.map((sr, i) => adaptWebStepResult(sr, i)),
    targetAppVersionId: null,
    targetApp: null,
    appPrepStatus: null,
    appPrepDurationMs: null,
    appPrepError: null,
    resetHomeAfter: false,
    killProcessAfter: false,
  };
}

function adaptWebSuiteChild(c: WebSuiteRunChild): SuiteRunChild {
  return {
    id: c.id,
    scenarioId: c.scenarioId,
    scenarioName: c.scenarioName,
    status: c.status,
    totalSteps: c.totalSteps,
    passedSteps: c.passedSteps,
    failedSteps: c.failedSteps,
    durationMs: c.durationMs,
    videoUrl: c.videoUrl,
    startedAt: c.startedAt,
    finishedAt: c.finishedAt,
    appPrepStatus: null,
  };
}

export function adaptWebSuiteRunView(s: WebSuiteRunView): SuiteRunView {
  return {
    id: s.id,
    suiteId: s.suiteId,
    suiteName: s.suiteName,
    deviceId: null,
    environment: s.environment,
    status: s.status,
    triggerType: "MANUAL",
    triggeredByUserId: s.triggeredByUserId,
    startedAt: s.startedAt,
    finishedAt: s.finishedAt,
    durationMs: s.durationMs,
    totalScenarios: s.totalScenarios,
    passedScenarios: s.passedScenarios,
    failedScenarios: s.failedScenarios,
    errorSummary: s.errorSummary,
    tags: [],
    createdAt: s.createdAt,
    runs: s.runs.map(adaptWebSuiteChild),
    targetAppVersionId: null,
    targetApp: null,
    resetHomeAfter: false,
    killProcessAfter: false,
  };
}

/** Platform-aware run.get — returns a unified RunView shape. */
export async function fetchRunView(platform: Platform, id: number): Promise<RunView> {
  if (platform === "WEB") return adaptWebRunView(await webRunApi.get(id));
  return runApi.get(id);
}

/** Platform-aware suite-run.get — returns a unified SuiteRunView shape. */
export async function fetchSuiteRunView(platform: Platform, id: number): Promise<SuiteRunView> {
  if (platform === "WEB") return adaptWebSuiteRunView(await webSuiteRunApi.get(id));
  return suiteRunApi.get(id);
}

/* ───────────────────────────  Tag helpers  ──────────────────────────── */

/** Returns every distinct tag in the feed, alphabetically sorted. */
export function distinctTags(items: ReportItem[]): string[] {
  const set = new Set<string>();
  for (const it of items) {
    for (const t of it.item.tags ?? []) set.add(t);
  }
  return [...set].sort();
}

/** ANY-match — show item if it carries at least one of the selected tags. */
export function matchesTagFilter(item: ReportItem, selected: string[]): boolean {
  if (selected.length === 0) return true;
  const tags = item.item.tags ?? [];
  if (tags.length === 0) return false;
  return selected.some((t) => tags.includes(t));
}

/* ───────────────────────────  Neighbor lookup  ──────────────────────── */

export type Neighbor =
  | { kind: "run";   id: number }
  | { kind: "suite"; id: number }
  | null;

/** Returns prev/next siblings and "X of N" position for the given item. */
export function neighborsFor(
  items: ReportItem[],
  current: { kind: "run" | "suite"; id: number },
): { prev: Neighbor; next: Neighbor; position: { idx: number; total: number } } {
  const idx = items.findIndex((f) => f.kind === current.kind && f.item.id === current.id);
  const total = items.length;
  if (idx < 0) return { prev: null, next: null, position: { idx: -1, total } };
  // Feed is desc by createdAt → "next" (chronologically older) is items[idx+1].
  const prev = idx > 0           ? toNeighbor(items[idx - 1]) : null;
  const next = idx < total - 1   ? toNeighbor(items[idx + 1]) : null;
  return { prev, next, position: { idx, total } };
}

function toNeighbor(it: ReportItem): Neighbor {
  return { kind: it.kind, id: it.item.id };
}

export function neighborPath(n: Neighbor): string | null {
  if (!n) return null;
  return n.kind === "suite" ? `/automation/suite-runs/${n.id}` : `/automation/runs/${n.id}`;
}

/* ────────────────────  Platform-aware compatibility flag  ───────────── */

/** WEB platform has no cancel/tags endpoints — Reports rows hide those
 *  controls when this returns false. */
export function platformSupportsRunTagsAndCancel(p: Platform): boolean {
  return p === "ANDROID";
}
