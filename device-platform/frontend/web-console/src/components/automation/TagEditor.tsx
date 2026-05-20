import { useEffect, useMemo, useRef, useState } from "react";
import { Plus, Tag as TagIcon, X } from "lucide-react";
import { cn } from "@/lib/cn";

type Props = {
  tags: string[];
  /** All known tags in the workspace — used for inline autocomplete. */
  suggestions?: string[];
  /** Async commit. Editor stays responsive while it runs. */
  onChange: (next: string[]) => Promise<void> | void;
  /** Read-only mode shows chips without the add/remove affordances. */
  readOnly?: boolean;
  /** Tighter visual treatment for list rows. */
  size?: "sm" | "md";
};

const MAX_TAG_LEN = 32;
const MAX_TAGS    = 16;

/**
 * Click "+" → input slides in. Enter commits; comma also splits multi-tag pastes.
 * Backspace on an empty input removes the trailing tag. Suggestions are filtered
 * live but never enforced — typing a brand-new label is always allowed.
 */
export default function TagEditor({ tags, suggestions = [], onChange, readOnly, size = "md" }: Props) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState("");
  const [pending, setPending] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => { if (editing) inputRef.current?.focus(); }, [editing]);

  const normalised = useMemo(() =>
    Array.from(new Set(tags.map((t) => t.trim().toLowerCase()).filter(Boolean))),
    [tags],
  );

  const filteredSuggestions = useMemo(() => {
    const q = draft.trim().toLowerCase();
    return suggestions
      .filter((s) => !normalised.includes(s))
      .filter((s) => q === "" || s.includes(q))
      .slice(0, 6);
  }, [draft, suggestions, normalised]);

  async function commit(next: string[]) {
    setPending(true);
    try { await onChange(next); }
    finally { setPending(false); }
  }

  function addLabels(raw: string) {
    if (!raw.trim()) return;
    const fresh = raw
      .split(",")
      .map((p) => p.trim().toLowerCase().replace(/\s+/g, "-"))
      .filter(Boolean)
      .map((p) => p.slice(0, MAX_TAG_LEN));
    const merged = [...normalised];
    for (const t of fresh) {
      if (!merged.includes(t) && merged.length < MAX_TAGS) merged.push(t);
    }
    if (merged.length === normalised.length) { setDraft(""); return; }
    setDraft("");
    void commit(merged);
  }

  function removeTag(t: string) {
    void commit(normalised.filter((x) => x !== t));
  }

  function stopEdit() {
    setEditing(false);
    setDraft("");
  }

  // Empty + read-only renders nothing rather than an awkward orphan icon.
  if (readOnly && normalised.length === 0) return null;

  const chipBase = size === "sm"
    ? "h-5 px-1.5 text-[10px]"
    : "h-6 px-2 text-[11px]";

  return (
    <div className="inline-flex items-center gap-1.5 flex-wrap min-w-0">
      {normalised.map((t) => (
        <span
          key={t}
          className={cn(
            "inline-flex items-center gap-1 rounded-full border bg-surface-muted/70 text-ink-secondary font-medium font-mono",
            chipBase,
          )}
        >
          <TagIcon size={size === "sm" ? 9 : 10} className="text-ink-muted" />
          {t}
          {!readOnly && (
            <button
              onClick={(e) => { e.stopPropagation(); e.preventDefault(); removeTag(t); }}
              disabled={pending}
              className="-mr-0.5 ml-0.5 rounded-full hover:bg-surface text-ink-muted hover:text-danger-500 disabled:opacity-50"
              title="Remove tag"
            >
              <X size={size === "sm" ? 9 : 10} />
            </button>
          )}
        </span>
      ))}

      {!readOnly && (editing ? (
        <span className="relative">
          <input
            ref={inputRef}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            disabled={pending || normalised.length >= MAX_TAGS}
            onKeyDown={(e) => {
              if (e.key === "Enter")        { e.preventDefault(); addLabels(draft); }
              else if (e.key === ",")       { e.preventDefault(); addLabels(draft); }
              else if (e.key === "Escape")  { e.preventDefault(); stopEdit(); }
              else if (e.key === "Backspace" && draft === "" && normalised.length > 0) {
                e.preventDefault();
                removeTag(normalised[normalised.length - 1]);
              }
            }}
            onBlur={() => {
              // Delay so a click on a suggestion still registers.
              setTimeout(() => { if (!inputRef.current) return; addLabels(draft); stopEdit(); }, 120);
            }}
            placeholder={normalised.length >= MAX_TAGS ? "max reached" : "add tag…"}
            className={cn(
              "rounded-md border border-brand-500/40 bg-surface text-xs font-mono",
              "focus:outline-none focus:ring-1 focus:ring-brand-500",
              size === "sm" ? "h-5 px-1.5 text-[10px] w-24" : "h-6 px-2 w-32",
            )}
          />
          {filteredSuggestions.length > 0 && (
            <div className="absolute left-0 top-full mt-1 z-20 min-w-[10rem] rounded-md border border-surface-border bg-surface-raised shadow-lg p-1 space-y-0.5">
              {filteredSuggestions.map((s) => (
                <button
                  key={s}
                  onMouseDown={(e) => { e.preventDefault(); addLabels(s); }}
                  className="block w-full text-left px-2 py-1 text-xs rounded hover:bg-surface-muted text-ink-secondary hover:text-ink-primary"
                >
                  {s}
                </button>
              ))}
            </div>
          )}
        </span>
      ) : (
        <button
          onClick={(e) => { e.stopPropagation(); e.preventDefault(); setEditing(true); }}
          disabled={pending || normalised.length >= MAX_TAGS}
          className={cn(
            "inline-flex items-center gap-1 rounded-full border border-dashed border-surface-border",
            "text-ink-muted hover:text-ink-primary hover:border-brand-500/40 disabled:opacity-50",
            chipBase,
          )}
          title="Add tag"
        >
          <Plus size={size === "sm" ? 9 : 10} />
          {normalised.length === 0 ? "tag" : ""}
        </button>
      ))}
    </div>
  );
}
