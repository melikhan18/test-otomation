import { useMemo, useState } from "react";
import {
  ChevronDown, ChevronRight, FileCheck2, FolderKanban, FolderOpen, Link2, Plus, Search, X,
} from "lucide-react";
import { cn } from "@/lib/cn";
import type { WorkspaceTree } from "@/lib/automation";

export type Selection =
  | { kind: "suite"; id: number }
  | { kind: "scenario"; id: number; suiteId?: number }
  | null;

type Props = {
  tree: WorkspaceTree | undefined;
  selection: Selection;
  onSelect: (s: Selection) => void;
  onCreateSuite: () => void;
  onCreateScenario: (suiteId?: number) => void;
  className?: string;
};

/**
 * Postman-style collapsible tree. Suites expand to reveal their scenarios; a separate
 * "Unassigned" group lists scenarios that aren't in any suite.
 */
export default function WorkspaceTree({
  tree, selection, onSelect, onCreateSuite, onCreateScenario, className,
}: Props) {
  const [search, setSearch] = useState("");
  // Default: only the selected suite is expanded.
  const [openSuites, setOpenSuites] = useState<Set<number>>(() => {
    const s = new Set<number>();
    if (selection?.kind === "scenario" && selection.suiteId) s.add(selection.suiteId);
    if (selection?.kind === "suite") s.add(selection.id);
    return s;
  });
  const [showOrphans, setShowOrphans] = useState(true);

  // Identify scenarios that live in >1 suite — used for the "shared" indicator.
  const sharedScenarioIds = useMemo(() => {
    if (!tree) return new Set<number>();
    const counts = new Map<number, number>();
    for (const su of tree.suites) {
      for (const sc of su.scenarios) counts.set(sc.id, (counts.get(sc.id) ?? 0) + 1);
    }
    const out = new Set<number>();
    counts.forEach((n, id) => { if (n > 1) out.add(id); });
    return out;
  }, [tree]);

  const filtered = useMemo(() => {
    if (!tree) return tree;
    if (!search) return tree;
    const q = search.toLowerCase();
    const filterNodes = (sc: typeof tree.orphanScenarios) =>
      sc.filter((s) =>
        s.name.toLowerCase().includes(q) ||
        (s.description ?? "").toLowerCase().includes(q) ||
        s.tags.some((t) => t.toLowerCase().includes(q)),
      );
    return {
      ...tree,
      suites: tree.suites
        .map((su) => ({
          ...su,
          scenarios: filterNodes(su.scenarios),
          matchesSelf:
            su.name.toLowerCase().includes(q) ||
            (su.description ?? "").toLowerCase().includes(q) ||
            su.tags.some((t) => t.toLowerCase().includes(q)),
        }))
        .filter((su: any) => su.matchesSelf || su.scenarios.length > 0),
      orphanScenarios: filterNodes(tree.orphanScenarios),
    };
  }, [tree, search]);

  function toggleSuite(id: number) {
    setOpenSuites((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  const isSuiteSelected = (id: number) =>
    selection?.kind === "suite" && selection.id === id;
  const isScenarioSelected = (id: number) =>
    selection?.kind === "scenario" && selection.id === id;

  return (
    <div className={cn("flex flex-col h-full min-h-0 bg-surface-raised/30", className)}>
      <div className="p-3 border-b border-surface-border space-y-2">
        <div className="relative">
          <Search size={12} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-ink-muted" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search suites / scenarios"
            className="input pl-7 pr-7 text-xs h-8"
          />
          {search && (
            <button onClick={() => setSearch("")} className="absolute right-2 top-1/2 -translate-y-1/2 text-ink-muted hover:text-ink-primary">
              <X size={11} />
            </button>
          )}
        </div>
      </div>

      <div className="flex-1 overflow-auto py-2">
        {/* Suites group */}
        <SectionHeader
          label={`Suites · ${filtered?.suites.length ?? 0}`}
          onAdd={onCreateSuite}
          addLabel="Create suite"
        />
        {filtered?.suites.length === 0 && (
          <div className="px-4 py-2 text-[11px] text-ink-muted italic">No suites yet.</div>
        )}
        <div className="space-y-0.5 px-1">
          {filtered?.suites.map((suite) => {
            const open = openSuites.has(suite.id) || !!search;
            const selected = isSuiteSelected(suite.id);
            return (
              <div key={suite.id}>
                <div
                  className={cn(
                    "group flex items-center h-7 rounded-md cursor-pointer transition-colors text-xs",
                    selected ? "bg-brand-500/15 text-ink-primary" : "hover:bg-surface-muted text-ink-secondary",
                  )}
                  onClick={() => onSelect({ kind: "suite", id: suite.id })}
                >
                  <button
                    onClick={(e) => { e.stopPropagation(); toggleSuite(suite.id); }}
                    className="px-1.5 text-ink-muted hover:text-ink-primary shrink-0"
                  >
                    {suite.scenarios.length > 0
                      ? (open ? <ChevronDown size={11} /> : <ChevronRight size={11} />)
                      : <span className="inline-block w-[11px]" />}
                  </button>
                  <span className="text-warning-500 shrink-0">
                    {open ? <FolderOpen size={12} /> : <FolderKanban size={12} />}
                  </span>
                  <span className="ml-1.5 font-medium truncate flex-1" title={suite.name}>{suite.name}</span>
                  <span className="text-[10px] font-mono text-ink-muted shrink-0 mr-1">{suite.scenarios.length}</span>
                  <button
                    onClick={(e) => { e.stopPropagation(); onCreateScenario(suite.id); }}
                    className="px-1 text-ink-muted hover:text-ink-primary opacity-0 group-hover:opacity-100 transition-opacity"
                    title="Add scenario to this suite"
                  >
                    <Plus size={11} />
                  </button>
                </div>

                {open && (
                  <div className="pl-3 border-l border-surface-border ml-3 mt-0.5 space-y-0.5">
                    {suite.scenarios.length === 0 ? (
                      <div className="text-[10px] text-ink-muted italic py-0.5 px-2">empty</div>
                    ) : suite.scenarios.map((sc) => (
                      <ScenarioRow
                        key={`${suite.id}-${sc.id}`}
                        node={sc}
                        selected={isScenarioSelected(sc.id)}
                        shared={sharedScenarioIds.has(sc.id)}
                        onClick={() => onSelect({ kind: "scenario", id: sc.id, suiteId: suite.id })}
                      />
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Unassigned scenarios */}
        {(filtered?.orphanScenarios.length ?? 0) > 0 && (
          <div className="mt-3">
            <SectionHeader
              label={`Unassigned · ${filtered?.orphanScenarios.length ?? 0}`}
              toggled={showOrphans}
              onToggle={() => setShowOrphans(!showOrphans)}
              onAdd={() => onCreateScenario(undefined)}
              addLabel="Create scenario"
            />
            {showOrphans && (
              <div className="space-y-0.5 px-1">
                {filtered?.orphanScenarios.map((sc) => (
                  <ScenarioRow
                    key={sc.id}
                    node={sc}
                    selected={isScenarioSelected(sc.id)}
                    onClick={() => onSelect({ kind: "scenario", id: sc.id })}
                    indent={false}
                  />
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Footer create buttons */}
      <div className="border-t border-surface-border p-2 space-y-1">
        <button
          onClick={onCreateSuite}
          className="w-full inline-flex items-center gap-2 h-7 px-2 rounded-md border border-dashed border-surface-border text-ink-secondary hover:text-ink-primary hover:border-brand-500/40 hover:bg-surface-muted/40 transition-colors text-xs"
        >
          <Plus size={11} /> New suite
        </button>
        <button
          onClick={() => onCreateScenario(undefined)}
          className="w-full inline-flex items-center gap-2 h-7 px-2 rounded-md border border-dashed border-surface-border text-ink-secondary hover:text-ink-primary hover:border-brand-500/40 hover:bg-surface-muted/40 transition-colors text-xs"
        >
          <Plus size={11} /> New scenario
        </button>
      </div>
    </div>
  );
}

function ScenarioRow({
  node, selected, shared, onClick, indent = true,
}: {
  node: { id: number; name: string; stepCount: number };
  selected: boolean; shared?: boolean;
  onClick: () => void; indent?: boolean;
}) {
  return (
    <div
      onClick={onClick}
      className={cn(
        "group flex items-center h-6 rounded-md cursor-pointer transition-colors text-xs",
        selected ? "bg-brand-500/15 text-ink-primary" : "hover:bg-surface-muted text-ink-secondary",
        indent ? "" : "",
      )}
      title={shared ? `${node.name} — referenced by multiple suites` : node.name}
    >
      <span className="px-1.5 inline-block w-[11px]" />
      <FileCheck2 size={11} className={selected ? "text-brand-300" : "text-ink-muted"} />
      <span className="ml-1.5 truncate flex-1">{node.name}</span>
      {shared && (
        <Link2 size={10} className="text-warning-500 shrink-0 mr-1" />
      )}
      <span className="text-[10px] font-mono text-ink-muted shrink-0 mr-2">{node.stepCount}</span>
    </div>
  );
}

function SectionHeader({
  label, onAdd, addLabel, toggled, onToggle,
}: { label: string; onAdd?: () => void; addLabel?: string; toggled?: boolean; onToggle?: () => void }) {
  const Icon = toggled === false ? ChevronRight : ChevronDown;
  return (
    <div className="flex items-center justify-between px-3 mb-1">
      <button
        onClick={onToggle}
        disabled={!onToggle}
        className="inline-flex items-center gap-1 text-[10px] uppercase tracking-[0.08em] font-semibold text-ink-muted hover:text-ink-primary"
      >
        {onToggle && <Icon size={10} />}
        {label}
      </button>
      {onAdd && (
        <button onClick={onAdd} className="text-ink-muted hover:text-ink-primary" title={addLabel}>
          <Plus size={11} />
        </button>
      )}
    </div>
  );
}
