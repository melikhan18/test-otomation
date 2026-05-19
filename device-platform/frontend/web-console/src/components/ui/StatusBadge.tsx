import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

type Tone = "success" | "warning" | "danger" | "info" | "neutral";

const tones: Record<Tone, { dot: string; chip: string; text: string }> = {
  success: { dot: "bg-success-500", chip: "bg-success-500/10 border-success-500/30", text: "text-success-500" },
  warning: { dot: "bg-warning-500", chip: "bg-warning-500/10 border-warning-500/30", text: "text-warning-500" },
  danger:  { dot: "bg-danger-500",  chip: "bg-danger-500/10 border-danger-500/30",   text: "text-danger-500" },
  info:    { dot: "bg-brand-500",   chip: "bg-brand-500/10 border-brand-500/30",     text: "text-brand-400" },
  neutral: { dot: "bg-ink-muted",   chip: "bg-surface-muted border-surface-border",  text: "text-ink-secondary" },
};

export function StatusBadge({
  tone = "neutral",
  children,
  dot = true,
  className,
}: { tone?: Tone; children: ReactNode; dot?: boolean; className?: string }) {
  const t = tones[tone];
  return (
    <span className={cn(
      "inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md text-[10px] font-semibold uppercase tracking-wider border",
      t.chip, t.text, className,
    )}>
      {dot && <span className={cn("h-1.5 w-1.5 rounded-full", t.dot)} />}
      {children}
    </span>
  );
}
