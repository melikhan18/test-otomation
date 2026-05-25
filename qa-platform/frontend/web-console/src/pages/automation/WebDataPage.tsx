import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Eye, EyeOff, Lock, Pencil, Plus, Search, ShieldCheck, Trash2, X } from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { cn } from "@/lib/cn";
import {
  webTestDataApi, type WebTestDataCreate, type WebTestDataUpdate, type WebTestDataView,
} from "@/lib/webAutomation";

/**
 * Web platform's test data page — visual mirror of Android's
 * {@code DataPage}: TopBar with action, filter card (search + sensitive
 * reveal toggle + env tabs), table layout. Sensitive values are masked
 * by default; toggle "Reveal" + the eye icon to read on demand.
 */
export default function WebDataPage() {
  const qc = useQueryClient();
  const [search, setSearch] = useState("");
  const [environment, setEnvironment] = useState<string>("all");
  const [reveal, setReveal] = useState(false);
  const [editing, setEditing] = useState<WebTestDataView | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<WebTestDataView | null>(null);

  const envsQ = useQuery({ queryKey: ["web-test-data-envs"], queryFn: webTestDataApi.environments });
  const dataQ = useQuery({ queryKey: ["web-test-data"], queryFn: webTestDataApi.list });

  const create = useMutation({
    mutationFn: (b: WebTestDataCreate) => webTestDataApi.create(b),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["web-test-data"] });
      qc.invalidateQueries({ queryKey: ["web-test-data-envs"] });
      setCreating(false); setError(null);
    },
    onError: (e: any) => setError(e?.response?.data?.detail ?? "create failed"),
  });
  const update = useMutation({
    mutationFn: ({ id, body }: { id: number; body: WebTestDataUpdate }) => webTestDataApi.update(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["web-test-data"] });
      qc.invalidateQueries({ queryKey: ["web-test-data-envs"] });
      setEditing(null); setError(null);
    },
    onError: (e: any) => setError(e?.response?.data?.detail ?? "update failed"),
  });
  const remove = useMutation({
    mutationFn: (id: number) => webTestDataApi.delete(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["web-test-data"] }); setConfirmDelete(null); },
  });

  // Reveal sensitive values inline by fetching each masked row with ?reveal=true on demand.
  // (The list endpoint doesn't have a reveal flag — we patch the cache per-row.)
  async function revealOne(d: WebTestDataView) {
    if (!d.masked) return;
    const full = await webTestDataApi.get(d.id, true);
    qc.setQueryData<WebTestDataView[]>(["web-test-data"], (old) =>
      (old ?? []).map((row) => row.id === d.id ? { ...row, value: full.value, masked: false } : row));
  }

  const filtered = useMemo(() => {
    const list = (dataQ.data ?? []).filter((d) =>
      environment === "all" || d.environment === environment,
    );
    if (!search) return list;
    const q = search.toLowerCase();
    return list.filter((d) =>
      d.name.toLowerCase().includes(q) ||
      d.environment.toLowerCase().includes(q) ||
      (!d.masked && d.value.toLowerCase().includes(q)) ||
      (d.description ?? "").toLowerCase().includes(q),
    );
  }, [dataQ.data, search, environment]);

  const envTabs = ["all", ...(envsQ.data ?? ["default"])];

  return (
    <>
      <TopBar
        crumbs={[{ label: "Automation", to: "/automation/web" }, { label: "Test data" }]}
        actions={
          <Button variant="primary" size="sm" leftIcon={<Plus size={14} />} onClick={() => { setCreating(true); setError(null); }}>
            New value
          </Button>
        }
      />

      <div className="px-6 py-6 space-y-6">
        <Card className="px-4 py-3 space-y-3">
          <div className="flex flex-col md:flex-row md:items-center gap-3">
            <div className="relative flex-1">
              <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-muted" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Filter by name, environment, value…"
                className="input pl-9 pr-9"
              />
              {search && (
                <button onClick={() => setSearch("")} className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-muted hover:text-ink-primary">
                  <X size={12} />
                </button>
              )}
            </div>

            <button
              onClick={() => setReveal(!reveal)}
              className={cn(
                "inline-flex items-center gap-2 h-9 px-3 rounded-md border text-xs font-medium transition-colors",
                reveal
                  ? "border-warning-500/40 bg-warning-500/10 text-warning-500"
                  : "border-surface-border bg-surface hover:bg-surface-muted text-ink-secondary hover:text-ink-primary",
              )}
              title="Reveal sensitive values inline"
            >
              {reveal ? <Eye size={13} /> : <EyeOff size={13} />}
              {reveal ? "Sensitive shown" : "Sensitive masked"}
            </button>
          </div>

          <div className="flex items-center gap-1 flex-wrap">
            {envTabs.map((env) => (
              <button
                key={env}
                onClick={() => setEnvironment(env)}
                className={cn(
                  "inline-flex items-center h-7 px-3 rounded-md text-xs font-medium border transition-colors",
                  environment === env
                    ? "bg-brand-500/15 border-brand-500/40 text-brand-300"
                    : "border-surface-border text-ink-secondary hover:text-ink-primary",
                )}
              >
                {env === "all" ? "All" : env}
              </button>
            ))}
          </div>
        </Card>

        {dataQ.isLoading ? (
          <Card className="flex items-center justify-center h-48 text-ink-muted gap-2 text-sm">
            <Spinner /> Loading…
          </Card>
        ) : filtered.length === 0 ? (
          <Card>
            <EmptyState
              icon={<ShieldCheck size={20} />}
              title={search || environment !== "all" ? "No data matches" : "No test data yet"}
              description={search || environment !== "all"
                ? "Try clearing the filter or switching environment."
                : "Test data are named values referenced by test steps (e.g. login-url)."}
              action={
                <Button variant="primary" size="sm" leftIcon={<Plus size={14} />} onClick={() => { setCreating(true); setError(null); }}>
                  Create value
                </Button>
              }
            />
          </Card>
        ) : (
          <Card className="overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-surface-raised/40 border-b border-surface-border text-[10px] uppercase tracking-wider text-ink-muted">
                  <tr>
                    <th className="text-left px-4 py-2 font-medium">Name</th>
                    <th className="text-left px-4 py-2 font-medium">Environment</th>
                    <th className="text-left px-4 py-2 font-medium">Value</th>
                    <th className="text-left px-4 py-2 font-medium">Notes</th>
                    <th className="text-right px-4 py-2 font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((d) => (
                    <tr key={d.id} className="border-b border-surface-border last:border-b-0 hover:bg-surface-muted/40">
                      <td className="px-4 py-2 font-mono text-xs">
                        <div className="flex items-center gap-2">
                          {d.sensitive && <Lock size={11} className="text-warning-500" />}
                          {d.name}
                        </div>
                      </td>
                      <td className="px-4 py-2 text-xs text-ink-secondary">{d.environment}</td>
                      <td className="px-4 py-2 font-mono text-xs text-ink-primary break-all max-w-[400px]">
                        {reveal && d.masked ? (
                          <button onClick={() => revealOne(d)} className="text-brand-400 hover:text-brand-300 underline-offset-2 hover:underline">
                            click to reveal
                          </button>
                        ) : d.value}
                      </td>
                      <td className="px-4 py-2 text-xs text-ink-muted max-w-[200px] truncate" title={d.description ?? ""}>
                        {d.description}
                      </td>
                      <td className="px-4 py-2 text-right">
                        <div className="inline-flex items-center gap-1">
                          <button onClick={() => { setEditing(d); setError(null); }} className="p-1.5 rounded text-ink-muted hover:text-ink-primary hover:bg-surface-muted" title="Edit">
                            <Pencil size={12} />
                          </button>
                          <button onClick={() => setConfirmDelete(d)} className="p-1.5 rounded text-ink-muted hover:text-danger-500 hover:bg-surface-muted" title="Delete">
                            <Trash2 size={12} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        )}
      </div>

      {(creating || editing) && (
        <DataEditor
          mode={creating ? "create" : "edit"}
          initial={editing ?? undefined}
          environments={envsQ.data ?? ["default"]}
          defaultEnvironment={environment === "all" ? "default" : environment}
          busy={create.isPending || update.isPending}
          error={error}
          onClose={() => { setCreating(false); setEditing(null); setError(null); }}
          onSubmit={(body) => {
            if (creating) create.mutate(body as WebTestDataCreate);
            else if (editing) update.mutate({ id: editing.id, body: body as WebTestDataUpdate });
          }}
        />
      )}

      {confirmDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <Card className="w-full max-w-md p-5">
            <div className="text-sm font-semibold">Delete test data?</div>
            <div className="text-xs text-ink-muted mt-1">
              <code className="font-mono">{confirmDelete.name}</code> ({confirmDelete.environment}) will be removed.
            </div>
            <div className="flex justify-end gap-2 mt-5">
              <Button variant="secondary" onClick={() => setConfirmDelete(null)}>Cancel</Button>
              <Button variant="danger" loading={remove.isPending} onClick={() => remove.mutate(confirmDelete.id)}>Delete</Button>
            </div>
          </Card>
        </div>
      )}
    </>
  );
}

/* ─────────────────────────  Editor dialog  ───────────────────────────── */

function DataEditor({ mode, initial, environments, defaultEnvironment, error, busy, onClose, onSubmit }: {
  mode: "create" | "edit";
  initial?: WebTestDataView;
  environments: string[];
  defaultEnvironment: string;
  error: string | null;
  busy: boolean;
  onClose: () => void;
  onSubmit: (payload: WebTestDataCreate | WebTestDataUpdate) => void;
}) {
  const [name, setName] = useState(initial?.name ?? "");
  const [environment, setEnvironment] = useState(initial?.environment ?? defaultEnvironment);
  const [value, setValue] = useState(initial?.masked ? "" : (initial?.value ?? ""));
  const [description, setDescription] = useState(initial?.description ?? "");
  const [sensitive, setSensitive] = useState(initial?.sensitive ?? false);
  const [envMode, setEnvMode] = useState<"pick" | "new">(environments.length > 0 ? "pick" : "new");

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

        <div className="p-5 space-y-4 overflow-auto">
          <label className="block">
            <span className="label block mb-1.5">Name</span>
            <input autoFocus value={name} onChange={(e) => setName(e.target.value)}
                   placeholder="admin-password" className="input font-mono" />
          </label>

          <div>
            <div className="flex items-center justify-between mb-1.5">
              <span className="label">Environment</span>
              {environments.length > 0 && (
                <button onClick={() => setEnvMode(envMode === "pick" ? "new" : "pick")}
                        className="text-[10px] text-brand-400 hover:text-brand-300">
                  {envMode === "pick" ? "New environment" : "Pick existing"}
                </button>
              )}
            </div>
            {envMode === "pick" && environments.length > 0 ? (
              <select value={environment} onChange={(e) => setEnvironment(e.target.value)} className="input">
                {environments.map((env) => <option key={env} value={env}>{env}</option>)}
              </select>
            ) : (
              <input value={environment} onChange={(e) => setEnvironment(e.target.value)}
                     placeholder="default | staging | prod" className="input font-mono" />
            )}
          </div>

          <label className="block">
            <span className="label block mb-1.5">Value</span>
            <input value={value} onChange={(e) => setValue(e.target.value)}
                   placeholder={initial?.masked ? "(re-enter to update)" : ""}
                   className="input font-mono" />
          </label>

          <label className="block">
            <span className="label block mb-1.5">Notes (optional)</span>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} className="input resize-y" />
          </label>

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
