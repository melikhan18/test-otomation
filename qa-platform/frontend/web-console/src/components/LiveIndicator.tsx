import { cn } from "@/lib/cn";

/**
 * Visual indicator for a row that's actively progressing (RUNNING / QUEUED).
 * Three variants — we'll keep one once the design lands.
 *
 *   - "dot"    : 8px circle with a slow pulse + halo ring. Calm, classic.
 *   - "bars"   : 3 vertical bars rising and falling — audio-wave / equalizer.
 *   - "stripe" : 2px left edge stripe with a scrolling gradient. Trello-row vibe.
 */
type Variant = "dot" | "bars" | "stripe";
type Tone = "running" | "queued";

const toneColor: Record<Tone, string> = {
  running: "bg-brand-500",
  queued:  "bg-warning-500",
};
const toneRing: Record<Tone, string> = {
  running: "ring-brand-500/40",
  queued:  "ring-warning-500/40",
};
const toneText: Record<Tone, string> = {
  running: "text-brand-500",
  queued:  "text-warning-500",
};

export function LiveIndicator({
  variant, tone = "running", label, className,
}: { variant: Variant; tone?: Tone; label?: string; className?: string }) {
  if (variant === "dot") {
    return (
      <span className={cn("inline-flex items-center gap-2", className)}>
        <span className="relative inline-flex h-2 w-2">
          <span className={cn("absolute inset-0 rounded-full animate-ping opacity-60", toneColor[tone])} />
          <span className={cn("relative inline-flex h-2 w-2 rounded-full ring-2 ring-offset-0 animate-pulse-soft", toneColor[tone], toneRing[tone])} />
        </span>
        {label && <span className={cn("text-[10px] uppercase tracking-wider font-semibold", toneText[tone])}>{label}</span>}
      </span>
    );
  }

  if (variant === "bars") {
    return (
      <span className={cn("inline-flex items-center gap-2", className)}>
        <span className="inline-flex items-end gap-[2px] h-3.5">
          <span className={cn("w-[3px] rounded-full origin-bottom animate-wave-bar-1", toneColor[tone])} style={{ height: "100%" }} />
          <span className={cn("w-[3px] rounded-full origin-bottom animate-wave-bar-2", toneColor[tone])} style={{ height: "100%" }} />
          <span className={cn("w-[3px] rounded-full origin-bottom animate-wave-bar-3", toneColor[tone])} style={{ height: "100%" }} />
        </span>
        {label && <span className={cn("text-[10px] uppercase tracking-wider font-semibold", toneText[tone])}>{label}</span>}
      </span>
    );
  }

  // "stripe" — left edge band. Use as an absolute child of a relative row.
  return (
    <span
      className={cn(
        "absolute left-0 top-0 bottom-0 w-[3px] rounded-l-md animate-stripe-shift",
        className,
      )}
      style={{
        backgroundImage:
          tone === "running"
            ? "linear-gradient(180deg, rgba(59,110,240,0) 0%, rgba(59,110,240,1) 35%, rgba(91,141,247,1) 50%, rgba(59,110,240,1) 65%, rgba(59,110,240,0) 100%)"
            : "linear-gradient(180deg, rgba(245,158,11,0) 0%, rgba(245,158,11,1) 35%, rgba(252,191,73,1) 50%, rgba(245,158,11,1) 65%, rgba(245,158,11,0) 100%)",
        backgroundSize: "100% 200%",
      }}
      aria-label={label}
    />
  );
}
