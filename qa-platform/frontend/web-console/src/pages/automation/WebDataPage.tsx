import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Database, Eye, EyeOff, Lock, Pencil, Plus, Trash2, X } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { cn } from "@/lib/cn";
import {
  webTestDataApi, type WebTestDataCreate, type WebTestDataUpdate, type WebTestDataView,
} from "@/lib/webAutomation";

/**
 * Web platform's test data page — analog of Android's {@code DataPage}.
 * Same row shape (name · environment · masked value · sensitive flag);
 * editor dialog mirrors Android's style.
 */
export default function WebDataPage() {
  const qc = useQueryClient();
  const listQ = useQuery({ queryKey: ["web-test-data"], queryFn: webTestDataApi.list });

  const [editing, setEditing] = useState<WebTestDataView | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [revealed, setRevealed] = useState<Set<number>>(new Set());

  const create = useMutation({
    mutationFn: (b: WebTestDataCreate) => webTestDataApi.create(b),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["web-test-data"] }); setCreating(false); setError(null); },
    onError: (e: any) => setError(e?.response?.data?.detail ?? "create failed"),
  });
  const update = useMutation({
    mutationFn: ({ id, body }: { id: number; body: WebTestDataUpdate }) => webTestDataApi.update(id, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["web-test-data"] }); setEditing(null); setError(null); },
    onError: (e: any) => setError(e?.response?.data?.detail ?? "update failed"),
  });
  const remove = useMutation({
    mutationFn: (id: number) => webTestDataApi.delete(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["web-test-data"] }); },
  });

  async function reveal(d: WebTestDataView) {
    if (revealed.has(d.id)) {
      setRevealed((s) => { const next = new Set(s); next.delete(d.id); return next; });
      return;
    }
    const full = await webTestDataApi.get(d.id, true);
    qc.setQueryData<WebTestDataView[]>(["web-test-data"], (old) =>
      (old ?? []).map((row) => row.id === d.id ? { ...row, value: full.value, masked: false } : row));
    setRevealed((s) => new Set(s).add(d.id));
  }

  return (
    <div className="p-4 lg:p-6 max-w-7xl mx-auto space-y-4">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold flex items-center gap-2">
            <Database size={20} className="text-brand-400" />
            Test data
          </h1>
          <p className="text-xs text-ink-muted mt-1">
            Per-environment fixtures. Steps reference these by name; sensitive values are masked at rest.
          </p>
        </div>
        <Button variant="primary" leftIcon={<Plus size={12} />} onClick={() => { setCreating(true); setEditing(null); setError(null); }}>
          New entry
        </Button>
      </header>

      <Card>
        {listQ.isLoading && <div className="p-6 text-xs text-ink-muted flex items-center gap-2"><Spinner /> Loading…</div>}
        {listQ.data?.length === 0 && (
          <EmptyState
            title="No test data yet"
            description="Save fixtures (usernames, URLs, expected text) so scenarios stay portable across environments."
            action={<Button variant="primary" leftIcon={<Plus size={12} />} onClick={() => setCreating(true)}>New entry</Button>}
          />
        )}
        {(listQ.data?.length ?? 0) > 0 && (
          <ul className="divide-y divide-surface-border">
            {listQ.data!.map((d) => (
              <li key={d.id} className="px-4 py-2.5 hover:bg-surface-muted/40 flex items-center gap-3">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium">{d.name}</span>
                    <span className="text-[10px] text-ink-muted">{d.environment}</span>
                    {d.sensitive && (
                      <span className="inline-flex items-center gap-0.5 text-[10px] text-warning-500">
                        <Lock size={10} /> sensitive
                      </span>
                    )}
                  </div>
                  <div className="text-[11px] text-ink-secondary mt-0.5 font-mono break-all">{d.value}</div>
                  {d.description && <div className="text-[11px] text-ink-muted mt-0.5">{d.description}</div>}
                </div>
                <div className="flex items-center gap-1">
                  {d.sensitive && (
                    <button onClick={() => reveal(d)}
                            className="text-ink-muted hover:text-brand-300 p-1.5 rounded hover:bg-surface-muted"
                            title={revealed.has(d.id) ? "Hide value" : "Reveal value"}>
                      {revealed.has(d.id) ? <EyeOff size={12} /> : <Eye size={12} />}
                    </button>
                  )}
                  <button onClick={() => { setEditing(d); setCreating(false); setError(null); }}
                          className="text-ink-muted hover:text-brand-300 p-1.5 rounded hover:bg-surface-muted"
                          title="Edit"><Pencil size={12} /></button>
                  <button onClick={() => { if (confirm(`Delete "${d.name}" (${d.environment})?`)) remove.mutate(d.id); }}
                          className="text-ink-muted hover:text-danger-500 p-1.5 rounded hover:bg-danger-500/10"
                          title="Delete"><Trash2 size={12} /></button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {(creating || editing) && (
        <DataEditor
          mode={creating ? "create" : "edit"}
          initial={editing ?? undefined}
          error={error}
          busy={create.isPending || update.isPending}
          onClose={() => { setCreating(false); setEditing(null); setError(null); }}
          onSubmit={(payload) => {
            if (creating) create.mutate(payload as WebTestDataCreate);
            else if (editing) update.mutate({ id: editing.id, body: payload as WebTestDataUpdate });
          }}
        />
      )}
    </div>
  );
}

function DataEditor({ mode, initial, error, busy, onClose, onSubmit }: {
  mode: "create" | "edit";
  initial?: WebTestDataView;
  error: string | null;
  busy: boolean;
  onClose: () => void;
  onSubmit: (payload: WebTestDataCreate | WebTestDataUpdate) => void;
}) {
  const [name, setName] = useState(initial?.name ?? "");
  const [environment, setEnvironment] = useState(initial?.environment ?? "default");
  const [value, setValue] = useState(initial?.masked ? "" : (initial?.value ?? ""));
  const [description, setDescription] = useState(initial?.description ?? "");
  const [sensitive, setSensitive] = useState(initial?.sensitive ?? false);

  function submit() {
    if (!name.trim() || !environment.trim() || !value.trim()) return;
    onSubmit({
      name: name.trim(),
      environment: environment.trim(),
      value: value.trim(),
      description: description.trim() || null,
      sensitive,
    });
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-xl flex flex-col max-h-[90vh]">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div className="text-sm font-semibold">{mode === "create" ? "New test data" : "Edit test data"}</div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted"><X size={14} /></button>
        </div>

        <div className="p-5 space-y-3 overflow-auto">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label block mb-1">Name</label>
              <input value={name} onChange={(e) => setName(e.target.value)}
                     placeholder="admin-password"
                     className="w-full h-8 px-2.5 rounded-md border border-surface-border bg-surface text-xs font-mono focus:outline-none focus:ring-1 focus:ring-brand-500" />
            </div>
            <div>
              <label className="label block mb-1">Environment</label>
              <input value={environment} onChange={(e) => setEnvironment(e.target.value)}
                     placeholder="default | staging | prod"
                     className="w-full h-8 px-2.5 rounded-md border border-surface-border bg-surface text-xs font-mono focus:outline-none focus:ring-1 focus:ring-brand-500" />
            </div>
          </div>

          <div>
            <label className="label block mb-1">Value</label>
            <input value={value} onChange={(e) => setValue(e.target.value)}
                   placeholder={initial?.masked ? "(re-enter to update)" : ""}
                   className="w-full h-8 px-2.5 rounded-md border border-surface-border bg-surface text-xs font-mono focus:outline-none focus:ring-1 focus:ring-brand-500" />
          </div>

          <div>
            <label className="label block mb-1">Description</label>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2}
                      className="w-full px-2.5 py-1.5 rounded-md border border-surface-border bg-surface text-xs focus:outline-none focus:ring-1 focus:ring-brand-500" />
          </div>

          <label className="flex items-center gap-2 cursor-pointer text-xs">
            <input type="checkbox" checked={sensitive} onChange={(e) => setSensitive(e.target.checked)} className="accent-brand-500" />
            <span>Mark as sensitive (value masked in lists; redacted in reports)</span>
          </label>

          {error && (
            <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">
              {error}
            </div>
          )}
        </div>

        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={busy} disabled={!name.trim() || !environment.trim() || !value.trim()} onClick={submit}>
            {mode === "create" ? "Create" : "Save changes"}
          </Button>
        </div>
      </Card>
    </div>
  );
}
