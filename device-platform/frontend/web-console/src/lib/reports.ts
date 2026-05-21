import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  runApi, suiteRunApi,
  type RunSummary, type SuiteRunSummary,
} from "@/lib/automation";
import { useAuthStore } from "@/store/auth";

/* ───────────────────────────  Feed model  ────────────────────────────
 * One unified report stream: standalone runs (suiteRunId == null) plus suite
 * runs, sorted by createdAt desc. Both pages — list and detail — derive their
 * data from the SAME query keys, so prev/next navigation hits the cache
 * instantly and doesn't refetch.
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
  const tenancyReady = activeCompanyId != null;

  const runsQ = useQuery({
    queryKey: ["automation-runs", activeCompanyId ?? null, scenarioId ?? null],
    queryFn: () => runApi.list(scenarioId),
    refetchInterval: (q) =>
      (q.state.data ?? []).some((r) => r.status === "QUEUED" || r.status === "RUNNING")
        ? POLL_LIVE_MS : POLL_IDLE_MS,
    refetchOnWindowFocus: false,
    enabled: tenancyReady,
  });

  // Suite-level rows are unrelated to a single scenario filter, so we omit
  // them when scenarioId is set — feed becomes pure run view in that mode.
  const suitesQ = useQuery({
    queryKey: ["automation-suite-runs", activeCompanyId ?? null],
    queryFn: () => suiteRunApi.list(),
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
    items,
    isLoading: runsQ.isLoading || (scenarioId == null && suitesQ.isLoading),
    isFetching: runsQ.isFetching || suitesQ.isFetching,
    refetch: () => { runsQ.refetch(); if (scenarioId == null) suitesQ.refetch(); },
  };
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
