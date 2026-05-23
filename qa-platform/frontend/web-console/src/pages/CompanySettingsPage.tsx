import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ChevronRight, FolderOpen, FolderPlus, Plus, Save, Trash2,
} from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { StatusBadge } from "@/components/ui/StatusBadge";
import NewProjectDialog from "@/components/NewProjectDialog";
import { FormRow, Section, SettingsLayout } from "@/components/settings/SettingsLayout";
import { companyApi, projectApi, type ProjectView } from "@/lib/tenancy";
import { useActiveCompany, useAuthStore } from "@/store/auth";

/**
 * /settings/company — single page that pulls the three top-level company
 * concerns together: rename, list of projects (with shortcuts into each
 * project's own detail page), and the archive-company danger zone.
 *
 * Projects rendered inline are deliberately read-only at this layer — the
 * editing UX lives on {@link ProjectDetailPage} so we keep one place per
 * responsibility.
 */
export default function CompanySettingsPage() {
  const company = useActiveCompany();
  const reloadMemberships = useAuthStore((s) => s.reloadMemberships);
  const nav = useNavigate();

  if (!company) {
    return (
      <>
        <TopBar crumbs={[{ label: "Settings" }, { label: "Company" }]} />
        <div className="p-6 text-ink-muted text-sm">Select a company to edit its settings.</div>
      </>
    );
  }

  return (
    <>
      <TopBar crumbs={[{ label: company.name }, { label: "Settings" }, { label: "Company" }]} />

      <SettingsLayout>
        <GeneralSection company={company} onSaved={reloadMemberships} />
        <ProjectsSection companyId={company.id} companyName={company.name} />
        <DangerSection companyId={company.id} companyName={company.name}
                        onArchived={async () => { await reloadMemberships(); nav("/"); }} />
      </SettingsLayout>
    </>
  );
}

/* ─────────────────────────  General  ──────────────────────────── */

function GeneralSection({ company, onSaved }: { company: { id: number; name: string; slug: string }; onSaved: () => void | Promise<void> }) {
  const [name, setName] = useState(company.name);
  useEffect(() => { setName(company.name); }, [company.id, company.name]);

  const rename = useMutation({
    mutationFn: () => companyApi.update(company.id, { name: name.trim() }),
    onSuccess: () => onSaved(),
  });
  const dirty = name.trim() !== company.name && name.trim().length > 0;
  const err = (rename.error as any)?.response?.data?.detail
           ?? (rename.error as any)?.response?.data?.message;

  return (
    <Section
      id="general"
      title="General"
      description="Display name and slug appear in the workspace switcher, breadcrumbs, and reports."
      action={(
        <Button variant="primary" size="sm" leftIcon={<Save size={12} />}
                disabled={!dirty}
                loading={rename.isPending}
                onClick={() => rename.mutate()}>
          Save
        </Button>
      )}
    >
      <FormRow label="Name" hint="OWNERs can change this anytime.">
        <input value={name} onChange={(e) => setName(e.target.value)}
               maxLength={128} placeholder="e.g. Acme Corp" className="input" />
      </FormRow>

      <FormRow label="Slug" hint="Used internally in URLs and headers. Immutable.">
        <input value={company.slug} readOnly className="input font-mono opacity-60" />
      </FormRow>

      {err && <ErrBox text={String(err)} />}
    </Section>
  );
}

/* ─────────────────────────  Projects  ─────────────────────────── */

function ProjectsSection({ companyId, companyName }: { companyId: number; companyName: string }) {
  const qc = useQueryClient();
  const [creating, setCreating] = useState(false);

  const projectsQ = useQuery({
    queryKey: ["company-projects", companyId],
    queryFn: () => projectApi.list(companyId),
    refetchOnWindowFocus: false,
  });

  const projects = projectsQ.data ?? [];
  const active   = projects.filter((p) => p.archivedAt == null);
  const archived = projects.filter((p) => p.archivedAt != null);

  return (
    <Section
      id="projects"
      title="Projects"
      description="Scopes for your test work. Pick a project to edit its name/description, manage members, or archive it."
      action={(
        <div className="flex items-center gap-2">
          <Link to="/settings/projects"
                className="text-[11px] text-ink-muted hover:text-ink-primary">
            Full list →
          </Link>
          <Button variant="primary" size="sm" leftIcon={<Plus size={12} />}
                  onClick={() => setCreating(true)}>
            New
          </Button>
        </div>
      )}
    >
      {projectsQ.isLoading ? (
        <div className="text-ink-muted text-xs flex items-center gap-2"><Spinner /> Loading…</div>
      ) : active.length === 0 ? (
        <Card className="border-dashed">
          <EmptyState
            icon={<FolderOpen size={18} />}
            title="No projects yet"
            description="Create your first project to start scoping automation, devices and members."
            action={(
              <Button variant="primary" size="sm" leftIcon={<FolderPlus size={12} />}
                      onClick={() => setCreating(true)}>
                Create project
              </Button>
            )}
          />
        </Card>
      ) : (
        <ul className="divide-y divide-surface-border rounded-md border border-surface-border bg-surface overflow-hidden">
          {active.map((p) => <ProjectRow key={p.id} project={p} />)}
        </ul>
      )}

      {archived.length > 0 && (
        <details className="text-xs">
          <summary className="cursor-pointer text-ink-muted hover:text-ink-secondary">
            Show {archived.length} archived
          </summary>
          <ul className="mt-2 divide-y divide-surface-border rounded-md border border-surface-border bg-surface overflow-hidden">
            {archived.map((p) => <ProjectRow key={p.id} project={p} archived />)}
          </ul>
        </details>
      )}

      {creating && (
        <NewProjectDialog
          companyId={companyId}
          companyName={companyName}
          onClose={() => {
            setCreating(false);
            qc.invalidateQueries({ queryKey: ["company-projects", companyId] });
          }}
        />
      )}
    </Section>
  );
}

function ProjectRow({ project, archived }: { project: ProjectView; archived?: boolean }) {
  return (
    <li>
      <Link to={`/settings/projects/${project.id}`}
            className="flex items-center gap-3 px-4 py-2.5 hover:bg-surface-muted/40 transition-colors">
        <div className="h-8 w-8 rounded-md bg-surface-raised border border-surface-border flex items-center justify-center shrink-0">
          <FolderOpen size={13} className="text-brand-400" />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium truncate">{project.name}</span>
            <span className="text-[10px] font-mono uppercase text-ink-muted">{project.slug}</span>
            {archived && <StatusBadge tone="neutral">Archived</StatusBadge>}
          </div>
          {project.description && (
            <div className="text-[11px] text-ink-secondary mt-0.5 line-clamp-1">{project.description}</div>
          )}
        </div>
        <ChevronRight size={13} className="text-ink-muted shrink-0" />
      </Link>
    </li>
  );
}

/* ─────────────────────────  Danger zone  ──────────────────────── */

function DangerSection({
  companyId, companyName, onArchived,
}: { companyId: number; companyName: string; onArchived: () => void | Promise<void> }) {
  const [confirmText, setConfirmText] = useState("");
  const archive = useMutation({
    mutationFn: () => companyApi.archive(companyId),
    onSuccess: () => onArchived(),
  });
  const canArchive = confirmText === companyName;
  const err = (archive.error as any)?.response?.data?.detail
           ?? (archive.error as any)?.response?.data?.message;

  return (
    <Section
      id="danger"
      tone="danger"
      title="Danger zone"
      description="Archiving hides the company from every member's switcher and cascades to every project inside it. Data is preserved, but reactivation is a manual operation."
    >
      <FormRow label="Confirm" hint={`Type "${companyName}" to enable the archive button.`}>
        <input value={confirmText} onChange={(e) => setConfirmText(e.target.value)}
               placeholder={companyName} className="input font-mono" />
      </FormRow>

      {err && <ErrBox text={String(err)} />}

      <div className="flex justify-end">
        <Button variant="danger" size="sm" leftIcon={<Trash2 size={12} />}
                disabled={!canArchive}
                loading={archive.isPending}
                onClick={() => archive.mutate()}>
          Archive company
        </Button>
      </div>
    </Section>
  );
}

/* ─────────────────────────  helper  ───────────────────────────── */

function ErrBox({ text }: { text: string }) {
  return (
    <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">
      {text}
    </div>
  );
}
