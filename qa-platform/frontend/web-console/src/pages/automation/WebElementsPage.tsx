import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ImageIcon, Pencil, Plus, Search, Trash2, X } from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { cn } from "@/lib/cn";
import {
  webElementApi, type WebElementCreate, type WebElementUpdate,
  type WebElementView, type WebLocator, type WebLocatorStrategy,
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
 * Web platform's element catalog — mirrors Android's
 * {@code ElementsPage} layout: TopBar with crumbs + action button,
 * stats card row, search card, grid of element cards. Differences:
 * no screenshot thumbnails (web elements don't capture one yet),
 * five Playwright strategies instead of Android's five Appium strategies.
 */
export default function WebElementsPage() {
  const qc = useQueryClient();
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<WebElementView | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<WebElementView | null>(null);

  const elementsQ = useQuery({ queryKey: ["web-elements"], queryFn: webElementApi.list });

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
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["web-elements"] }); setConfirmDelete(null); },
  });

  const filtered = useMemo(() => {
    const all = elementsQ.data ?? [];
    if (!search) return all;
    const q = search.toLowerCase();
    return all.filter((el) =>
      el.name.toLowerCase().includes(q) ||
      el.primaryValue.toLowerCase().includes(q) ||
      (el.description ?? "").toLowerCase().includes(q),
    );
  }, [elementsQ.data, search]);

  const stats = useMemo(() => {
    const all = elementsQ.data ?? [];
    const byStrategy = all.reduce<Record<string, number>>((acc, e) => {
      acc[e.primaryStrategy] = (acc[e.primaryStrategy] ?? 0) + 1; return acc;
    }, {});
    const withFallbacks = all.filter((e) => e.fallbackLocators.length > 0).length;
    return { total: all.length, withFallbacks, byStrategy };
  }, [elementsQ.data]);

  return (
    <>
      <TopBar
        crumbs={[{ label: "Automation", to: "/automation/web" }, { label: "Elements" }]}
        actions={
          <Button variant="primary" size="sm" leftIcon={<Plus size={14} />} onClick={() => { setCreating(true); setError(null); }}>
            New element
          </Button>
        }
      />

      <div className="px-6 py-6 space-y-6">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Stat label="Elements"      value={stats.total} />
          <Stat label="With fallback" value={stats.withFallbacks} />
          <Stat label="CSS"           value={stats.byStrategy["CSS"] ?? 0} />
          <Stat label="XPath"         value={stats.byStrategy["XPATH"] ?? 0} />
        </div>

        <Card className="px-4 py-3">
          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-muted" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by name, locator value or description…"
              className="input pl-9 pr-9"
            />
            {search && (
              <button onClick={() => setSearch("")} className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-muted hover:text-ink-primary">
                <X size={12} />
              </button>
            )}
          </div>
        </Card>

        {elementsQ.isLoading ? (
          <Card className="flex items-center justify-center h-48 text-ink-muted gap-2 text-sm">
            <Spinner /> Loading…
          </Card>
        ) : filtered.length === 0 ? (
          <Card>
            <EmptyState
              icon={<ImageIcon size={20} />}
              title={search ? "No elements match" : "No elements yet"}
              description={search
                ? "Try a different keyword."
                : "Save your first Playwright selector to reuse it across scenarios."}
              action={
                <Button variant="primary" size="sm" leftIcon={<Plus size={14} />} onClick={() => { setCreating(true); setError(null); }}>
                  Create manually
                </Button>
              }
            />
          </Card>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {filtered.map((el) => (
              <ElementCard
                key={el.id}
                el={el}
                onEdit={() => { setEditing(el); setError(null); }}
                onDelete={() => setConfirmDelete(el)}
              />
            ))}
          </div>
        )}
      </div>

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

      {confirmDelete && (
        <ConfirmDelete
          name={confirmDelete.name}
          busy={remove.isPending}
          onCancel={() => setConfirmDelete(null)}
          onConfirm={() => remove.mutate(confirmDelete.id)}
        />
      )}
    </>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <Card className="px-5 py-4">
      <div className="label">{label}</div>
      <div className="text-2xl font-semibold mt-1 text-ink-primary">{value}</div>
    </Card>
  );
}

function ElementCard({ el, onEdit, onDelete }: { el: WebElementView; onEdit: () => void; onDelete: () => void }) {
  return (
    <Card className="p-4 flex flex-col gap-3 hover:border-brand-500/40 transition-colors group">
      <div className="flex items-start gap-3">
        <div className="w-16 h-16 rounded-md border border-surface-border bg-black/30 flex items-center justify-center shrink-0">
          <ImageIcon size={18} className="text-ink-muted" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="font-mono text-sm font-medium text-ink-primary truncate" title={el.name}>{el.name}</div>
          {el.description && <div className="text-[11px] text-ink-muted truncate" title={el.description}>{el.description}</div>}
          <div className="flex items-center gap-1 mt-1 flex-wrap">
            <span className={cn("inline-flex items-center text-[10px] font-mono uppercase tracking-wider rounded px-1.5 py-0.5 border",
              STRATEGY_TONE[el.primaryStrategy])}>
              {el.primaryStrategy}
            </span>
            {el.fallbackLocators.length > 0 && (
              <span className="text-[10px] text-ink-muted">+{el.fallbackLocators.length} fallback</span>
            )}
          </div>
        </div>
      </div>

      <div className="text-[11px] font-mono text-ink-secondary truncate" title={el.primaryValue}>
        {el.primaryValue}
      </div>

      <div className="flex items-center justify-end gap-1 mt-auto opacity-0 group-hover:opacity-100 transition-opacity">
        <button onClick={onEdit} className="p-1.5 rounded text-ink-muted hover:text-ink-primary hover:bg-surface-muted" title="Edit">
          <Pencil size={13} />
        </button>
        <button onClick={onDelete} className="p-1.5 rounded text-ink-muted hover:text-danger-500 hover:bg-surface-muted" title="Delete">
          <Trash2 size={13} />
        </button>
      </div>
    </Card>
  );
}

function ConfirmDelete({ name, busy, onCancel, onConfirm }: {
  name: string; busy?: boolean; onCancel: () => void; onConfirm: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-md p-5">
        <div className="text-sm font-semibold">Delete element?</div>
        <div className="text-xs text-ink-muted mt-1">
          <code className="font-mono">{name}</code> will be removed. Test steps that reference it will fall back to their literal selector (or fail validation).
        </div>
        <div className="flex justify-end gap-2 mt-5">
          <Button variant="secondary" onClick={onCancel}>Cancel</Button>
          <Button variant="danger" loading={busy} onClick={onConfirm}>Delete</Button>
        </div>
      </Card>
    </div>
  );
}

/* ─────────────────────────────  Editor  ──────────────────────────────── */

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
  const [fallbacks, setFallbacks] = useState<WebLocator[]>(initial?.fallbackLocators ?? []);

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

        <div className="p-5 space-y-4 overflow-auto">
          <label className="block">
            <span className="label block mb-1.5">Name</span>
            <input autoFocus value={name} onChange={(e) => setName(e.target.value)}
                   placeholder="login-button" className="input font-mono" />
            <div className="text-[10px] text-ink-muted mt-1">Lowercase kebab-case (a-z, 0-9, -). Must be unique within project.</div>
          </label>

          <label className="block">
            <span className="label block mb-1.5">Description (optional)</span>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} className="input resize-y" />
          </label>

          <div>
            <span className="label block mb-1.5">Primary strategy</span>
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

          <label className="block">
            <span className="label block mb-1.5">Selector value</span>
            <input value={primaryValue} onChange={(e) => setPrimaryValue(e.target.value)}
                   placeholder='button[data-cy="login"] | //button[@aria-label="Login"] | role=button[name="Login"]'
                   className="input font-mono" />
          </label>

          <div>
            <span className="label block mb-1.5">Fallback locators (optional)</span>
            <div className="space-y-1.5">
              {fallbacks.map((f, i) => (
                <div key={i} className="flex gap-1.5">
                  <select value={f.strategy}
                          onChange={(e) => setFallbacks((arr) => arr.map((x, j) => j === i ? { ...x, strategy: e.target.value as WebLocatorStrategy } : x))}
                          className="input text-[11px] w-32">
                    {STRATEGIES.map((s) => <option key={s} value={s}>{s}</option>)}
                  </select>
                  <input value={f.value}
                         onChange={(e) => setFallbacks((arr) => arr.map((x, j) => j === i ? { ...x, value: e.target.value } : x))}
                         placeholder="alternate selector"
                         className="input text-[11px] font-mono flex-1" />
                  <button onClick={() => setFallbacks((arr) => arr.filter((_, j) => j !== i))}
                          className="text-ink-muted hover:text-danger-500 p-1 rounded hover:bg-danger-500/10"><Trash2 size={11} /></button>
                </div>
              ))}
              <button onClick={() => setFallbacks((arr) => [...arr, { strategy: "CSS", value: "" }])}
                      className="text-[11px] text-brand-400 hover:text-brand-300 flex items-center gap-1">
                <Plus size={11} /> Add fallback
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
