import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronRight, FolderOpen, FolderPlus, Plus } from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { StatusBadge } from "@/components/ui/StatusBadge";
import NewProjectDialog from "@/components/NewProjectDialog";
import { Section, SettingsLayout } from "@/components/settings/SettingsLayout";
import { projectApi, type ProjectView } from "@/lib/tenancy";
import { useActiveCompany, useAuthStore } from "@/store/auth";
import { useMemo } from "react";

/**
 * /settings/projects — lightweight roster. The detail / edit / archive flows
 * live in {@link ProjectDetailPage}, which we link to from each row. Keeping
 * this page focused on "spawn new" + navigation makes the IA cleaner.
 */
export default function ProjectsSettingsPage() {
  const company = useActiveCompany();
  const reloadMemberships = useAuthStore((s) => s.reloadMemberships);
  const qc = useQueryClient();
  const [creating, setCreating] = useState(false);

  const projectsQ = useQuery({
    queryKey: ["company-projects", company?.id ?? null],
    queryFn: () => projectApi.list(company!.id),
    enabled: company != null,
    refetchOnWindowFocus: false,
  });

  if (!company) {
    return (
      <>
        <TopBar crumbs={[{ label: "Settings" }, { label: "Projects" }]} />
        <div className="p-6 text-ink-muted text-sm">Select a company to manage its projects.</div>
      </>
    );
  }

  function refresh() {
    qc.invalidateQueries({ queryKey: ["company-projects", company!.id] });
    reloadMemberships();
  }

  // Non-owners only see the projects they hold QA_MANAGER on; that's the
  // editable subset for them. TESTER-only projects belong on the automation
  // pages, not in a settings roster.
  const isOwner = company.owner;
  const managerProjectIds = useMemo(
    () => new Set(company.projects.filter((p) => p.role === "QA_MANAGER").map((p) => p.id)),
    [company.projects],
  );

  const projectsRaw = projectsQ.data ?? [];
  const projects = isOwner ? projectsRaw : projectsRaw.filter((p) => managerProjectIds.has(p.id));
  const active   = projects.filter((p) => p.archivedAt == null);
  const archived = projects.filter((p) => p.archivedAt != null);

  return (
    <>
      <TopBar
        crumbs={[
          { label: company.name },
          { label: "Settings" },
          { label: "Projects" },
        ]}
      />

      <SettingsLayout>
        <Section
          id="active"
          title="Active projects"
          description={`${active.length} active${archived.length ? ` · ${archived.length} archived` : ""} in ${company.name}. Pick a project to edit its members, description, or archive it.`}
          action={isOwner && (
            <Button variant="primary" size="sm" leftIcon={<Plus size={14} />} onClick={() => setCreating(true)}>
              New project
            </Button>
          )}
        >
          {projectsQ.isLoading ? (
            <div className="text-ink-muted text-xs flex items-center gap-2"><Spinner /> Loading…</div>
          ) : active.length === 0 ? (
            <Card className="border-dashed">
              <EmptyState
                icon={<FolderOpen size={18} />}
                title={isOwner ? "No projects yet" : "No projects you manage"}
                description={isOwner
                  ? "Create your first project to scope automation, devices, and members."
                  : "You're not a QA manager on any active project. Ask an owner to grant access."}
                action={isOwner ? (
                  <Button variant="primary" size="sm" leftIcon={<FolderPlus size={12} />}
                          onClick={() => setCreating(true)}>
                    Create project
                  </Button>
                ) : undefined}
              />
            </Card>
          ) : (
            <ProjectList projects={active} />
          )}
        </Section>

        {archived.length > 0 && (
          <Section
            id="archived"
            title="Archived projects"
            description="Hidden from pickers everywhere; data is preserved."
          >
            <ProjectList projects={archived} archived />
          </Section>
        )}
      </SettingsLayout>

      {creating && (
        <NewProjectDialog
          companyId={company.id}
          companyName={company.name}
          onClose={() => { setCreating(false); refresh(); }}
        />
      )}
    </>
  );
}

function ProjectList({ projects, archived }: { projects: ProjectView[]; archived?: boolean }) {
  return (
    <ul className="divide-y divide-surface-border rounded-md border border-surface-border bg-surface overflow-hidden">
      {projects.map((p) => (
        <li key={p.id}>
          <Link to={`/settings/projects/${p.id}`}
                className="flex items-center gap-3 px-4 py-3 hover:bg-surface-muted/40 transition-colors">
            <div className="h-9 w-9 rounded-md bg-surface-raised border border-surface-border flex items-center justify-center shrink-0">
              <FolderOpen size={14} className="text-brand-400" />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium truncate">{p.name}</span>
                <span className="text-[10px] font-mono uppercase text-ink-muted">{p.slug}</span>
                {archived && <StatusBadge tone="neutral">Archived</StatusBadge>}
              </div>
              {p.description && (
                <div className="text-[11px] text-ink-secondary mt-0.5 line-clamp-1">{p.description}</div>
              )}
            </div>
            <ChevronRight size={14} className="text-ink-muted shrink-0" />
          </Link>
        </li>
      ))}
    </ul>
  );
}
