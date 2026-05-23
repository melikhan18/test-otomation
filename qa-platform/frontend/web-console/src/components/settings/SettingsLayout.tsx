import { AlertTriangle } from "lucide-react";
import { Card } from "@/components/ui/Card";
import { cn } from "@/lib/cn";

/**
 * Building blocks shared by every enterprise-style settings page (Company,
 * Project detail, …). Each page is one centered, scrollable column of
 * {@link Section}s.
 */

/**
 * @param width - "default" (max-w-3xl) for form-heavy pages with FormRow stacks,
 *                "wide" (max-w-6xl) for table-driven admin pages that need horizontal
 *                breathing room.
 */
export function SettingsLayout({
  children, width = "default",
}: { children: React.ReactNode; width?: "default" | "wide" }) {
  const widthClass = width === "wide" ? "max-w-6xl" : "max-w-3xl";
  return (
    <div className="px-6 py-6 flex justify-center">
      <main className={cn("w-full space-y-8", widthClass)}>{children}</main>
    </div>
  );
}

/**
 * One titled card within a settings page. Use {@code tone="danger"} for
 * destructive zones — adds the red accent border + warning icon.
 */
export function Section({
  id, title, description, tone = "default", action, children,
}: {
  id: string;
  title: string;
  /** Plain text or rich JSX (e.g. inline badges, links). */
  description?: React.ReactNode;
  tone?: "default" | "danger";
  action?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <section id={id} className="scroll-mt-6">
      <Card className={cn("overflow-hidden", tone === "danger" && "border-danger-500/30")}>
        <header className="px-5 py-4 border-b border-surface-border flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className={cn(
              "text-sm font-semibold flex items-center gap-2",
              tone === "danger" && "text-danger-500",
            )}>
              {tone === "danger" && <AlertTriangle size={14} />}
              {title}
            </h2>
            {description && (
              <div className="text-[11px] text-ink-muted mt-0.5 max-w-prose">{description}</div>
            )}
          </div>
          {action && <div className="shrink-0">{action}</div>}
        </header>
        <div className="p-5 space-y-4">{children}</div>
      </Card>
    </section>
  );
}

/** Convenience wrapper for two-column form rows (label left, control right). */
export function FormRow({
  label, hint, children,
}: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <label className="block grid grid-cols-1 md:grid-cols-3 gap-3 md:gap-4">
      <div className="md:pt-1.5">
        <span className="text-xs font-medium block">{label}</span>
        {hint && <span className="text-[10px] text-ink-muted block mt-0.5">{hint}</span>}
      </div>
      <div className="md:col-span-2">{children}</div>
    </label>
  );
}
