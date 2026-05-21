import { cn } from "@/lib/cn";

/**
 * Shimmer placeholder for content that's loading. Renders a neutral pill at
 * the size you pass via Tailwind utilities (h-4 w-32 etc.).
 *
 * Reads from --surface-muted so it stays visible across light + dark themes
 * without explicit per-theme overrides.
 *
 * Composition example — settings page header skeleton:
 *
 * <div className="space-y-2">
 *   <Skeleton className="h-4 w-32" />
 *   <Skeleton className="h-3 w-48" />
 * </div>
 */
export function Skeleton({ className }: { className?: string }) {
  return <div className={cn("skeleton motion-reduce:animate-none", className)} />;
}

/**
 * Five-line text block. Useful for empty card bodies while data fetches —
 * gives a more "structural" feel than a single bar.
 */
export function SkeletonText({ lines = 3, className }: { lines?: number; className?: string }) {
  return (
    <div className={cn("space-y-2", className)}>
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton
          key={i}
          className={cn(
            "h-3",
            i === lines - 1 ? "w-2/3" : "w-full",
          )}
        />
      ))}
    </div>
  );
}

/**
 * Header row pattern (title + subtitle) — shows up across nearly every
 * settings page while data resolves. Single line, two-tone width.
 */
export function SkeletonHeader({ className }: { className?: string }) {
  return (
    <div className={cn("space-y-2", className)}>
      <Skeleton className="h-5 w-40" />
      <Skeleton className="h-3 w-64" />
    </div>
  );
}

/**
 * Multi-row placeholder for data tables. Renders inside the same bordered
 * shell as the real table so the layout doesn't shift when content arrives.
 */
export function SkeletonTable({
  rows = 5, className,
}: { rows?: number; className?: string }) {
  return (
    <div className={cn("rounded-md border border-surface-border bg-surface overflow-hidden", className)}>
      <div className="divide-y divide-surface-border">
        {Array.from({ length: rows }).map((_, i) => (
          <div key={i} className="px-4 py-3 flex items-center gap-3">
            <Skeleton className="h-8 w-8 rounded-md" />
            <div className="flex-1 space-y-1.5">
              <Skeleton className="h-3.5 w-1/3" />
              <Skeleton className="h-2.5 w-1/4" />
            </div>
            <Skeleton className="h-5 w-16" />
            <Skeleton className="h-5 w-12" />
          </div>
        ))}
      </div>
    </div>
  );
}
