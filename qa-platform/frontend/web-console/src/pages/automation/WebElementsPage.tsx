import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, Target, Trash2, X } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { cn } from "@/lib/cn";
import {
  webElementApi, type WebElementCreate, type WebElementUpdate,
  type WebElementView, type WebLocatorStrategy,
} from "@/lib/webAutomation";

const STRATEGIES: WebLocatorStrategy[] = ["CSS", "XPATH", "ROLE", "TEXT", "TEST_ID"];

const STRATEGY_TONE: Record<WebLocatorStrategy, string> = {
  CSS:     "border-brand-500/40    bg-brand-500/10    text-brand-300",
  XPATH:   "border-warning-500/40  bg-warning-500/10  text-warning-500",
  ROLE:    "border-success-500/40  bg-success-500/10  text-success-500",
  TEXT:    "border-danger-500/30   bg-danger-500/10   text-danger-500",
  TEST_ID: "border-surface-border  bg-surface-muted   text-ink-secondary",
};

/**
 * Web platform's element catalog page — the analog of Android's
 * {@code ElementsPage}. Same column layout (list on the left, editor on
 * the right when active) and same action affordances for consistent UX.
 */
export default function WebElementsPage() {
  const qc = useQueryClient();
  const listQ = useQuery({ queryKey: ["web-elements"], queryFn: webElementApi.list });

  const [editing, setEditing] = useState<WebElementView | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: (b: WebElementCreate) => webElementApi.create(b),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["web-elements"] }); setCreating(false); setError(null); },
    onError: (e: any) => setError(e?.response?.data?.detail ?? "create failed"),
  });
  const update = useMutation({
    mutationFn: ({ id, body }: { id: number; body: WebElementUpdate }) => webElementApi.update(id, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["web-elements"] }); setEditing(null); setError(null); },
    onError: (e: any) => setError(e?.response?.data?.detail ?? "update failed"),
  });
  const remove = useMutation({
    mutationFn: (id: number) => webElementApi.delete(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["web-elements"] }); },
  });

  return (
    <div className="p-4 lg:p-6 max-w-7xl mx-auto space-y-4">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold flex items-center gap-2">
            <Target size={20} className="text-brand-400" />
            Elements
          </h1>
          <p className="text-xs text-ink-muted mt-1">
            Saved Playwright locators. Reuse across scenarios; one edit, every step updated.
          </p>
        </div>
        <Button variant="primary" leftIcon={<Plus size={12} />} onClick={() => { setCreating(true); setEditing(null); setError(null); }}>
          New element
        </Button>
      </header>

      <Card>
        {listQ.isLoading && <div className="p-6 text-xs text-ink-muted flex items-center gap-2"><Spinner /> Loading…</div>}
        {listQ.data?.length === 0 && (
          <EmptyState
            title="No elements yet"
            description="Save your first Playwright selector to reuse it across scenarios."
            action={<Button variant="primary" leftIcon={<Plus size={12} />} onClick={() => setCreating(true)}>New element</Button>}
          />
        )}
        {(listQ.data?.length ?? 0) > 0 && (
          <ul className="divide-y divide-surface-border">
            {listQ.data!.map((el) => (
              <li key={el.id} className="px-4 py-3 hover:bg-surface-muted/40 flex items-start gap-3">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium">{el.name}</span>
                    <span className={cn("inline-flex items-center text-[10px] font-mono uppercase tracking-wider rounded px-1.5 py-0.5 border",
                      STRATEGY_TONE[el.primaryStrategy])}>
                      {el.primaryStrategy}
                    </span>
                    {el.fallbackLocators.length > 0 && (
                      <span className="text-[10px] text-ink-muted">+{el.fallbackLocators.length} fallback</span>
                    )}
                  </div>
                  <div className="text-[11px] text-ink-secondary mt-0.5 font-mono break-all">{el.primaryValue}</div>
                  {el.description && <div className="text-[11px] text-ink-muted mt-0.5">{el.description}</div>}
                </div>
                <div className="flex items-center gap-1">
                  <button onClick={() => { setEditing(el); setCreating(false); setError(null); }}
                          className="text-ink-muted hover:text-brand-300 p-1.5 rounded hover:bg-surface-muted"
                          title="Edit"><Pencil size={12} /></button>
                  <button onClick={() => { if (confirm(`Delete "${el.name}"?`)) remove.mutate(el.id); }}
                          className="text-ink-muted hover:text-danger-500 p-1.5 rounded hover:bg-danger-500/10"
                          title="Delete"><Trash2 size={12} /></button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {(creating || editing) && (
        <ElementEditor
          mode={creating ? "create" : "edit"}
          initial={editing ?? undefined}
          error={error}
          busy={create.isPending || update.isPending}
          onClose={() => { setCreating(false); setEditing(null); setError(null); }}
          onSubmit={(payload) => {
            if (creating) create.mutate(payload as WebElementCreate);
            else if (editing) update.mutate({ id: editing.id, body: payload as WebElementUpdate });
          }}
        />
      )}
    </div>
  );
}

/* ────────────────────────────  Editor dialog  ────────────────────────── */

function ElementEditor({ mode, initial, error, busy, onClose, onSubmit }: {
  mode: "create" | "edit";
  initial?: WebElementView;
  error: string | null;
  busy: boolean;
  onClose: () => void;
  onSubmit: (payload: WebElementCreate | WebElementUpdate) => void;
}) {
  const [name, setName] = useState(initial?.name ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [primaryStrategy, setPrimaryStrategy] = useState<WebLocatorStrategy>(initial?.primaryStrategy ?? "CSS");
  const [primaryValue, setPrimaryValue] = useState(initial?.primaryValue ?? "");
  const [fallbacks, setFallbacks] = useState(initial?.fallbackLocators ?? []);

  function submit() {
    const trimmed = name.trim();
    if (!trimmed || !primaryValue.trim()) return;
    onSubmit({
      name: trimmed,
      description: description.trim() || null,
      primaryStrategy,
      primaryValue: primaryValue.trim(),
      fallbackLocators: fallbacks.filter((f) => f.value.trim()),
    });
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-2xl flex flex-col max-h-[90vh]">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div className="text-sm font-semibold">{mode === "create" ? "New element" : "Edit element"}</div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted"><X size={14} /></button>
        </div>

        <div className="p-5 space-y-3 overflow-auto">
          <div>
            <label className="label block mb-1">Name</label>
            <input value={name} onChange={(e) => setName(e.target.value)}
                   placeholder="login-button"
                   className="w-full h-8 px-2.5 rounded-md border border-surface-border bg-surface text-xs font-mono focus:outline-none focus:ring-1 focus:ring-brand-500" />
            <div className="text-[10px] text-ink-muted mt-1">Lowercase kebab-case (a-z, 0-9, -). Must be unique within project.</div>
          </div>

          <div>
            <label className="label block mb-1">Description</label>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)}
                      rows={2}
                      className="w-full px-2.5 py-1.5 rounded-md border border-surface-border bg-surface text-xs focus:outline-none focus:ring-1 focus:ring-brand-500" />
          </div>

          <div>
            <label className="label block mb-1">Primary strategy</label>
            <div className="flex flex-wrap gap-1.5">
              {STRATEGIES.map((s) => (
                <button key={s} onClick={() => setPrimaryStrategy(s)}
                        className={cn("px-2.5 h-7 rounded-md text-[11px] font-medium border transition-colors",
                          primaryStrategy === s
                            ? "bg-brand-500/15 border-brand-500/40 text-brand-300"
                            : "border-surface-border text-ink-secondary hover:text-ink-primary")}>
                  {s}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="label block mb-1">Selector value</label>
            <input value={primaryValue} onChange={(e) => setPrimaryValue(e.target.value)}
                   placeholder='button[data-cy="login"] | //button[@aria-label="Login"] | role=button[name="Login"]'
                   className="w-full h-8 px-2.5 rounded-md border border-surface-border bg-surface text-xs font-mono focus:outline-none focus:ring-1 focus:ring-brand-500" />
          </div>

          <div>
            <label className="label block mb-1">Fallback locators (optional)</label>
            <div className="space-y-1.5">
              {fallbacks.map((f, i) => (
                <div key={i} className="flex gap-1.5">
                  <select value={f.strategy}
                          onChange={(e) => setFallbacks((arr) => arr.map((x, j) => j === i ? { ...x, strategy: e.target.value as WebLocatorStrategy } : x))}
                          className="h-7 px-2 rounded-md border border-surface-border bg-surface text-[11px] focus:outline-none focus:ring-1 focus:ring-brand-500">
                    {STRATEGIES.map((s) => <option key={s} value={s}>{s}</option>)}
                  </select>
                  <input value={f.value}
                         onChange={(e) => setFallbacks((arr) => arr.map((x, j) => j === i ? { ...x, value: e.target.value } : x))}
                         placeholder="alternate selector"
                         className="flex-1 h-7 px-2 rounded-md border border-surface-border bg-surface text-[11px] font-mono focus:outline-none focus:ring-1 focus:ring-brand-500" />
                  <button onClick={() => setFallbacks((arr) => arr.filter((_, j) => j !== i))}
                          className="text-ink-muted hover:text-danger-500 p-1 rounded hover:bg-danger-500/10"><Trash2 size={11} /></button>
                </div>
              ))}
              <button onClick={() => setFallbacks((arr) => [...arr, { strategy: "CSS", value: "" }])}
                      className="text-[10px] text-brand-400 hover:text-brand-300 flex items-center gap-1">
                <Plus size={10} /> Add fallback
              </button>
            </div>
          </div>

          {error && (
            <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">
              {error}
            </div>
          )}
        </div>

        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={busy} disabled={!name.trim() || !primaryValue.trim()} onClick={submit}>
            {mode === "create" ? "Create" : "Save changes"}
          </Button>
        </div>
      </Card>
    </div>
  );
}
