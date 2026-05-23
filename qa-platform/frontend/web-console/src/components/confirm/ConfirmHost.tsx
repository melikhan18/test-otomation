import { useEffect } from "react";
import { AlertTriangle, HelpCircle } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { useConfirmStore } from "./confirmStore";

/**
 * Mounted once at the app root (main.tsx). Subscribes to {@link useConfirmStore}
 * and renders the active confirm request as a themed modal. Plays nicely with
 * the existing dialog backdrop selector (auto scale-in entrance applies).
 *
 * Keyboard: Esc → cancel, Enter → confirm. Focus the primary button on open
 * so the keyboard user can confirm without reaching for the mouse.
 */
export function ConfirmHost() {
  const pending = useConfirmStore((s) => s.pending);
  const resolve = useConfirmStore((s) => s.resolve);

  // Esc / Enter shortcuts while a dialog is open.
  useEffect(() => {
    if (!pending) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") { e.preventDefault(); resolve(false); }
      if (e.key === "Enter")  { e.preventDefault(); resolve(true); }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [pending, resolve]);

  if (!pending) return null;

  const Icon = pending.danger ? AlertTriangle : HelpCircle;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-md">
        <div className="px-5 py-4 flex items-start gap-3">
          <div className={
            "h-9 w-9 rounded-full flex items-center justify-center shrink-0 " +
            (pending.danger
              ? "bg-danger-500/10 text-danger-500"
              : "bg-brand-500/10 text-brand-500")
          }>
            <Icon size={18} />
          </div>
          <div className="min-w-0 flex-1">
            <div className="text-sm font-semibold text-ink-primary">{pending.title}</div>
            {pending.description && (
              <div className="text-xs text-ink-secondary mt-1 leading-relaxed">{pending.description}</div>
            )}
          </div>
        </div>
        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={() => resolve(false)}>
            {pending.cancelLabel ?? "Cancel"}
          </Button>
          <Button
            variant={pending.danger ? "danger" : "primary"}
            autoFocus
            onClick={() => resolve(true)}
          >
            {pending.confirmLabel ?? "Confirm"}
          </Button>
        </div>
      </Card>
    </div>
  );
}
