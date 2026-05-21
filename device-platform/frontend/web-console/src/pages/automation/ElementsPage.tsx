import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Image as ImageIcon, Pencil, Plus, Search, Trash2, X } from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { LocatorBadge } from "@/components/automation/LocatorBadge";
import ElementEditor from "@/components/automation/ElementEditor";
import {
  elementApi, type ElementCreate, type ElementUpdate, type ElementView,
} from "@/lib/automation";
import { useAuthStore } from "@/store/auth";

export default function ElementsPage() {
  const qc = useQueryClient();
  const activeCompanyId = useAuthStore((s) => s.activeCompanyId);
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<ElementView | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<ElementView | null>(null);

  const elementsQ = useQuery({
    queryKey: ["automation-elements", activeCompanyId ?? null, search],
    queryFn: () => elementApi.list(search || undefined),
    refetchOnWindowFocus: false,
    enabled: activeCompanyId != null,
  });

  const create = useMutation({
    mutationFn: (body: ElementCreate) => elementApi.create(body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["automation-elements"] }); setCreating(false); setError(null); },
    onError: (e: any) => setError(e?.response?.data?.detail ?? "create failed"),
  });
  const update = useMutation({
    mutationFn: ({ id, body }: { id: number; body: ElementUpdate }) => elementApi.update(id, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["automation-elements"] }); setEditing(null); setError(null); },
    onError: (e: any) => setError(e?.response?.data?.detail ?? "update failed"),
  });
  const remove = useMutation({
    mutationFn: (id: number) => elementApi.delete(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["automation-elements"] }); setConfirmDelete(null); },
  });

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
        crumbs={[{ label: "Automation", to: "/automation/elements" }, { label: "Elements" }]}
        actions={
          <Button variant="primary" size="sm" leftIcon={<Plus size={14} />} onClick={() => { setCreating(true); setError(null); }}>
            New element
          </Button>
        }
      />

      <div className="px-6 py-6 space-y-6">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Stat label="Elements" value={stats.total} />
          <Stat label="With fallback" value={stats.withFallbacks} />
          <Stat label="resource-id"      value={stats.byStrategy["RESOURCE_ID"] ?? 0} />
          <Stat label="xpath only"       value={stats.byStrategy["XPATH"] ?? 0} />
        </div>

        <Card className="px-4 py-3">
          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-muted" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by name, locator value, resource-id or text…"
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
        ) : (elementsQ.data ?? []).length === 0 ? (
          <Card>
            <EmptyState
              icon={<ImageIcon size={20} />}
              title={search ? "No elements match" : "No elements yet"}
              description={search
                ? "Try a different keyword."
                : "Start a device session, open the Inspector, select a node and click \"Save as element\". You can also create one manually."}
              action={
                <Button variant="primary" size="sm" leftIcon={<Plus size={14} />} onClick={() => { setCreating(true); setError(null); }}>
                  Create manually
                </Button>
              }
            />
          </Card>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {(elementsQ.data ?? []).map((el) => (
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

      {creating && (
        <ElementEditor
          mode="create"
          busy={create.isPending}
          error={error}
          onClose={() => setCreating(false)}
          onSubmit={(body) => create.mutate(body as ElementCreate)}
        />
      )}

      {editing && (
        <ElementEditor
          mode="edit"
          initial={editing}
          busy={update.isPending}
          error={error}
          onClose={() => setEditing(null)}
          onSubmit={(body) => update.mutate({ id: editing.id, body: body as ElementUpdate })}
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

function ElementCard({
  el, onEdit, onDelete,
}: { el: ElementView; onEdit: () => void; onDelete: () => void }) {
  return (
    <Card className="p-4 flex flex-col gap-3 hover:border-brand-500/40 transition-colors group">
      <div className="flex items-start gap-3">
        <div className="w-16 h-16 rounded-md border border-surface-border bg-black overflow-hidden flex items-center justify-center shrink-0">
          {el.screenshotData
            ? <img src={el.screenshotData} alt={el.name} className="w-full h-full object-contain" />
            : <ImageIcon size={18} className="text-ink-muted" />
          }
        </div>
        <div className="min-w-0 flex-1">
          <div className="font-mono text-sm font-medium text-ink-primary truncate" title={el.name}>{el.name}</div>
          {el.description && <div className="text-[11px] text-ink-muted truncate" title={el.description}>{el.description}</div>}
          <div className="flex items-center gap-1 mt-1 flex-wrap">
            <LocatorBadge strategy={el.primaryStrategy} />
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

function ConfirmDelete({
  name, busy, onCancel, onConfirm,
}: { name: string; busy?: boolean; onCancel: () => void; onConfirm: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-md p-5">
        <div className="text-sm font-semibold">Delete element?</div>
        <div className="text-xs text-ink-muted mt-1">
          <code className="font-mono">{name}</code> will be removed. Test steps that reference it will need to be updated.
        </div>
        <div className="flex justify-end gap-2 mt-5">
          <Button variant="secondary" onClick={onCancel}>Cancel</Button>
          <Button variant="danger" loading={busy} onClick={onConfirm}>Delete</Button>
        </div>
      </Card>
    </div>
  );
}
