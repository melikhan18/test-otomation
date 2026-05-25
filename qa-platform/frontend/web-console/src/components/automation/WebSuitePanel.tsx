import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  FileCheck2, FolderKanban, Pencil, Play, Plus, Trash2, X,
} from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { cn } from "@/lib/cn";
import {
  browserApi, webScenarioApi, webSuiteApi, webSuiteRunApi,
  type BrowserProfile, type WebSuiteUpdate,
} from "@/lib/webAutomation";

type Props = {
  suiteId: number;
  onSelectScenario: (scenarioId: number, suiteId: number) => void;
  onAfterDelete: () => void;
  onMutated: () => void;
};

/**
 * Visual mirror of Android's {@code SuitePanel}. Header card (suite name +
 * description + tags + scenario count), scenario list (click → workspace
 * selection jumps to that scenario), run button. Skips Android-only
 * features (drag reorder, status badges per scenario) for v1.
 */
export default function WebSuitePanel({ suiteId, onSelectScenario, onAfterDelete, onMutated }: Props) {
  const qc = useQueryClient();
  const suiteQ = useQuery({
    queryKey: ["web-suite", suiteId],
    queryFn: () => webSuiteApi.get(suiteId),
  });
  const scenariosQ = useQuery({ queryKey: ["web-scenarios"], queryFn: webScenarioApi.list });

  function invalidate() {
    qc.invalidateQueries({ queryKey: ["web-suite", suiteId] });
    qc.invalidateQueries({ queryKey: ["web-workspace-tree"] });
    onMutated();
  }

  const addScenario = useMutation({
    mutationFn: (sid: number) => webSuiteApi.addScenario(suiteId, sid),
    onSuccess: invalidate,
  });
  const removeScenario = useMutation({
    mutationFn: (sid: number) => webSuiteApi.removeScenario(suiteId, sid),
    onSuccess: invalidate,
  });
  const updateMeta = useMutation({
    mutationFn: (b: WebSuiteUpdate) => webSuiteApi.update(suiteId, b),
    onSuccess: invalidate,
  });
  const remove = useMutation({
    mutationFn: () => webSuiteApi.delete(suiteId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["web-workspace-tree"] });
      onAfterDelete();
    },
  });

  const [editingMeta, setEditingMeta] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [addPickerOpen, setAddPickerOpen] = useState(false);
  const [running, setRunning] = useState(false);

  if (suiteQ.isLoading) {
    return <div className="p-6 text-ink-muted flex items-center gap-2 text-sm"><Spinner /> Loading suite…</div>;
  }
  if (suiteQ.error || !suiteQ.data) return <div className="p-6 text-danger-500">Suite not found</div>;

  const suite = suiteQ.data;
  const inSuite = new Set(suite.scenarios.map((s) => s.scenarioId));
  const candidates = (scenariosQ.data ?? []).filter((sc) => !inSuite.has(sc.id));

  return (
    <div className="space-y-3">
      <Card className="p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2 text-warning-500">
              <FolderKanban size={14} />
              <span className="text-[10px] uppercase tracking-wider font-semibold">Suite</span>
            </div>
            <div className="text-lg font-semibold truncate mt-0.5">{suite.name}</div>
            {suite.description && <div className="text-xs text-ink-muted mt-1">{suite.description}</div>}
            <div className="flex items-center gap-2 mt-2 flex-wrap">
              {suite.tags.map((t) => (
                <span key={t} className="text-[10px] uppercase tracking-wider rounded px-1.5 py-0.5 border border-surface-border text-ink-secondary">{t}</span>
              ))}
              <span className="text-[10px] text-ink-muted font-mono">{suite.scenarios.length} scenarios</span>
            </div>
          </div>
          <div className="flex items-center gap-1.5">
            <Button
              variant="primary"
              size="sm"
              leftIcon={<Play size={12} />}
              disabled={suite.scenarios.length === 0}
              onClick={() => setRunning(true)}
              title={suite.scenarios.length === 0 ? "Add scenarios first" : "Run suite"}
            >
              Run suite
            </Button>
            <button onClick={() => setEditingMeta(true)} className="p-1.5 rounded text-ink-muted hover:text-ink-primary hover:bg-surface-muted" title="Edit suite">
              <Pencil size={13} />
            </button>
            <button
              onClick={() => setConfirmingDelete(true)}
              className="p-1.5 rounded text-ink-muted hover:text-danger-500 hover:bg-surface-muted"
              title="Delete suite"
            >
              <Trash2 size={13} />
            </button>
          </div>
        </div>
      </Card>

      {suite.scenarios.length === 0 ? (
        <Card>
          <EmptyState
            icon={<FileCheck2 size={20} />}
            title="No scenarios in this suite yet"
            description="Add an existing scenario to run them sequentially."
            action={
              <Button variant="primary" size="sm" leftIcon={<Plus size={14} />} disabled={candidates.length === 0} onClick={() => setAddPickerOpen(true)}>
                {candidates.length === 0 ? "No scenarios available" : "Add scenario"}
              </Button>
            }
          />
        </Card>
      ) : (
        <div className="space-y-2">
          {suite.scenarios.map((ref) => (
            <Card key={ref.scenarioId} className="p-3 group">
              <div className="flex items-center gap-3">
                <div className="text-[10px] text-ink-muted w-6 text-right font-mono">{ref.orderIndex + 1}</div>
                <FileCheck2 size={13} className="text-ink-muted shrink-0" />
                <div
                  className="flex-1 min-w-0 cursor-pointer"
                  onClick={() => onSelectScenario(ref.scenarioId, suiteId)}
                  title="Jump to scenario"
                >
                  <div className="text-sm font-medium truncate text-ink-primary hover:text-brand-300 transition-colors">{ref.name}</div>
                  <div className="text-[10px] text-ink-muted">{ref.stepCount} step{ref.stepCount === 1 ? "" : "s"}{ref.description ? ` · ${ref.description}` : ""}</div>
                </div>
                <button
                  onClick={() => removeScenario.mutate(ref.scenarioId)}
                  className="p-1.5 rounded text-ink-muted hover:text-danger-500 hover:bg-danger-500/10 opacity-0 group-hover:opacity-100 transition-opacity"
                  title="Remove from suite"
                >
                  <Trash2 size={13} />
                </button>
              </div>
            </Card>
          ))}

          {candidates.length > 0 && (
            <button
              onClick={() => setAddPickerOpen(true)}
              className="w-full inline-flex items-center justify-center gap-1.5 h-9 rounded-md border border-dashed border-surface-border text-ink-secondary hover:text-ink-primary hover:border-brand-500/40 hover:bg-surface-muted/40 transition-colors text-xs"
            >
              <Plus size={12} /> Add scenario to suite
            </button>
          )}
        </div>
      )}

      {addPickerOpen && (
        <AddScenarioDialog
          candidates={candidates.map((sc) => ({ id: sc.id, name: sc.name }))}
          busy={addScenario.isPending}
          onClose={() => setAddPickerOpen(false)}
          onSubmit={(sid) => addScenario.mutate(sid, { onSuccess: () => setAddPickerOpen(false) })}
        />
      )}

      {editingMeta && (
        <SuiteMetaDialog
          initial={{ name: suite.name, description: suite.description, tags: suite.tags }}
          busy={updateMeta.isPending}
          onClose={() => setEditingMeta(false)}
          onSubmit={(b) => updateMeta.mutate(b, { onSuccess: () => setEditingMeta(false) })}
        />
      )}

      {confirmingDelete && (
        <ConfirmDelete
          name={suite.name}
          busy={remove.isPending}
          onCancel={() => setConfirmingDelete(false)}
          onConfirm={() => remove.mutate()}
        />
      )}

      {running && (
        <SuiteRunDialog
          suiteId={suiteId}
          suiteName={suite.name}
          onClose={() => setRunning(false)}
        />
      )}
    </div>
  );
}

function AddScenarioDialog({ candidates, busy, onClose, onSubmit }: {
  candidates: { id: number; name: string }[];
  busy: boolean;
  onClose: () => void;
  onSubmit: (scenarioId: number) => void;
}) {
  const [picked, setPicked] = useState<number | null>(null);
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-lg flex flex-col max-h-[80vh]">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div className="text-sm font-semibold">Add scenario to suite</div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted"><X size={14} /></button>
        </div>
        <div className="p-5 overflow-auto">
          {candidates.length === 0 ? (
            <div className="text-xs text-ink-muted">No scenarios available — every scenario is already in this suite.</div>
          ) : (
            <ul className="divide-y divide-surface-border border border-surface-border rounded-md overflow-hidden">
              {candidates.map((sc) => (
                <li key={sc.id}>
                  <button onClick={() => setPicked(sc.id)}
                          className={cn("w-full text-left px-3 py-2 text-sm transition-colors",
                            picked === sc.id ? "bg-brand-500/15 text-ink-primary" : "hover:bg-surface-muted text-ink-secondary")}>
                    {sc.name}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={busy} disabled={picked == null} onClick={() => picked != null && onSubmit(picked)}>
            Add
          </Button>
        </div>
      </Card>
    </div>
  );
}

function SuiteMetaDialog({ initial, busy, onClose, onSubmit }: {
  initial: { name: string; description: string | null; tags: string[] };
  busy: boolean;
  onClose: () => void;
  onSubmit: (b: WebSuiteUpdate) => void;
}) {
  const [name, setName] = useState(initial.name);
  const [description, setDescription] = useState(initial.description ?? "");
  const [tagsInput, setTagsInput] = useState(initial.tags.join(", "));
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-lg">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div className="text-sm font-semibold">Edit suite</div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted"><X size={14} /></button>
        </div>
        <div className="p-5 space-y-4">
          <label className="block">
            <span className="label block mb-1.5">Name</span>
            <input value={name} onChange={(e) => setName(e.target.value)} className="input" autoFocus />
          </label>
          <label className="block">
            <span className="label block mb-1.5">Description</span>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} className="input resize-y" />
          </label>
          <label className="block">
            <span className="label block mb-1.5">Tags (comma-separated)</span>
            <input value={tagsInput} onChange={(e) => setTagsInput(e.target.value)} className="input" />
          </label>
        </div>
        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={busy} disabled={!name.trim()}
                  onClick={() => onSubmit({
                    name: name.trim(),
                    description: description.trim() || null,
                    tags: tagsInput.split(",").map((t) => t.trim()).filter(Boolean),
                  })}>
            Save
          </Button>
        </div>
      </Card>
    </div>
  );
}

function ConfirmDelete({ name, busy, onCancel, onConfirm }: {
  name: string; busy: boolean; onCancel: () => void; onConfirm: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-md p-5">
        <div className="text-sm font-semibold">Delete suite?</div>
        <div className="text-xs text-ink-muted mt-1">
          <code className="font-mono">{name}</code> will be removed. Scenarios inside the suite stay intact (they just lose this grouping).
        </div>
        <div className="flex justify-end gap-2 mt-5">
          <Button variant="secondary" onClick={onCancel}>Cancel</Button>
          <Button variant="danger" loading={busy} onClick={onConfirm}>Delete</Button>
        </div>
      </Card>
    </div>
  );
}

function SuiteRunDialog({ suiteId, suiteName, onClose }: { suiteId: number; suiteName: string; onClose: () => void }) {
  const qc = useQueryClient();
  const browsersQ = useQuery({ queryKey: ["browsers"], queryFn: browserApi.list });
  const [profileId, setProfileId] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () => webSuiteRunApi.create({ suiteId, browserProfileId: profileId! }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["web-suite-runs"] }); onClose(); },
  });
  const err = (create.error as any)?.response?.data?.detail;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-2xl flex flex-col max-h-[90vh]">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div>
            <div className="text-sm font-semibold flex items-center gap-2">
              <Play size={14} className="text-success-500" />
              Run suite
            </div>
            <div className="text-xs text-ink-muted mt-0.5">
              <code className="font-mono">{suiteName}</code> — scenarios run sequentially on the chosen browser.
            </div>
          </div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted"><X size={14} /></button>
        </div>
        <div className="p-5 space-y-3 overflow-auto">
          <span className="label block mb-1.5">Browser profile</span>
          {browsersQ.isLoading && <div className="text-xs text-ink-muted flex items-center gap-2"><Spinner /> Loading…</div>}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            {(browsersQ.data ?? []).map((b: BrowserProfile) => (
              <button key={b.id} onClick={() => setProfileId(b.id)}
                      className={cn("text-left px-3 py-2 rounded-md border transition-colors",
                        profileId === b.id ? "border-brand-500/50 bg-brand-500/10" : "border-surface-border hover:border-brand-500/30 bg-surface")}>
                <div className="text-sm font-medium">{b.displayName}</div>
                <div className="text-[10px] text-ink-muted">{b.engine} · {b.width}×{b.height}{b.isMobile && " · mobile"}</div>
              </button>
            ))}
          </div>
          {err && (
            <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">{err}</div>
          )}
        </div>
        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="primary" leftIcon={<Play size={12} />} disabled={!profileId} loading={create.isPending} onClick={() => create.mutate()}>
            Run suite
          </Button>
        </div>
      </Card>
    </div>
  );
}
