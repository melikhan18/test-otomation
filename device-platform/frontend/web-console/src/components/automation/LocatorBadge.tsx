import type { LocatorStrategy } from "@/lib/automation";
import { cn } from "@/lib/cn";

const COLORS: Record<LocatorStrategy, string> = {
  RESOURCE_ID:      "border-success-500/40 bg-success-500/10 text-success-500",
  ACCESSIBILITY_ID: "border-brand-500/40    bg-brand-500/10    text-brand-300",
  TEXT:             "border-warning-500/40  bg-warning-500/10  text-warning-500",
  CLASS:            "border-surface-border  bg-surface-muted   text-ink-secondary",
  XPATH:            "border-danger-500/30   bg-danger-500/10   text-danger-500",
};

const SHORT: Record<LocatorStrategy, string> = {
  RESOURCE_ID: "id",
  ACCESSIBILITY_ID: "a11y-id",
  TEXT: "text",
  CLASS: "class",
  XPATH: "xpath",
};

export function LocatorBadge({ strategy, className }: { strategy: LocatorStrategy; className?: string }) {
  return (
    <span className={cn(
      "inline-flex items-center text-[10px] font-mono uppercase tracking-wider rounded px-1.5 py-0.5 border",
      COLORS[strategy], className,
    )}>
      {SHORT[strategy]}
    </span>
  );
}
