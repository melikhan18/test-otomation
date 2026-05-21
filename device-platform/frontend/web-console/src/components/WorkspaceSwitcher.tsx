import { useEffect, useRef, useState } from "react";
import {
  Building2, Check, ChevronsUpDown, FolderOpen, FolderPlus, Plus,
} from "lucide-react";
import {
  useActiveCompany, useActiveProject, useAuthStore,
} from "@/store/auth";
import { cn } from "@/lib/cn";
import NewCompanyDialog from "@/components/NewCompanyDialog";
import NewProjectDialog from "@/components/NewProjectDialog";

/**
 * Sidebar widget showing the active company + project with two stacked picker
 * dropdowns. Switching company resets the project to the first one in the new
 * company (handled by the store). Each picker has a "+ New …" footer action
 * for creating fresh workspaces inline. Hidden when the user has 0 companies.
 */
export default function WorkspaceSwitcher() {
  const companies = useAuthStore((s) => s.companies);
  const activeCompany = useActiveCompany();
  const activeProject = useActiveProject();
  const setActiveCompany = useAuthStore((s) => s.setActiveCompany);
  const setActiveProject = useAuthStore((s) => s.setActiveProject);

  const [newCompanyOpen, setNewCompanyOpen] = useState(false);
  const [newProjectOpen, setNewProjectOpen] = useState(false);

  // Zero-company state: signed-in user belongs to no workspace yet. Show a
  // single "Create company" CTA so they can self-serve instead of staring at
  // an empty sidebar.
  if (companies.length === 0) {
    return (
      <div className="px-3 pt-3">
        <button
          onClick={() => setNewCompanyOpen(true)}
          className="w-full flex items-center gap-2 px-2.5 py-2 rounded-md border border-dashed border-surface-border text-left hover:border-brand-500/40 hover:bg-surface-muted/30 transition-colors"
        >
          <Plus size={13} className="text-brand-400" />
          <span className="flex-1 min-w-0">
            <span className="text-[10px] uppercase tracking-wider text-ink-muted block leading-tight">Workspace</span>
            <span className="text-sm font-medium truncate block leading-tight">Create your first company</span>
          </span>
        </button>
        {newCompanyOpen && <NewCompanyDialog onClose={() => setNewCompanyOpen(false)} />}
      </div>
    );
  }

  // Only company OWNERs can spin up new projects — QA_MANAGER is project-scoped now
  // and a project manager can't unilaterally add new projects to the company.
  const canCreateProject = !!activeCompany?.owner;

  return (
    <div className="px-3 pt-3 space-y-1.5">
      <Picker
        icon={<Building2 size={13} />}
        label="Company"
        currentValue={activeCompany?.name ?? "—"}
        currentMeta={activeCompany?.owner ? "OWNER" : "MEMBER"}
        empty={companies.length === 0}
        items={companies.map((c) => ({
          id: c.id,
          primary: c.name,
          secondary: c.owner ? "OWNER" : "MEMBER",
        }))}
        onSelect={(id) => setActiveCompany(id)}
        footer={
          <button
            onClick={() => setNewCompanyOpen(true)}
            className="w-full px-3 py-1.5 text-xs text-ink-secondary hover:bg-surface-muted/60 hover:text-ink-primary text-left flex items-center gap-2"
          >
            <Plus size={11} /> New company
          </button>
        }
      />
      <Picker
        icon={<FolderOpen size={13} />}
        label="Project"
        currentValue={activeProject?.name ?? (activeCompany ? "No project selected" : "—")}
        currentMeta={activeProject?.role}
        empty={!activeCompany}
        items={(activeCompany?.projects ?? []).map((p) => ({
          id: p.id,
          primary: p.name,
          secondary: shortRole(p.role),
        }))}
        emptyState={
          canCreateProject ? (
            <button
              onClick={() => setNewProjectOpen(true)}
              className="w-full px-3 py-1.5 text-xs text-brand-300 hover:bg-surface-muted/60 text-left flex items-center gap-2"
            >
              <FolderPlus size={11} /> Create your first project
            </button>
          ) : (
            <div className="px-3 py-1.5 text-[11px] text-ink-muted">
              No projects yet. Ask an admin to add you.
            </div>
          )
        }
        onSelect={(id) => setActiveProject(id)}
        footer={canCreateProject ? (
          <button
            onClick={() => setNewProjectOpen(true)}
            className="w-full px-3 py-1.5 text-xs text-ink-secondary hover:bg-surface-muted/60 hover:text-ink-primary text-left flex items-center gap-2"
          >
            <Plus size={11} /> New project
          </button>
        ) : null}
      />

      {newCompanyOpen && <NewCompanyDialog onClose={() => setNewCompanyOpen(false)} />}
      {newProjectOpen && activeCompany && (
        <NewProjectDialog
          companyId={activeCompany.id}
          companyName={activeCompany.name}
          onClose={() => setNewProjectOpen(false)}
        />
      )}
    </div>
  );
}

/** Compact label for the project role chip on the right of each row. */
function shortRole(role: string): string {
  if (role === "QA_MANAGER") return "QM";
  if (role === "TESTER") return "Tester";
  if (role === "OWNER") return "Owner";
  return role;
}

type PickerItem = { id: number; primary: string; secondary?: string };

function Picker({
  icon, label, currentValue, currentMeta, items, onSelect, empty, emptyState, footer,
}: {
  icon: React.ReactNode;
  label: string;
  currentValue: string;
  currentMeta?: string;
  items: PickerItem[];
  empty?: boolean;
  emptyState?: React.ReactNode;
  footer?: React.ReactNode;
  onSelect: (id: number) => void;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  // Click-outside dismiss.
  useEffect(() => {
    if (!open) return;
    function onDoc(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, [open]);

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => !empty && setOpen((v) => !v)}
        className={cn(
          "w-full flex items-center gap-2 px-2.5 py-2 rounded-md border border-surface-border text-left",
          "bg-surface hover:border-brand-500/30 transition-colors group",
          empty && "opacity-60 cursor-not-allowed",
        )}
        disabled={empty}
        title={`Switch ${label.toLowerCase()}`}
      >
        <span className="text-ink-muted">{icon}</span>
        <span className="flex-1 min-w-0">
          <span className="text-[10px] uppercase tracking-wider text-ink-muted block leading-tight">{label}</span>
          <span className="text-sm font-medium truncate block leading-tight">{currentValue}</span>
        </span>
        {!empty && <ChevronsUpDown size={12} className="text-ink-muted shrink-0" />}
      </button>
      {currentMeta && (
        <span className="absolute right-2.5 top-2 text-[9px] font-mono uppercase tracking-wider text-ink-muted">
          {currentMeta}
        </span>
      )}

      {open && (
        <div className="absolute left-0 right-0 mt-1 z-30 rounded-md border border-surface-border bg-surface-raised shadow-xl py-1 max-h-64 overflow-y-auto">
          {items.length === 0 ? (
            emptyState ?? <div className="px-3 py-2 text-[11px] text-ink-muted">No options</div>
          ) : items.map((it) => {
            const isActive = currentValue === it.primary;
            return (
              <button
                key={it.id}
                onClick={() => { onSelect(it.id); setOpen(false); }}
                className={cn(
                  "w-full text-left px-3 py-1.5 flex items-center gap-2 text-sm",
                  "hover:bg-surface-muted/60",
                  isActive ? "text-ink-primary" : "text-ink-secondary",
                )}
              >
                <span className="w-3.5 shrink-0">{isActive && <Check size={12} className="text-brand-400" />}</span>
                <span className="flex-1 min-w-0 truncate">{it.primary}</span>
                {it.secondary && (
                  <span className="text-[10px] font-mono uppercase tracking-wider text-ink-muted shrink-0">
                    {it.secondary}
                  </span>
                )}
              </button>
            );
          })}
          {footer && (
            <>
              <div className="border-t border-surface-border my-1" />
              {footer}
            </>
          )}
        </div>
      )}
    </div>
  );
}
