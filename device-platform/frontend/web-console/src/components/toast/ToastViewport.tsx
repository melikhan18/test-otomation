import { AlertCircle, AlertTriangle, CheckCircle2, Info, X } from "lucide-react";
import { useToastStore, useToasts, type Toast, type ToastKind } from "./toastStore";
import { cn } from "@/lib/cn";

/**
 * Render layer for {@link toast}. Mount once at the app root (see main.tsx).
 * Toasts stack bottom-right, newest at the bottom of the column so the most
 * recent action sits next to the cursor's natural attention point.
 *
 * Each toast slides up + fades in (via `animate-slide-up-fade`) and stays in
 * the DOM until either auto-TTL fires or the user clicks the close button.
 */
export function ToastViewport() {
  const toasts = useToasts();
  const dismiss = useToastStore((s) => s.dismiss);

  if (toasts.length === 0) return null;

  return (
    <div
      aria-live="polite"
      aria-atomic="true"
      className="fixed bottom-4 right-4 z-[60] flex flex-col gap-2 pointer-events-none"
    >
      {toasts.map((t) => (
        <ToastCard key={t.id} toast={t} onClose={() => dismiss(t.id)} />
      ))}
    </div>
  );
}

function ToastCard({ toast, onClose }: { toast: Toast; onClose: () => void }) {
  const Icon = iconFor(toast.kind);
  return (
    <div
      role={toast.kind === "error" ? "alert" : "status"}
      className={cn(
        "pointer-events-auto w-[360px] max-w-[calc(100vw-2rem)] rounded-md border bg-surface-raised shadow-pop overflow-hidden",
        "animate-slide-up-fade",
        accent(toast.kind),
      )}
    >
      <div className="flex items-start gap-3 px-3.5 py-3">
        <Icon size={16} className={cn("mt-0.5 shrink-0", iconColor(toast.kind))} />
        <div className="flex-1 min-w-0">
          <div className="text-sm font-semibold text-ink-primary leading-snug">{toast.title}</div>
          {toast.description && (
            <div className="text-xs text-ink-secondary mt-0.5 leading-relaxed">{toast.description}</div>
          )}
          {toast.action && (
            <button
              onClick={() => { toast.action!.onClick(); onClose(); }}
              className="mt-2 text-xs font-medium text-brand-500 hover:text-brand-600 transition-colors"
            >
              {toast.action.label}
            </button>
          )}
        </div>
        <button
          onClick={onClose}
          aria-label="Dismiss"
          className="text-ink-muted hover:text-ink-primary p-1 -m-1 rounded transition-colors"
        >
          <X size={13} />
        </button>
      </div>
    </div>
  );
}

/* ─────────────────────────  style helpers  ────────────────────────── */

function iconFor(kind: ToastKind) {
  switch (kind) {
    case "success": return CheckCircle2;
    case "warning": return AlertTriangle;
    case "error":   return AlertCircle;
    case "info":    return Info;
  }
}

function iconColor(kind: ToastKind): string {
  switch (kind) {
    case "success": return "text-success-500";
    case "warning": return "text-warning-500";
    case "error":   return "text-danger-500";
    case "info":    return "text-brand-500";
  }
}

/** Left-edge accent stripe matching the toast kind. */
function accent(kind: ToastKind): string {
  switch (kind) {
    case "success": return "border-surface-border border-l-2 border-l-success-500";
    case "warning": return "border-surface-border border-l-2 border-l-warning-500";
    case "error":   return "border-surface-border border-l-2 border-l-danger-500";
    case "info":    return "border-surface-border border-l-2 border-l-brand-500";
  }
}
