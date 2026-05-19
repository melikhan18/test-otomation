import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, Search, X } from "lucide-react";
import { cn } from "@/lib/cn";

export type ComboOption = {
  value: string;
  label: string;
  /** Optional secondary line under label. */
  hint?: string;
  /** Optional left-side thumbnail (data URL or img src). */
  thumbnail?: string | null;
  /** Optional right-side badge (e.g. environment, locator strategy). */
  badge?: string;
};

type Props = {
  options: ComboOption[];
  value: string | null;
  onChange: (value: string | null) => void;
  placeholder?: string;
  /** Renders a clear (X) button when a value is selected. */
  clearable?: boolean;
  disabled?: boolean;
  className?: string;
  emptyText?: string;
};

/**
 * Searchable single-select combobox. Designed for picking elements + test-data inside
 * the step editor: shows a thumbnail and a secondary hint line per row.
 *
 * Headless — no portal, opens downward (or upward if no room).
 */
export default function Combobox({
  options, value, onChange, placeholder = "Select…", clearable = true,
  disabled, className, emptyText = "No matches",
}: Props) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const rootRef = useRef<HTMLDivElement>(null);

  const selected = useMemo(() => options.find((o) => o.value === value) ?? null, [options, value]);

  useEffect(() => {
    function onDocClick(e: MouseEvent) {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    }
    function onKey(e: KeyboardEvent) { if (e.key === "Escape") setOpen(false); }
    document.addEventListener("mousedown", onDocClick);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDocClick);
      document.removeEventListener("keydown", onKey);
    };
  }, []);

  const filtered = useMemo(() => {
    const q = query.toLowerCase().trim();
    if (!q) return options;
    return options.filter((o) =>
      o.label.toLowerCase().includes(q) ||
      (o.hint ?? "").toLowerCase().includes(q) ||
      (o.badge ?? "").toLowerCase().includes(q),
    );
  }, [options, query]);

  return (
    <div ref={rootRef} className={cn("relative", className)}>
      <button
        type="button"
        disabled={disabled}
        onClick={() => setOpen((v) => !v)}
        className={cn(
          "w-full flex items-center gap-2 h-9 px-2.5 rounded-md border bg-surface text-sm transition-colors",
          "disabled:opacity-50 disabled:cursor-not-allowed",
          open ? "border-brand-500/40 ring-2 ring-brand-500/30" : "border-surface-border hover:border-surface-border",
        )}
      >
        {selected?.thumbnail && (
          <img src={selected.thumbnail} alt="" className="w-6 h-6 rounded object-contain bg-black/30 shrink-0" />
        )}
        <div className="flex-1 min-w-0 text-left">
          {selected ? (
            <div className="truncate">
              <div className="text-ink-primary font-mono text-xs truncate">{selected.label}</div>
              {selected.hint && <div className="text-[10px] text-ink-muted truncate">{selected.hint}</div>}
            </div>
          ) : (
            <span className="text-ink-muted text-xs">{placeholder}</span>
          )}
        </div>
        {selected && clearable && (
          <span
            onClick={(e) => { e.stopPropagation(); onChange(null); }}
            className="text-ink-muted hover:text-ink-primary p-0.5 rounded hover:bg-surface-muted shrink-0 cursor-pointer"
            role="button"
            aria-label="Clear"
          >
            <X size={12} />
          </span>
        )}
        <ChevronDown size={12} className={cn("text-ink-muted shrink-0 transition-transform", open && "rotate-180")} />
      </button>

      {open && (
        <div className="absolute z-50 mt-1 left-0 right-0 rounded-md border border-surface-border bg-surface-raised shadow-card overflow-hidden">
          <div className="relative border-b border-surface-border">
            <Search size={12} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-ink-muted" />
            <input
              autoFocus
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Filter…"
              className="w-full pl-7 pr-2 h-8 bg-transparent text-xs outline-none"
            />
          </div>
          <div className="max-h-64 overflow-auto">
            {filtered.length === 0 ? (
              <div className="px-3 py-4 text-center text-xs text-ink-muted">{emptyText}</div>
            ) : filtered.map((o) => (
              <button
                key={o.value}
                onClick={() => { onChange(o.value); setOpen(false); setQuery(""); }}
                className={cn(
                  "w-full flex items-center gap-2 px-2.5 py-1.5 text-left hover:bg-surface-muted",
                  value === o.value && "bg-brand-500/10",
                )}
              >
                {o.thumbnail
                  ? <img src={o.thumbnail} alt="" className="w-7 h-7 rounded object-contain bg-black/30 shrink-0" />
                  : <span className="w-7 h-7 shrink-0" />}
                <div className="flex-1 min-w-0">
                  <div className="font-mono text-xs text-ink-primary truncate">{o.label}</div>
                  {o.hint && <div className="text-[10px] text-ink-muted truncate">{o.hint}</div>}
                </div>
                {o.badge && (
                  <span className="text-[10px] font-mono uppercase tracking-wider text-ink-muted shrink-0">{o.badge}</span>
                )}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
