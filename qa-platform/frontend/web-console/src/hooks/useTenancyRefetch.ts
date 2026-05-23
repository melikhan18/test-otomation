import { useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/store/auth";

/**
 * Invalidate every TanStack Query when the active company or project changes.
 *
 * Why blanket invalidation
 * ────────────────────────
 * Tenant scoping rides on the {@code X-Project-Id} request header (set by the
 * axios interceptor from the auth store). The store change updates the header,
 * but cached query results are keyed only by their human-readable key
 * (e.g. {@code ["automation-scenarios"]}) — not by the active project. So an
 * untouched page would keep showing the previous workspace's data.
 *
 * We could namespace every query key with {@code activeProjectId}, but that
 * means editing dozens of {@code useQuery} call sites and risking bugs every
 * time a new page is added. A single {@code invalidateQueries()} call on
 * switch is correct, cheap, and forgets-to-do-X-proof.
 *
 * Skip-first-render
 * ─────────────────
 * On initial mount the active ids change from {@code null → real} during
 * rehydration; firing an invalidate then is harmless (cache is empty) but
 * we still guard it to avoid noise in the devtools panel.
 */
export function useTenancyRefetch() {
  const qc = useQueryClient();
  const activeCompanyId = useAuthStore((s) => s.activeCompanyId);
  const activeProjectId = useAuthStore((s) => s.activeProjectId);
  const firstRun = useRef(true);

  useEffect(() => {
    if (firstRun.current) { firstRun.current = false; return; }
    qc.invalidateQueries();
  }, [activeCompanyId, activeProjectId, qc]);
}
