import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  DndContext, KeyboardSensor, PointerSensor, closestCenter,
  useSensor, useSensors, type DragEndEvent,
} from "@dnd-kit/core";
import {
  SortableContext, arrayMove, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { restrictToVerticalAxis, restrictToParentElement } from "@dnd-kit/modifiers";
import {
  FileCheck2, FolderKanban, GripVertical, Pencil, Plus, Trash2,
} from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import Combobox, { type ComboOption } from "@/components/automation/Combobox";
import {
  scenarioApi, suiteApi,
  type SuiteScenarioRef, type SuiteSummary, type SuiteUpdate, type SuiteView,
} from "@/lib/automation";

type Props = {
  suiteId: number;
  onSelectScenario: (id: number, suiteId: number) => void;
  onAfterDelete: () => void;
  onMutated: () => void;
};

export default function SuitePanel({ suiteId, onSelectScenario, onAfterDelete, onMutated }: Props) {
  const qc = useQueryClient();
  const suiteQ = useQuery({
    queryKey: ["automation-suite", suiteId],
    queryFn: () => suiteApi.get(suiteId),
    refetchOnWindowFocus: false,
  });
  const scenariosQ = useQuery({
    queryKey: ["automation-scenarios"],
    queryFn: scenarioApi.list,
  });

  const [localOrder, setLocalOrder] = useState<SuiteScenarioRef[] | null>(null);
  const items = localOrder ?? suiteQ.data?.scenarios ?? [];

  function applySuite(view: SuiteView) {
    qc.setQueryData(["automation-suite", suiteId], view);
    setLocalOrder(null);
    onMutated();
  }

  const addScenario = useMutation({
    mutationFn: (scenarioId: number) => suiteApi.addScenario(suiteId, scenarioId),
    onSuccess: applySuite,
  });
  const removeScenario = useMutation({
    mutationFn: (scenarioId: number) => suiteApi.removeScenario(suiteId, scenarioId),
    onSuccess: applySuite,
  });
  const reorder = useMutation({
    mutationFn: (ids: number[]) => suiteApi.reorderScenarios(suiteId, ids),
    onSuccess: applySuite,
    onError: () => setLocalOrder(null),
  });
  const update = useMutation({
    mutationFn: (b: SuiteUpdate) => suiteApi.update(suiteId, b),
    onSuccess: applySuite,
  });
  const remove = useMutation({
    mutationFn: () => suiteApi.delete(suiteId),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["automation-workspace-tree"] }); onAfterDelete(); },
  });

  const [editingMeta, setEditingMeta] = useState(false);
  useEffect(() => { setLocalOrder(null); setEditingMeta(false); }, [suiteId]);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  function onDragEnd(e: DragEndEvent) {
    const { active, over } = e;
    if (!over || active.id === over.id) return;
    const oldIdx = items.findIndex((r) => r.scenarioId === active.id);
    const newIdx = items.findIndex((r) => r.scenarioId === over.id);
    if (oldIdx < 0 || newIdx < 0) return;
    const next = arrayMove(items, oldIdx, newIdx).map((r, i) => ({ ...r, orderIndex: i }));
    setLocalOrder(next);
    reorder.mutate(next.map((r) => r.scenarioId));
  }

  const linkedIds = useMemo(() => new Set(items.map((r) => r.scenarioId)), [items]);
  const addable = useMemo<ComboOption[]>(
    () => (scenariosQ.data ?? [])
      .filter((s) => !linkedIds.has(s.id))
      .map((s) => ({
        value: String(s.id),
        label: s.name,
        hint: s.description ?? `${s.stepCount} steps`,
        badge: s.tags[0],
      })),
    [scenariosQ.data, linkedIds],
  );

  if (suiteQ.isLoading) return <div className="p-6 text-ink-muted flex items-center gap-2 text-sm"><Spinner /> Loading suite…</div>;
  if (suiteQ.error || !suiteQ.data) return <div className="p-6 text-danger-500">Suite not found</div>;

  const suite = suiteQ.data;
  const totalSteps = items.reduce((n, s) => n + s.stepCount, 0);

  return (
    <div className="space-y-4">
      <Card className="p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2 text-warning-500">
              <FolderKanban size={14} />
              <span className="text-[10px] uppercase tracking-wider font-semibold">Suite</span>
            </div>
            <div className="text-lg font-semibold truncate mt-0.5">{suite.name}</div>
            {suite.description && <div className="text-xs text-ink-muted mt-1">{suite.description}</div>}
            <div className="flex items-center gap-2 mt-2">
              {suite.tags.map((t) => (
                <span key={t} className="text-[10px] uppercase tracking-wider rounded px-1.5 py-0.5 border border-surface-border text-ink-secondary">{t}</span>
              ))}
              <span className="text-[10px] text-ink-muted font-mono">{items.length} scenarios · {totalSteps} steps</span>
            </div>
          </div>
          <div className="flex items-center gap-1">
            <button onClick={() => setEditingMeta(true)} className="p-1.5 rounded text-ink-muted hover:text-ink-primary hover:bg-surface-muted" title="Edit suite">
              <Pencil size={13} />
            </button>
            <button
              onClick={() => { if (confirm(`Delete suite "${suite.name}"?`)) remove.mutate(); }}
              className="p-1.5 rounded text-ink-muted hover:text-danger-500 hover:bg-surface-muted"
              title="Delete suite"
            >
              <Trash2 size={13} />
            </button>
          </div>
        </div>
      </Card>

      <Card className="p-4">
        <div className="flex items-end gap-2">
          <div className="flex-1 min-w-0">
            <span className="label block mb-1.5">Add scenario to this suite</span>
            <Combobox
              options={addable}
              value={null}
              onChange={(v) => v && addScenario.mutate(Number(v))}
              placeholder={addable.length ? "Pick a scenario…" : "All scenarios are already in this suite"}
              emptyText="No scenarios available"
              disabled={addable.length === 0 || addScenario.isPending}
              clearable={false}
            />
          </div>
        </div>
      </Card>

      {items.length === 0 ? (
        <Card>
          <EmptyState
            icon={<FileCheck2 size={20} />}
            title="No scenarios in this suite yet"
            description="Pick from existing scenarios above, or create new ones from the sidebar."
          />
        </Card>
      ) : (
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}
          modifiers={[restrictToVerticalAxis, restrictToParentElement]}>
          <SortableContext items={items.map((r) => r.scenarioId)} strategy={verticalListSortingStrategy}>
            <div className="space-y-2">
              {items.map((row) => (
                <SuiteScenarioRow
                  key={row.scenarioId}
                  row={row}
                  onOpen={() => onSelectScenario(row.scenarioId, suiteId)}
                  onRemove={() => removeScenario.mutate(row.scenarioId)}
                />
              ))}
            </div>
          </SortableContext>
        </DndContext>
      )}

      {editingMeta && (
        <SuiteMetaEditor
          initial={{ ...suite, scenarioCount: items.length }}
          busy={update.isPending}
          onClose={() => setEditingMeta(false)}
          onSubmit={(b) => update.mutate(b, { onSuccess: () => setEditingMeta(false) })}
        />
      )}
    </div>
  );
}

function SuiteScenarioRow({
  row, onOpen, onRemove,
}: { row: SuiteScenarioRef; onOpen: () => void; onRemove: () => void }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: row.scenarioId });
  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };
  return (
    <div ref={setNodeRef} style={style}
      className="group flex items-stretch min-h-[44px] rounded-md border border-surface-border bg-surface hover:border-brand-500/40 transition-colors">
      <button {...attributes} {...listeners}
        className="px-2 flex items-center text-ink-muted hover:text-ink-primary cursor-grab active:cursor-grabbing">
        <GripVertical size={14} />
      </button>
      <div className="w-7 shrink-0 flex items-center justify-center text-[11px] font-mono text-ink-muted border-r border-surface-border">
        {row.orderIndex + 1}
      </div>
      <button onClick={onOpen} className="flex-1 min-w-0 px-3 py-2 flex items-center gap-3 text-left">
        <FileCheck2 size={12} className="text-brand-300 shrink-0" />
        <span className="font-medium text-ink-primary hover:text-brand-300 truncate">{row.name}</span>
        {row.description && (
          <span className="text-xs text-ink-muted truncate min-w-0">— {row.description}</span>
        )}
        <div className="ml-auto flex items-center gap-2 shrink-0">
          {row.tags.slice(0, 2).map((t) => (
            <span key={t} className="text-[10px] uppercase tracking-wider rounded px-1.5 py-0.5 border border-surface-border text-ink-secondary">{t}</span>
          ))}
          <span className="text-[11px] font-mono text-ink-muted">{row.stepCount} steps</span>
        </div>
      </button>
      <div className="flex items-center pr-1.5 opacity-0 group-hover:opacity-100 transition-opacity">
        <button onClick={onRemove} className="p-1.5 rounded text-ink-muted hover:text-danger-500 hover:bg-surface-muted" title="Remove from suite">
          <Trash2 size={12} />
        </button>
      </div>
    </div>
  );
}

function SuiteMetaEditor({
  initial, busy, onClose, onSubmit,
}: {
  initial: SuiteSummary | SuiteView;
  busy?: boolean;
  onClose: () => void;
  onSubmit: (body: SuiteUpdate) => void;
}) {
  const [name, setName] = useState(initial.name);
  const [description, setDescription] = useState(initial.description ?? "");
  const [tagsInput, setTagsInput] = useState((initial.tags ?? []).join(", "));

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-lg">
        <div className="px-5 py-4 border-b border-surface-border">
          <div className="text-sm font-semibold">Edit suite</div>
        </div>
        <div className="p-5 space-y-4">
          <label className="block">
            <span className="label block mb-1.5">Name</span>
            <input autoFocus value={name} onChange={(e) => setName(e.target.value)} className="input" />
          </label>
          <label className="block">
            <span className="label block mb-1.5">Description</span>
            <textarea value={description ?? ""} onChange={(e) => setDescription(e.target.value)}
              rows={3} className="input resize-y" />
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
              description: description?.trim() || null,
              tags: tagsInput.split(",").map((t) => t.trim()).filter(Boolean),
            })}
          >Save</Button>
        </div>
      </Card>
    </div>
  );
}

