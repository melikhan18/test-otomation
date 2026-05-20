import { useNavigate } from "react-router-dom";
import { ArrowLeft, ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/Button";
import {
  neighborPath, neighborsFor, useReportFeed,
} from "@/lib/reports";

type Props = {
  /** Which report we're currently viewing. */
  current: { kind: "run" | "suite"; id: number };
};

/**
 * Prev / Next buttons + "X of N" indicator + Back-to-list. Shares the same query keys
 * as ReportsPage so navigation hits the in-memory cache; no extra fetch round-trip.
 */
export default function ReportNav({ current }: Props) {
  const nav = useNavigate();
  const feed = useReportFeed();
  const { prev, next, position } = neighborsFor(feed.items, current);

  const prevHref = neighborPath(prev);
  const nextHref = neighborPath(next);

  return (
    <>
      <Button variant="ghost" size="sm" leftIcon={<ArrowLeft size={14} />} onClick={() => nav("/automation/reports")}>
        Back
      </Button>

      <div className="hidden md:flex items-center gap-1 px-2 h-8 rounded-md border border-surface-border bg-surface text-[11px] text-ink-muted font-mono">
        {position.idx >= 0 ? `${position.idx + 1} / ${position.total}` : "—"}
      </div>

      <Button
        variant="ghost" size="sm"
        leftIcon={<ChevronLeft size={14} />}
        disabled={!prevHref}
        onClick={() => prevHref && nav(prevHref)}
        title="Newer report (J)"
      >
        Newer
      </Button>
      <Button
        variant="ghost" size="sm"
        rightIcon={<ChevronRight size={14} />}
        disabled={!nextHref}
        onClick={() => nextHref && nav(nextHref)}
        title="Older report (K)"
      >
        Older
      </Button>
    </>
  );
}
