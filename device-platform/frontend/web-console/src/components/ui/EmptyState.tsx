import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

export function EmptyState({
  icon, title, description, action, className,
}: { icon?: ReactNode; title: string; description?: string; action?: ReactNode; className?: string }) {
  return (
    <div className={cn("flex flex-col items-center justify-center text-center px-6 py-12", className)}>
      {icon && (
        <div className="h-12 w-12 rounded-xl border border-surface-border bg-surface-raised flex items-center justify-center text-ink-secondary mb-4">
          {icon}
        </div>
      )}
      <div className="text-sm font-semibold text-ink-primary">{title}</div>
      {description && <div className="text-xs text-ink-muted mt-1 max-w-sm">{description}</div>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
