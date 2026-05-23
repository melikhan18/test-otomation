import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Archive, ArchiveRestore, Building2, ChevronRight, Filter, Plus, Save, Search, Settings, X,
} from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { SkeletonTable } from "@/components/ui/Skeleton";
import { StatusBadge } from "@/components/ui/StatusBadge";
import NewCompanyDialog from "@/components/NewCompanyDialog";
import { Section, SettingsLayout, FormRow } from "@/components/settings/SettingsLayout";
import { companyApi, type CompanyView } from "@/lib/tenancy";
import { useAuthStore } from "@/store/auth";
import { cn } from "@/lib/cn";
import { toast } from "@/components/toast/toastStore";
import { confirm as confirmDialog } from "@/components/confirm/confirmStore";

/**
 * /admin/companies — platform-wide tenant roster. Each row shows headline counts
 * plus actions: rename in-place, archive / unarchive, or "Manage" which switches
 * the active company in the store and jumps to /settings/company so the admin
 * can drill into projects, members, devices for that tenant.
 *
 * Platform admin only — gated by RequirePlatformAdmin upstream.
 */
export default function AdminCompaniesPage() {
  const qc = useQueryClient();
  const setActiveCompany = useAuthStore((s) => s.setActiveCompany);
  const reloadMemberships = useAuthStore((s) => s.reloadMemberships);
  const nav = useNavigate();

  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState<"all" | "active" | "archived">("all");
  const [creating, setCreating] = useState(false);
  const [renaming, setRenaming] = useState<CompanyView | null>(null);

  const companiesQ = useQuery({
    queryKey: ["admin-companies-all"],
    queryFn: companyApi.adminListAll,
    refetchOnWindowFocus: false,
  });

  const all = companiesQ.data ?? [];
  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return all.filter((c) => {
      const archived = c.archivedAt != null;
      if (filter === "active" && archived) return false;
      if (filter === "archived" && !archived) return false;
      if (!q) return true;
      return c.name.toLowerCase().includes(q) || c.slug.toLowerCase().includes(q);
    });
  }, [all, search, filter]);

  function refresh() {
    qc.invalidateQueries({ queryKey: ["admin-companies-all"] });
    reloadMemberships();
  }

  async function manage(c: CompanyView) {
    // Make sure the company is in the user's local memberships list before we
    // try to set it as active — relevant when a new tenant just appeared.
    await reloadMemberships();
    setActiveCompany(c.id);
    nav("/settings/company");
  }

  const archive = useMutation({
    mutationFn: (id: number) => companyApi.archive(id),
    onSuccess: (_data, id) => {
      const name = all.find((c) => c.id === id)?.name ?? "Company";
      toast.success(`${name} archived`);
      refresh();
    },
    onError: (e: any) => toast.error(e?.response?.data?.detail ?? "Couldn't archive company"),
  });
  const unarchive = useMutation({
    mutationFn: (id: number) => companyApi.unarchive(id),
    onSuccess: (data) => {
      toast.success(`${data.name} restored`);
      refresh();
    },
    onError: (e: any) => toast.error(e?.response?.data?.detail ?? "Couldn't restore company"),
  });

  return (
    <>
      <TopBar crumbs={[{ label: "Platform" }, { label: "Companies" }]} />

      <SettingsLayout width="wide">
        <Section
          id="filters"
          title="Filters"
          description="Search every tenant on the platform. Filter by status to find orphans or archived workspaces."
          action={(
            <Button variant="primary" size="sm" leftIcon={<Plus size={12} />} onClick={() => setCreating(true)}>
              New company
            </Button>
          )}
        >
          <div className="flex flex-col md:flex-row md:items-center gap-3">
            <div className="relative flex-1">
              <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-muted" />
              <input value={search} onChange={(e) => setSearch(e.target.value)}
                     placeholder="Search by name or slug…"
                     className="input pl-9 pr-9" />
              {search && (
                <button onClick={() => setSearch("")}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-muted hover:text-ink-primary">
                  <X size={12} />
                </button>
              )}
            </div>
            <div className="flex items-center gap-1 rounded-md border border-surface-border bg-surface p-1">
              {(["all", "active", "archived"] as const).map((f) => (
                <button key={f} onClick={() => setFilter(f)}
                        className={cn(
                          "px-3 py-1 rounded text-xs font-medium capitalize transition-colors",
                          filter === f ? "bg-surface-muted text-ink-primary"
                                        : "text-ink-secondary hover:text-ink-primary",
                        )}>
                  {f}
                </button>
              ))}
            </div>
          </div>
        </Section>

        <Section
          id="companies"
          title="Companies"
          description={`${all.length} total · ${all.filter((c) => c.archivedAt == null).length} active · ${filtered.length} shown`}
        >
          {companiesQ.isLoading ? (
            <SkeletonTable rows={5} />
          ) : all.length === 0 ? (
            <Card className="border-dashed">
              <EmptyState
                icon={<Building2 size={18} />}
                title="No companies yet"
                description="Spin up the first one to start onboarding tenants."
                action={(
                  <Button variant="primary" size="sm" leftIcon={<Plus size={12} />}
                          onClick={() => setCreating(true)}>
                    Create company
                  </Button>
                )}
              />
            </Card>
          ) : filtered.length === 0 ? (
            <Card className="border-dashed">
              <EmptyState
                icon={<Filter size={18} />}
                title="No matches"
                description="Adjust search or status filter."
              />
            </Card>
          ) : (
            <div className="overflow-x-auto rounded-md border border-surface-border bg-surface">
              <table className="w-full text-sm">
                <thead className="text-[10px] uppercase tracking-wider text-ink-muted bg-surface-muted/40">
                  <tr>
                    <th className="px-4 py-3 text-left font-semibold w-[28%]">Company</th>
                    <th className="px-4 py-3 text-left font-semibold w-[10%]">Members</th>
                    <th className="px-4 py-3 text-left font-semibold w-[10%]">Projects</th>
                    <th className="px-4 py-3 text-left font-semibold w-[12%]">Created</th>
                    <th className="px-4 py-3 text-left font-semibold w-[10%]">Status</th>
                    <th className="px-4 py-3 text-right font-semibold w-[30%]">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((c) => (
                    <CompanyRow
                      key={c.id}
                      c={c}
                      busy={archive.isPending || unarchive.isPending}
                      onManage={() => manage(c)}
                      onRename={() => setRenaming(c)}
                      onArchive={async () => {
                        const ok = await confirmDialog({
                          title: `Archive ${c.name}?`,
                          description: "Members lose access until you restore the company. Data is preserved.",
                          confirmLabel: "Archive",
                          danger: true,
                        });
                        if (ok) archive.mutate(c.id);
                      }}
                      onUnarchive={() => unarchive.mutate(c.id)}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Section>
      </SettingsLayout>

      {creating && (
        <NewCompanyDialog onClose={() => { setCreating(false); refresh(); }} />
      )}
      {renaming && (
        <RenameDialog
          company={renaming}
          onClose={() => setRenaming(null)}
          onSaved={() => { setRenaming(null); refresh(); }}
        />
      )}
    </>
  );
}

/* ───────────────────────────  Row  ─────────────────────────── */

function CompanyRow({
  c, busy, onManage, onRename, onArchive, onUnarchive,
}: {
  c: CompanyView;
  busy: boolean;
  onManage: () => void;
  onRename: () => void;
  onArchive: () => void;
  onUnarchive: () => void;
}) {
  const archived = c.archivedAt != null;
  return (
    <tr className="border-t border-surface-border hover:bg-surface-muted/30">
      <td className="px-4 py-2.5">
        <div className="flex items-center gap-2.5 min-w-0">
          <div className="h-8 w-8 rounded-md bg-surface-raised border border-surface-border flex items-center justify-center shrink-0">
            <Building2 size={13} className="text-brand-400" />
          </div>
          <div className="min-w-0">
            <div className="text-sm font-medium truncate">{c.name}</div>
            <div className="text-[10px] font-mono uppercase text-ink-muted">{c.slug}</div>
          </div>
        </div>
      </td>
      <td className="px-3 py-2.5 text-xs">{c.memberCount}</td>
      <td className="px-3 py-2.5 text-xs">{c.projectCount}</td>
      <td className="px-3 py-2.5 text-xs text-ink-muted">{new Date(c.createdAt).toLocaleDateString()}</td>
      <td className="px-3 py-2.5">
        {archived ? <StatusBadge tone="neutral">Archived</StatusBadge>
                  : <StatusBadge tone="success">Active</StatusBadge>}
      </td>
      <td className="px-3 py-2.5 text-right">
        <div className="inline-flex items-center gap-1">
          {!archived && (
            <button onClick={onManage}
                    className="inline-flex items-center gap-1 px-2 h-7 rounded border border-surface-border text-[11px] text-ink-secondary hover:text-ink-primary hover:border-brand-500/30"
                    title="Switch to this company and open its settings">
              <Settings size={11} /> Manage <ChevronRight size={10} />
            </button>
          )}
          {!archived && (
            <button onClick={onRename}
                    disabled={busy}
                    className="text-[10px] uppercase tracking-wider text-ink-muted hover:text-ink-primary px-2 h-7 rounded hover:bg-surface-muted disabled:opacity-50"
                    title="Rename">
              Rename
            </button>
          )}
          {archived ? (
            <button onClick={onUnarchive}
                    disabled={busy}
                    className="inline-flex items-center gap-1 text-[10px] uppercase tracking-wider text-success-500 hover:text-success-500 px-2 h-7 rounded hover:bg-success-500/10 disabled:opacity-50"
                    title="Restore">
              <ArchiveRestore size={11} /> Unarchive
            </button>
          ) : (
            <button onClick={onArchive}
                    disabled={busy}
                    className="inline-flex items-center gap-1 text-[10px] uppercase tracking-wider text-danger-500 hover:text-danger-500 px-2 h-7 rounded hover:bg-danger-500/10 disabled:opacity-50"
                    title="Archive">
              <Archive size={11} /> Archive
            </button>
          )}
        </div>
      </td>
    </tr>
  );
}

/* ───────────────────────────  Rename dialog  ─────────────────────── */

function RenameDialog({
  company, onClose, onSaved,
}: { company: CompanyView; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(company.name);
  const save = useMutation({
    mutationFn: () => companyApi.update(company.id, { name: name.trim() }),
    onSuccess: onSaved,
  });
  const err = (save.error as any)?.response?.data?.detail
           ?? (save.error as any)?.response?.data?.message;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-md">
        <div className="px-5 py-4 border-b border-surface-border flex items-start justify-between gap-3">
          <div>
            <div className="text-sm font-semibold flex items-center gap-2">
              <Building2 size={14} className="text-brand-400" />
              Rename company
            </div>
            <div className="text-[11px] text-ink-muted mt-0.5 font-mono">{company.slug}</div>
          </div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted">
            <X size={14} />
          </button>
        </div>
        <div className="p-5 space-y-4">
          <FormRow label="Name" hint="The slug stays the same to keep URLs stable.">
            <input autoFocus value={name} onChange={(e) => setName(e.target.value)}
                   maxLength={128} className="input" />
          </FormRow>
          {err && (
            <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">
              {String(err)}
            </div>
          )}
        </div>
        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="primary" leftIcon={<Save size={12} />}
                  loading={save.isPending}
                  disabled={name.trim().length === 0 || name.trim() === company.name}
                  onClick={() => save.mutate()}>
            Save
          </Button>
        </div>
      </Card>
    </div>
  );
}
