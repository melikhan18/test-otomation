import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import {
  ArrowLeft, ArrowLeftRight, ArrowRight, Ban, Camera, CheckSquare, CircleDot, CircleSlash,
  Clock, Code2, Equal, Eraser, Eye, EyeOff, GitBranch, Globe, Hand, Hash, Heading1, Hourglass, Keyboard,
  KeyRound, Link, ListChecks, Loader, MessageSquare, MousePointer2, MousePointerClick,
  Regex, RotateCw, Search, Square, Target, TextSearch, ToggleRight, X,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/cn";

/* ─────────────────────────  Icon registry  ───────────────────────────
 * Mapping from string name (which the lib stores) to the actual lucide
 * component. Keeping it whitelisted means lib/automation.ts stays pure
 * data and only the icons we actually use get bundled.
 */
const ICONS: Record<string, LucideIcon> = {
  ArrowLeft, ArrowLeftRight, ArrowRight, Ban, Camera, CheckSquare, CircleDot, CircleSlash,
  Clock, Code2, Equal, Eraser, Eye, EyeOff, GitBranch, Globe, Hand, Hash, Heading1, Hourglass, Keyboard,
  KeyRound, Link, ListChecks, Loader, MessageSquare, MousePointer2, MousePointerClick,
  Regex, RotateCw, Square, Target, TextSearch, ToggleRight,
};

export function iconFor(name: string | undefined): LucideIcon {
  return (name && ICONS[name]) || MousePointerClick;
}

/* ─────────────────────────  Generic option model  ─────────────────────
 * Both Android and Web step actions feed into this shape. The picker
 * doesn't care which platform produced it.
 */
export type ActionPickerOption = {
  key: string;
  label: string;
  category: string;
  /** Short tooltip shown on hover. */
  description: string;
  /** Icon string — resolved through {@link iconFor}. */
  iconName: string;
  /** Color tint for the trigger button + chip. */
  tone?: "blue" | "green" | "amber" | "violet" | "gray";
};

const TONE_RING: Record<NonNullable<ActionPickerOption["tone"]>, string> = {
  blue:   "border-blue-500/40 bg-blue-500/10 text-blue-300",
  green:  "border-success-500/40 bg-success-500/10 text-success-500",
  amber:  "border-warning-500/40 bg-warning-500/10 text-warning-500",
  violet: "border-brand-500/40 bg-brand-500/10 text-brand-300",
  gray:   "border-surface-border bg-surface-muted text-ink-secondary",
};

type Props = {
  /** Currently picked action key, or null when nothing's picked yet. */
  value: string | null;
  onChange: (key: string) => void;
  /** Full action catalog. Order is preserved inside each category. */
  options: ActionPickerOption[];
  /** Subset of `options.key`s to surface as one-click pills above the
   *  popover. Order is respected. Leave empty for a popover-only picker. */
  quickPickKeys?: string[];
  /** Override the popover trigger label. Defaults to "More actions". */
  moreLabel?: string;
};

/**
 * Action picker shared by Android + Web step editors.
 *
 * Layout:
 *  - A row of one-click "quick pick" pills for the most-common actions,
 *    each with its lucide icon. Title attribute carries the description.
 *  - A trigger button ("More actions ▾") that opens a search popover
 *    listing every action grouped by category. Search is fuzzy across
 *    label + category. Keyboard nav: ↑/↓ to move, Enter to pick, Esc
 *    to close. The pill bordering inherits the tone of the picked
 *    action so the editor lights up correctly.
 */
export default function ActionPicker({ value, onChange, options, quickPickKeys, moreLabel }: Props) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [activeIdx, setActiveIdx] = useState(0);
  const triggerRef = useRef<HTMLButtonElement | null>(null);
  const popoverRef = useRef<HTMLDivElement | null>(null);
  const searchRef  = useRef<HTMLInputElement | null>(null);

  const byKey = useMemo(() => {
    const m: Record<string, ActionPickerOption> = {};
    for (const o of options) m[o.key] = o;
    return m;
  }, [options]);

  const quickPicks: ActionPickerOption[] = useMemo(
    () => (quickPickKeys ?? []).map((k) => byKey[k]).filter(Boolean),
    [quickPickKeys, byKey],
  );

  // Filtered + grouped view used inside the popover. Search is case-
  // insensitive over label and category, plus a contains-match on the
  // key (helpful for power users who remember names like FILL or GOTO).
  const groups = useMemo(() => {
    const q = query.trim().toLowerCase();
    const filtered = !q ? options : options.filter((o) =>
      o.label.toLowerCase().includes(q) ||
      o.category.toLowerCase().includes(q) ||
      o.key.toLowerCase().includes(q),
    );
    const byCat = new Map<string, ActionPickerOption[]>();
    for (const o of filtered) {
      const arr = byCat.get(o.category) ?? [];
      arr.push(o);
      byCat.set(o.category, arr);
    }
    return Array.from(byCat.entries()).map(([category, items]) => ({ category, items }));
  }, [options, query]);

  // Flat list mirrors `groups` so arrow-key nav doesn't have to think
  // about which category a row belongs to.
  const flat = useMemo(() => groups.flatMap((g) => g.items), [groups]);

  useEffect(() => { if (open) setActiveIdx(0); }, [open, query]);

  // Focus the search box when the popover opens.
  useLayoutEffect(() => {
    if (open) requestAnimationFrame(() => searchRef.current?.focus());
  }, [open]);

  // Click-outside + Escape — closes the popover and returns focus to the trigger.
  useEffect(() => {
    if (!open) return;
    function onPointerDown(e: PointerEvent) {
      const t = e.target as Node;
      if (popoverRef.current?.contains(t) || triggerRef.current?.contains(t)) return;
      setOpen(false);
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") { setOpen(false); triggerRef.current?.focus(); }
    }
    window.addEventListener("pointerdown", onPointerDown);
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("pointerdown", onPointerDown);
      window.removeEventListener("keydown", onKey);
    };
  }, [open]);

  function pick(key: string) {
    onChange(key);
    setOpen(false);
    setQuery("");
  }

  function onSearchKey(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActiveIdx((i) => Math.min(i + 1, Math.max(0, flat.length - 1)));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActiveIdx((i) => Math.max(i - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      const hit = flat[activeIdx];
      if (hit) pick(hit.key);
    }
  }

  const picked = value ? byKey[value] : null;
  const PickedIcon = iconFor(picked?.iconName);

  return (
    <div className="space-y-2">
      {/* ── Quick-pick row + More trigger ─────────────────────────── */}
      <div className="flex flex-wrap items-center gap-1.5">
        {quickPicks.map((a) => {
          const Icon = iconFor(a.iconName);
          const active = a.key === value;
          return (
            <button
              key={a.key}
              type="button"
              onClick={() => onChange(a.key)}
              title={a.description}
              className={cn(
                "inline-flex items-center gap-1.5 px-2.5 h-7 rounded-md text-[11px] font-medium border transition-colors",
                active
                  ? "bg-brand-500/15 border-brand-500/40 text-brand-300"
                  : "border-surface-border bg-surface text-ink-secondary hover:text-ink-primary hover:border-brand-500/30",
              )}
            >
              <Icon size={12} className={active ? "text-brand-300" : "text-ink-muted"} />
              {a.label}
            </button>
          );
        })}

        {/* "More actions" trigger — also acts as the visual confirmation of
            the current pick when it isn't in the quick-pick row. */}
        <div className="relative">
          <button
            ref={triggerRef}
            type="button"
            onClick={() => setOpen((o) => !o)}
            aria-expanded={open}
            className={cn(
              "inline-flex items-center gap-1.5 px-2.5 h-7 rounded-md text-[11px] font-medium border transition-colors",
              picked && !quickPicks.some((q) => q.key === picked.key)
                ? cn("bg-brand-500/15", TONE_RING[picked.tone ?? "gray"], "text-brand-300 border-brand-500/40")
                : "border-dashed border-surface-border text-ink-secondary hover:text-ink-primary hover:border-brand-500/30",
            )}
          >
            <PickedIcon size={12} className={picked ? "" : "text-ink-muted"} />
            <span>
              {picked && !quickPicks.some((q) => q.key === picked.key)
                ? picked.label
                : (moreLabel ?? "More actions")}
            </span>
            <span aria-hidden className="text-[9px] text-ink-muted">▾</span>
          </button>

          {open && (
            <div
              ref={popoverRef}
              className="absolute left-0 top-full mt-1 z-30 w-80 max-w-[calc(100vw-2rem)] rounded-md border border-surface-border bg-surface-raised shadow-lg overflow-hidden animate-fade-in"
              role="dialog"
              aria-label="Pick an action"
            >
              <div className="p-2 border-b border-surface-border flex items-center gap-2">
                <Search size={12} className="text-ink-muted shrink-0" />
                <input
                  ref={searchRef}
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  onKeyDown={onSearchKey}
                  placeholder="Search actions…"
                  className="flex-1 bg-transparent text-xs outline-none placeholder:text-ink-muted"
                />
                {query && (
                  <button
                    type="button"
                    onClick={() => setQuery("")}
                    className="p-0.5 rounded text-ink-muted hover:text-ink-primary"
                    title="Clear search"
                  >
                    <X size={11} />
                  </button>
                )}
              </div>

              <div className="max-h-80 overflow-y-auto py-1">
                {flat.length === 0 ? (
                  <div className="px-3 py-4 text-xs text-ink-muted text-center">
                    No actions match <span className="font-mono">"{query}"</span>.
                  </div>
                ) : (
                  groups.map((g) => (
                    <div key={g.category} className="pb-1">
                      <div className="px-3 pt-1.5 pb-0.5 text-[9px] uppercase tracking-wider font-semibold text-ink-muted">
                        {g.category}
                      </div>
                      {g.items.map((a) => {
                        const Icon = iconFor(a.iconName);
                        const flatIdx = flat.findIndex((f) => f.key === a.key);
                        const active = flatIdx === activeIdx;
                        const selected = a.key === value;
                        return (
                          <button
                            key={a.key}
                            type="button"
                            onClick={() => pick(a.key)}
                            onMouseEnter={() => setActiveIdx(flatIdx)}
                            title={a.description}
                            className={cn(
                              "w-full px-3 py-1.5 flex items-center gap-2 text-[11px] text-left transition-colors",
                              active ? "bg-surface-muted" : "hover:bg-surface-muted/60",
                              selected ? "text-brand-300" : "text-ink-primary",
                            )}
                          >
                            <Icon size={13} className={cn("shrink-0", selected ? "text-brand-300" : "text-ink-muted")} />
                            <span className="flex-1 truncate">{a.label}</span>
                            {selected && (
                              <span className="text-[9px] uppercase tracking-wider text-brand-300">picked</span>
                            )}
                          </button>
                        );
                      })}
                    </div>
                  ))
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
