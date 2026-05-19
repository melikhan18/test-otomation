import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Eye, EyeOff, Lock, Pencil, Plus, Search, ShieldCheck, Trash2, X,
} from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { useAuthStore } from "@/store/auth";
import { cn } from "@/lib/cn";
import {
  testDataApi, type TestDataCreate, type TestDataUpdate, type TestDataView,
} from "@/lib/automation";

export default function DataPage() {
  const role = useAuthStore((s) => s.role);
  const qc = useQueryClient();
  const [search, setSearch] = useState("");
  const [environment, setEnvironment] = useState<string>("all");
  const [reveal, setReveal] = useState(false);
  const [editing, setEditing] = useState<TestDataView | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<TestDataView | null>(null);

  const envsQ = useQuery({
    queryKey: ["automation-test-data-envs"],
    queryFn: testDataApi.environments,
  });

  const dataQ = useQuery({
    queryKey: ["automation-test-data", environment, reveal],
    queryFn: () => testDataApi.list(environment === "all" ? undefined : environment, reveal),
    refetchOnWindowFocus: false,
  });

  const create = useMutation({
    mutationFn: (b: TestDataCreate) => testDataApi.create(b),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["automation-test-data"] }); qc.invalidateQueries({ queryKey: ["automation-test-data-envs"] }); setCreating(false); setError(null); },
    onError: (e: any) => setError(e?.response?.data?.detail ?? "create failed"),
  });
  const update = useMutation({
    mutationFn: ({ id, body }: { id: number; body: TestDataUpdate }) => testDataApi.update(id, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["automation-test-data"] }); qc.invalidateQueries({ queryKey: ["automation-test-data-envs"] }); setEditing(null); setError(null); },
    onError: (e: any) => setError(e?.response?.data?.detail ?? "update failed"),
  });
  const remove = useMutation({
    mutationFn: (id: number) => testDataApi.delete(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["automation-test-data"] }); setConfirmDelete(null); },
  });

  const filtered = useMemo(() => {
    const list = dataQ.data ?? [];
    if (!search) return list;
    const q = search.toLowerCase();
    return list.filter((d) =>
      d.name.toLowerCase().includes(q) ||
      d.environment.toLowerCase().includes(q) ||
      (!d.masked && d.value.toLowerCase().includes(q)) ||
      (d.description ?? "").toLowerCase().includes(q),
    );
  }, [dataQ.data, search]);

  const envTabs = ["all", ...(envsQ.data ?? ["default"])];

  return (
    <>
      <TopBar
        crumbs={[{ label: "Automation", to: "/automation/elements" }, { label: "Test data" }]}
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

            {role === "ADMIN" && (
              <button
                onClick={() => setReveal(!reveal)}
                className={cn(
                  "inline-flex items-center gap-2 h-9 px-3 rounded-md border text-xs font-medium transition-colors",
                  reveal
                    ? "border-warning-500/40 bg-warning-500/10 text-warning-500"
                    : "border-surface-border bg-surface hover:bg-surface-muted text-ink-secondary hover:text-ink-primary",
                )}
                title="Reveal sensitive values (admin only)"
              >
                {reveal ? <Eye size={13} /> : <EyeOff size={13} />}
                {reveal ? "Sensitive shown" : "Sensitive masked"}
              </button>
            )}
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
                : "Test data are named values referenced by test steps (e.g. phone-number-1)."}
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
                        {d.value}
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

      {creating && (
        <DataEditor
          mode="create"
          environments={envsQ.data ?? ["default"]}
          defaultEnvironment={environment === "all" ? "default" : environment}
          busy={create.isPending}
          error={error}
          onClose={() => setCreating(false)}
          onSubmit={(body) => create.mutate(body)}
        />
      )}

      {editing && (
        <DataEditor
          mode="edit"
          initial={editing}
          environments={envsQ.data ?? ["default"]}
          busy={update.isPending}
          error={error}
          onClose={() => setEditing(null)}
          onSubmit={(body) => update.mutate({ id: editing.id, body })}
        />
      )}

      {confirmDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <Card className="w-full max-w-md p-5">
            <div className="text-sm font-semibold">Delete value?</div>
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

function DataEditor({
  mode, initial, environments, defaultEnvironment, busy, error, onClose, onSubmit,
}: {
  mode: "create" | "edit";
  initial?: TestDataView;
  environments: string[];
  defaultEnvironment?: string;
  busy?: boolean;
  error?: string | null;
  onClose: () => void;
  onSubmit: (body: TestDataCreate) => void;
}) {
  const [name, setName] = useState(initial?.name ?? "");
  const [environment, setEnvironment] = useState(initial?.environment ?? defaultEnvironment ?? "default");
  const [customEnv, setCustomEnv] = useState("");
  const [value, setValue] = useState(initial?.value ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [sensitive, setSensitive] = useState(initial?.sensitive ?? false);

  const useCustom = environment === "__custom__";
  const finalEnv = useCustom ? customEnv.trim() : environment;

  function submit() {
    onSubmit({
      name: name.trim(),
      environment: finalEnv,
      value: value,
      description: description?.trim() || null,
      sensitive,
    });
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-lg max-h-[90vh] flex flex-col">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div>
            <div className="text-sm font-semibold">{mode === "create" ? "New test data" : "Edit test data"}</div>
            <div className="text-xs text-ink-muted mt-0.5">Reusable named value referenced by test steps.</div>
          </div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted">
            <X size={14} />
          </button>
        </div>

        <div className="p-5 space-y-4 overflow-auto">
          <label className="block">
            <span className="label block mb-1.5">Name (kebab-case)</span>
            <input autoFocus value={name} onChange={(e) => setName(e.target.value)} placeholder="phone-number-1" className="input" />
          </label>

          <label className="block">
            <span className="label block mb-1.5">Environment</span>
            <div className="flex gap-2">
              <select value={environment} onChange={(e) => setEnvironment(e.target.value)} className="input flex-1">
                {environments.map((e) => <option key={e} value={e}>{e}</option>)}
                <option value="__custom__">+ new environment…</option>
              </select>
              {useCustom && (
                <input
                  value={customEnv}
                  onChange={(e) => setCustomEnv(e.target.value)}
                  placeholder="staging"
                  className="input flex-1 font-mono text-xs"
                />
              )}
            </div>
          </label>

          <label className="block">
            <span className="label block mb-1.5">Value</span>
            <textarea
              value={value}
              onChange={(e) => setValue(e.target.value)}
              rows={3}
              className="input font-mono text-xs resize-y"
              placeholder="+90 555 123 4567"
            />
          </label>

          <label className="block">
            <span className="label block mb-1.5">Description (optional)</span>
            <input value={description ?? ""} onChange={(e) => setDescription(e.target.value)} className="input" />
          </label>

          <label className="flex items-center gap-2 cursor-pointer">
            <input type="checkbox" checked={sensitive} onChange={(e) => setSensitive(e.target.checked)} className="accent-brand-500" />
            <span className="text-sm">Sensitive — mask the value in UI and logs</span>
          </label>

          {error && (
            <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">
              {error}
            </div>
          )}
        </div>

        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            variant="primary"
            loading={busy}
            disabled={!name.trim() || !finalEnv || !value}
            onClick={submit}
          >
            {mode === "create" ? "Create" : "Save"}
          </Button>
        </div>
      </Card>
    </div>
  );
}
