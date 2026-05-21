import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import {
  AlertOctagon, Bell, BellRing, CheckCircle2, Layers, ListChecks, Mail, X, XCircle,
} from "lucide-react";
import {
  notificationApi, openNotificationStream,
  type NotificationStatus, type NotificationType, type NotificationView,
} from "@/lib/notifications";
import { useAuthStore } from "@/store/auth";
import { cn } from "@/lib/cn";

/**
 * Notification bell shown in the global header.
 *
 * Data flow
 * ─────────
 * 1. {@link notificationApi.unreadCount} drives the red dot badge.
 * 2. The dropdown opens → fetches the recent list (top 100).
 * 3. An SSE channel is open as long as the user is logged in; pushes invalidate
 *    both queries so badge + list update instantly.
 *
 * The SSE connection auto-reconnects (browser does it for us) and uses the
 * access token via query param because EventSource can't set custom headers.
 */
export default function NotificationBell() {
  const accessToken = useAuthStore((s) => s.accessToken);
  const reloadMemberships = useAuthStore((s) => s.reloadMemberships);
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const nav = useNavigate();

  const countQ = useQuery({
    queryKey: ["notifications-unread-count"],
    queryFn: notificationApi.unreadCount,
    enabled: !!accessToken,
    // Polling backup in case SSE drops. 30s is fine — SSE handles instant updates.
    refetchInterval: 30_000,
  });
  const listQ = useQuery({
    queryKey: ["notifications"],
    queryFn: notificationApi.list,
    enabled: !!accessToken && open,   // lazy: only fetch when dropdown is open
  });

  // Real-time stream
  useEffect(() => {
    if (!accessToken) return;
    const es = openNotificationStream(accessToken);
    es.addEventListener("notification", () => {
      qc.invalidateQueries({ queryKey: ["notifications-unread-count"] });
      qc.invalidateQueries({ queryKey: ["notifications"] });
    });
    es.onerror = () => { /* EventSource auto-reconnects */ };
    return () => es.close();
  }, [accessToken, qc]);

  // Click outside dismiss
  useEffect(() => {
    if (!open) return;
    function onDoc(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, [open]);

  const markAllRead = useMutation({
    mutationFn: () => notificationApi.markAllRead(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["notifications-unread-count"] });
      qc.invalidateQueries({ queryKey: ["notifications"] });
    },
  });
  const accept = useMutation({
    mutationFn: (id: number) => notificationApi.accept(id),
    onSuccess: async () => {
      await reloadMemberships();
      qc.invalidateQueries({ queryKey: ["notifications"] });
      qc.invalidateQueries({ queryKey: ["notifications-unread-count"] });
    },
  });
  const decline = useMutation({
    mutationFn: (id: number) => notificationApi.decline(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["notifications"] });
      qc.invalidateQueries({ queryKey: ["notifications-unread-count"] });
    },
  });

  function onClickRow(n: NotificationView) {
    if (n.status === "UNREAD") notificationApi.markRead(n.id).then(() => {
      qc.invalidateQueries({ queryKey: ["notifications-unread-count"] });
    });
    // Type-dispatched navigation. Unknown types just close the dropdown.
    const link = navigateFor(n);
    if (link) { setOpen(false); nav(link); }
  }

  const unread = countQ.data ?? 0;

  if (!accessToken) return null;

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        className="relative h-9 w-9 rounded-md border border-surface-border bg-surface hover:border-brand-500/30 text-ink-secondary hover:text-ink-primary flex items-center justify-center"
        title="Notifications"
      >
        {unread > 0 ? <BellRing size={15} /> : <Bell size={15} />}
        {unread > 0 && (
          <span className="absolute -top-1 -right-1 min-w-[16px] h-4 px-1 rounded-full bg-danger-500 text-white text-[9px] font-semibold flex items-center justify-center">
            {unread > 99 ? "99+" : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 mt-1.5 w-[360px] max-h-[480px] z-30 rounded-md border border-surface-border bg-surface-raised shadow-xl flex flex-col">
          <div className="px-3 py-2 border-b border-surface-border flex items-center justify-between">
            <div className="text-xs font-semibold">Notifications</div>
            <div className="flex items-center gap-1">
              <button
                onClick={() => markAllRead.mutate()}
                disabled={markAllRead.isPending || unread === 0}
                className="text-[10px] text-ink-muted hover:text-brand-300 px-2 h-6 rounded hover:bg-surface-muted disabled:opacity-40"
              >
                Mark all read
              </button>
              <button onClick={() => setOpen(false)} className="text-ink-muted hover:text-ink-primary p-1 rounded hover:bg-surface-muted">
                <X size={12} />
              </button>
            </div>
          </div>

          <div className="flex-1 overflow-auto">
            {listQ.isLoading ? (
              <div className="p-4 text-ink-muted text-xs">Loading…</div>
            ) : (listQ.data ?? []).filter((n) => n.status !== "DISMISSED").length === 0 ? (
              <div className="p-6 text-center text-ink-muted text-xs">No notifications yet.</div>
            ) : (
              <ul className="divide-y divide-surface-border">
                {(listQ.data ?? [])
                  .filter((n) => n.status !== "DISMISSED")
                  .map((n) => (
                  <NotificationRow
                    key={n.id}
                    n={n}
                    onClick={() => onClickRow(n)}
                    onAccept={() => accept.mutate(n.id)}
                    onDecline={() => decline.mutate(n.id)}
                    busy={accept.isPending || decline.isPending}
                  />
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

/* ─────────────────────────────  Row  ───────────────────────────────── */

function NotificationRow({
  n, onClick, onAccept, onDecline, busy,
}: {
  n: NotificationView;
  onClick: () => void;
  onAccept: () => void;
  onDecline: () => void;
  busy: boolean;
}) {
  const isInvite = n.type === "COMPANY_INVITATION";
  const isPending = isInvite && n.status === "UNREAD";
  const Icon = iconFor(n.type, n.status);
  const tone = toneFor(n.type, n.status);
  const title = titleFor(n);
  const subtitle = subtitleFor(n);

  return (
    <li
      onClick={onClick}
      className={cn(
        "px-3 py-2.5 cursor-pointer hover:bg-surface-muted/40 transition-colors",
        n.status === "UNREAD" && "bg-brand-500/[0.04]",
      )}
    >
      <div className="flex items-start gap-2.5">
        <Icon size={14} className={cn("mt-0.5 shrink-0", tone)} />
        <div className="min-w-0 flex-1">
          <div className="text-xs font-medium leading-snug">{title}</div>
          {subtitle && <div className="text-[11px] text-ink-muted mt-0.5">{subtitle}</div>}
          <div className="text-[10px] text-ink-muted mt-1 font-mono">
            {timeAgo(n.createdAt)}
            {n.status !== "UNREAD" && (
              <span className="ml-2 uppercase">{statusLabel(n.status)}</span>
            )}
          </div>
          {isPending && (
            <div className="flex items-center gap-1.5 mt-2" onClick={(e) => e.stopPropagation()}>
              <button
                onClick={onAccept}
                disabled={busy}
                className="h-6 px-2 rounded text-[10px] font-medium bg-success-500/20 text-success-500 hover:bg-success-500/30 disabled:opacity-50"
              >
                Accept
              </button>
              <button
                onClick={onDecline}
                disabled={busy}
                className="h-6 px-2 rounded text-[10px] font-medium border border-surface-border text-ink-secondary hover:text-ink-primary disabled:opacity-50"
              >
                Decline
              </button>
            </div>
          )}
        </div>
        {n.status === "UNREAD" && (
          <span className="h-1.5 w-1.5 rounded-full bg-brand-500 shrink-0 mt-1.5" title="Unread" />
        )}
      </div>
    </li>
  );
}

/* ─────────────────────────────  helpers  ──────────────────────────── */

function iconFor(type: NotificationType, status: NotificationStatus) {
  if (type === "COMPANY_INVITATION") return Mail;
  if (type === "RUN_COMPLETED") return CheckCircle2;
  if (type === "RUN_FAILED") return XCircle;
  if (type === "SUITE_RUN_COMPLETED") return Layers;
  if (status === "ACCEPTED") return CheckCircle2;
  if (status === "DECLINED") return XCircle;
  return AlertOctagon;
}
function toneFor(type: NotificationType, status: NotificationStatus) {
  if (status === "ACCEPTED") return "text-success-500";
  if (status === "DECLINED") return "text-ink-muted";
  if (type === "RUN_FAILED") return "text-danger-500";
  if (type === "RUN_COMPLETED" || type === "SUITE_RUN_COMPLETED") return "text-success-500";
  if (type === "COMPANY_INVITATION") return "text-brand-400";
  return "text-ink-muted";
}

function titleFor(n: NotificationView): string {
  const p = n.payload;
  switch (n.type) {
    case "COMPANY_INVITATION":
      return `${p.inviterUsername ?? "Someone"} invited you to ${p.companyName ?? "a company"}`;
    case "RUN_COMPLETED":
      return `Run #${p.runId ?? "?"} passed`;
    case "RUN_FAILED":
      return `Run #${p.runId ?? "?"} failed`;
    case "SUITE_RUN_COMPLETED":
      return `Suite run #${p.suiteRunId ?? "?"} ${p.status === "PASSED" ? "passed" : "finished"}`;
    case "MEMBER_ADDED":
      return `You were added to ${p.companyName ?? "a company"}`;
    case "ROLE_CHANGED":
      return `Your role in ${p.companyName ?? "a company"} is now ${p.role ?? "?"}`;
    case "PROJECT_INVITED":
      return `You were added to project ${p.projectName ?? "?"}`;
    default:
      return n.type;
  }
}
function subtitleFor(n: NotificationView): string | null {
  if (n.type === "COMPANY_INVITATION") {
    if (n.payload.owner) return "as company OWNER";
    const grants = Array.isArray(n.payload.grants) ? n.payload.grants : [];
    if (grants.length === 0) return "with no project access";
    // Inline summary: "QA Manager on BIP, Tester on Lifebox"
    return grants
      .map((g: any) => `${g.role === "QA_MANAGER" ? "QA Manager" : "Tester"} on ${g.projectName ?? "project #" + g.projectId}`)
      .join(", ");
  }
  if (n.type === "RUN_COMPLETED" || n.type === "RUN_FAILED") return n.payload.scenarioName ?? null;
  if (n.type === "SUITE_RUN_COMPLETED") return n.payload.suiteName ?? null;
  return null;
}
function navigateFor(n: NotificationView): string | null {
  const p = n.payload;
  switch (n.type) {
    case "RUN_COMPLETED":
    case "RUN_FAILED":
      return p.runId ? `/automation/runs/${p.runId}` : null;
    case "SUITE_RUN_COMPLETED":
      return p.suiteRunId ? `/automation/suite-runs/${p.suiteRunId}` : null;
    default:
      return null;
  }
}
function statusLabel(s: NotificationStatus) {
  switch (s) {
    case "ACCEPTED": return "Accepted";
    case "DECLINED": return "Declined";
    case "READ":     return "";
    default:         return s;
  }
}
function timeAgo(iso: string) {
  const ms = Date.now() - new Date(iso).getTime();
  const s = Math.floor(ms / 1000);
  if (s < 60) return "just now";
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.floor(h / 24)}d ago`;
}
