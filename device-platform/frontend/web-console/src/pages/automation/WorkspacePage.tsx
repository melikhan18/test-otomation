import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { FolderKanban, X } from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import WorkspaceTree, { type Selection } from "@/components/automation/WorkspaceTree";
import SuitePanel from "@/components/automation/SuitePanel";
import ScenarioPanel from "@/components/automation/ScenarioPanel";
import {
  scenarioApi, suiteApi, workspaceApi,
  type ScenarioCreate, type SuiteCreate,
} from "@/lib/automation";
import { useAuthStore } from "@/store/auth";

export default function WorkspacePage() {
  const qc = useQueryClient();
  const [params, setParams] = useSearchParams();
  const activeCompanyId = useAuthStore((s) => s.activeCompanyId);
  const activeProjectId = useAuthStore((s) => s.activeProjectId);

  // Drop any ?scenario= / ?suite= selection when the user switches project or
  // company — the id belongs to the *previous* workspace and would otherwise
  // surface as "Scenario not found" once the tree refetch returns nothing.
  const tenancySnapshot = useRef<{ c: number | null; p: number | null }>(
    { c: activeCompanyId ?? null, p: activeProjectId ?? null },
  );
  useEffect(() => {
    const prev = tenancySnapshot.current;
    const next = { c: activeCompanyId ?? null, p: activeProjectId ?? null };
    if (prev.c !== next.c || prev.p !== next.p) {
      tenancySnapshot.current = next;
      if (params.get("scenario") || params.get("suite")) {
        const cleared = new URLSearchParams(params);
        cleared.delete("scenario");
        cleared.delete("suite");
        setParams(cleared, { replace: true });
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeCompanyId, activeProjectId]);

  // ── Selection state, mirrored to URL so the view is shareable ──────
  const selection: Selection = (() => {
    const sc = params.get("scenario");
    const su = params.get("suite");
    if (sc) return { kind: "scenario", id: Number(sc), suiteId: su ? Number(su) : undefined };
    if (su) return { kind: "suite", id: Number(su) };
    return null;
  })();

  function setSelection(s: Selection) {
    const next = new URLSearchParams(params);
    next.delete("suite"); next.delete("scenario");
    if (s?.kind === "suite")    next.set("suite", String(s.id));
    if (s?.kind === "scenario") {
      next.set("scenario", String(s.id));
      if (s.suiteId) next.set("suite", String(s.suiteId));
    }
    setParams(next, { replace: true });
  }

  // ── Workspace tree ─────────────────────────────────────────────────
  const treeQ = useQuery({
    queryKey: ["automation-workspace-tree", activeCompanyId ?? null],
    queryFn: workspaceApi.tree,
    refetchOnWindowFocus: false,
    enabled: activeCompanyId != null,
  });

  function refreshTree() { qc.invalidateQueries({ queryKey: ["automation-workspace-tree"] }); }

  // ── Create dialogs ─────────────────────────────────────────────────
  const [creatingSuite, setCreatingSuite] = useState(false);
  const [creatingScenario, setCreatingScenario] = useState<{ suiteId?: number } | null>(null);

  const createSuite = useMutation({
    mutationFn: (b: SuiteCreate) => suiteApi.create(b),
    onSuccess: (s) => { refreshTree(); setCreatingSuite(false); setSelection({ kind: "suite", id: s.id }); },
  });
  const createScenario = useMutation({
    mutationFn: async (req: { body: ScenarioCreate; suiteId?: number }) => {
      const created = await scenarioApi.create(req.body);
      if (req.suiteId) await suiteApi.addScenario(req.suiteId, created.id);
      return { scenario: created, suiteId: req.suiteId };
    },
    onSuccess: ({ scenario, suiteId }) => {
      refreshTree();
      setCreatingScenario(null);
      setSelection({ kind: "scenario", id: scenario.id, suiteId });
    },
  });

  // If the selected resource was deleted, clear selection.
  useEffect(() => {
    if (!selection || !treeQ.data) return;
    if (selection.kind === "suite") {
      const ok = treeQ.data.suites.some((s) => s.id === selection.id);
      if (!ok) setSelection(null);
    } else {
      const ok =
        treeQ.data.orphanScenarios.some((s) => s.id === selection.id) ||
        treeQ.data.suites.some((su) => su.scenarios.some((s) => s.id === selection.id));
      if (!ok) setSelection(null);
    }
  }, [treeQ.data?.totalScenarios, treeQ.data?.totalSuites]);

  return (
    <>
      <TopBar
        crumbs={[{ label: "Automation", to: "/automation" }, { label: "Workspace" }]}
        actions={
          treeQ.data && (
            <span className="text-[11px] text-ink-muted font-mono">
              {treeQ.data.totalSuites} suites · {treeQ.data.totalScenarios} scenarios
            </span>
          )
        }
      />

      {/* Two-column workspace: tree (left, fixed) + detail (right, flex) */}
      <div className="grid grid-cols-[280px_1fr] min-h-[calc(100vh-4rem)]">
        <div className="border-r border-surface-border min-h-0">
          {treeQ.isLoading ? (
            <div className="p-6 text-ink-muted flex items-center gap-2 text-xs"><Spinner /> Loading…</div>
          ) : (
            <WorkspaceTree
              tree={treeQ.data}
              selection={selection}
              onSelect={setSelection}
              onCreateSuite={() => setCreatingSuite(true)}
              onCreateScenario={(suiteId) => setCreatingScenario({ suiteId })}
            />
          )}
        </div>

        <div className="p-6 min-w-0 overflow-x-hidden">
          {!selection ? (
            <Card className="h-full flex items-center justify-center">
              <EmptyState
                icon={<FolderKanban size={20} />}
                title="Pick a suite or scenario from the sidebar"
                description="Or use the buttons in the sidebar to create one. Suites group scenarios; scenarios contain ordered steps."
              />
            </Card>
          ) : selection.kind === "suite" ? (
            <SuitePanel
              suiteId={selection.id}
              onSelectScenario={(id, suiteId) => setSelection({ kind: "scenario", id, suiteId })}
              onAfterDelete={() => { setSelection(null); refreshTree(); }}
              onMutated={refreshTree}
            />
          ) : (
            <ScenarioPanel
              scenarioId={selection.id}
              onAfterDelete={() => { setSelection(null); refreshTree(); }}
              onMutated={refreshTree}
              onSelectSuite={(suiteId) => setSelection({ kind: "suite", id: suiteId })}
            />
          )}
        </div>
      </div>

      {creatingSuite && (
        <CreateSuiteDialog
          busy={createSuite.isPending}
          error={(createSuite.error as any)?.response?.data?.detail}
          onClose={() => setCreatingSuite(false)}
          onSubmit={(b) => createSuite.mutate(b)}
        />
      )}

      {creatingScenario && (
        <CreateScenarioDialog
          targetSuiteName={creatingScenario.suiteId
            ? treeQ.data?.suites.find((s) => s.id === creatingScenario.suiteId)?.name
            : undefined}
          busy={createScenario.isPending}
          error={(createScenario.error as any)?.response?.data?.detail}
          onClose={() => setCreatingScenario(null)}
          onSubmit={(b) => createScenario.mutate({ body: b, suiteId: creatingScenario.suiteId })}
        />
      )}
    </>
  );
}

/* ───────────────────────────  Dialogs  ────────────────────────────── */

function CreateSuiteDialog({
  busy, error, onClose, onSubmit,
}: { busy?: boolean; error?: string; onClose: () => void; onSubmit: (b: SuiteCreate) => void }) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [tagsInput, setTagsInput] = useState("");
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-lg">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div>
            <div className="text-sm font-semibold">New suite</div>
            <div className="text-xs text-ink-muted mt-0.5">A suite groups scenarios.</div>
          </div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted"><X size={14} /></button>
        </div>
        <div className="p-5 space-y-4">
          <label className="block">
            <span className="label block mb-1.5">Name</span>
            <input autoFocus value={name} onChange={(e) => setName(e.target.value)}
              placeholder="WhatsApp login scenarios" className="input" />
          </label>
          <label className="block">
            <span className="label block mb-1.5">Description (optional)</span>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} className="input resize-y" />
          </label>
          <label className="block">
            <span className="label block mb-1.5">Tags (comma-separated)</span>
            <input value={tagsInput} onChange={(e) => setTagsInput(e.target.value)}
              placeholder="login, smoke" className="input" />
          </label>
          {error && (
            <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">{error}</div>
          )}
        </div>
        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={busy} disabled={!name.trim()}
            onClick={() => onSubmit({
              name: name.trim(),
              description: description?.trim() || null,
              tags: tagsInput.split(",").map((t) => t.trim()).filter(Boolean),
            })}
          >Create</Button>
        </div>
      </Card>
    </div>
  );
}

function CreateScenarioDialog({
  targetSuiteName, busy, error, onClose, onSubmit,
}: {
  targetSuiteName?: string;
  busy?: boolean; error?: string;
  onClose: () => void; onSubmit: (b: ScenarioCreate) => void;
}) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [tagsInput, setTagsInput] = useState("");
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-lg">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div>
            <div className="text-sm font-semibold">New scenario</div>
            <div className="text-xs text-ink-muted mt-0.5">
              {targetSuiteName
                ? <>Will be added to suite <code className="font-mono">{targetSuiteName}</code>.</>
                : "Will be created as an unassigned scenario."}
            </div>
          </div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted"><X size={14} /></button>
        </div>
        <div className="p-5 space-y-4">
          <label className="block">
            <span className="label block mb-1.5">Name</span>
            <input autoFocus value={name} onChange={(e) => setName(e.target.value)}
              placeholder="Username Password Pass Test" className="input" />
          </label>
          <label className="block">
            <span className="label block mb-1.5">Description (optional)</span>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} className="input resize-y" />
          </label>
          <label className="block">
            <span className="label block mb-1.5">Tags (comma-separated)</span>
            <input value={tagsInput} onChange={(e) => setTagsInput(e.target.value)}
              placeholder="login, smoke, regression" className="input" />
          </label>
          {error && (
            <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">{error}</div>
          )}
        </div>
        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={busy} disabled={!name.trim()}
            onClick={() => onSubmit({
              name: name.trim(),
              description: description?.trim() || null,
              tags: tagsInput.split(",").map((t) => t.trim()).filter(Boolean),
            })}
          >Create</Button>
        </div>
      </Card>
    </div>
  );
}
