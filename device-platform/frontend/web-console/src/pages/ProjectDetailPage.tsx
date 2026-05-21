import { useEffect, useMemo, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft, Crown, FolderOpen, Save, ShieldCheck, Trash2, UserPlus, Wrench, X,
} from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { Section, SettingsLayout, FormRow } from "@/components/settings/SettingsLayout";
import {
  memberApi, projectApi,
  type MemberView, type ProjectMemberView, type ProjectRole, type ProjectUpdate,
} from "@/lib/tenancy";
import { useActiveCompany, useAuthStore } from "@/store/auth";
import { cn } from "@/lib/cn";

const ROLE_OPTIONS: { value: ProjectRole; label: string }[] = [
  { value: "QA_MANAGER", label: "QA Manager" },
  { value: "TESTER",     label: "Tester" },
];

/**
 * /settings/projects/:id — single project's settings panel. Three sections:
 *   - General  — name + description rename, slug shown read-only.
 *   - Members  — project-scoped roster: company OWNERs (implicit), plus
 *                QA_MANAGERs and TESTERs explicitly granted. Add/change/remove
 *                inline.
 *   - Danger   — archive (last project guard surfaces backend error).
 */
export default function ProjectDetailPage() {
  const { id } = useParams();
  const projectId = Number(id);
  const company = useActiveCompany();
  const platformAdmin = useAuthStore((s) => s.platformAdmin);
  const reloadMemberships = useAuthStore((s) => s.reloadMemberships);
  const nav = useNavigate();
  const qc = useQueryClient();

  const projectQ = useQuery({
    queryKey: ["company-project", company?.id ?? null, projectId],
    queryFn: () => projectApi.get(company!.id, projectId),
    enabled: company != null && !Number.isNaN(projectId),
    refetchOnWindowFocus: false,
  });

  if (!company) {
    return (
      <>
        <TopBar crumbs={[{ label: "Settings" }, { label: "Projects" }]} />
        <div className="p-6 text-ink-muted text-sm">Select a company first.</div>
      </>
    );
  }

  // Per-id access gate: OWNER (or platform admin) can manage every project;
  // anyone else must hold a QA_MANAGER grant on *this specific* project. A
  // TESTER-only assignment is read-via-automation, not editable from here.
  const isOwner = platformAdmin || company.owner;
  const myGrant = company.projects.find((p) => p.id === projectId);
  const canManage = isOwner || myGrant?.role === "QA_MANAGER";
  if (!canManage) {
    return <Navigate to="/" replace />;
  }
  if (projectQ.isLoading) {
    return (
      <>
        <TopBar crumbs={[{ label: company.name }, { label: "Settings" }, { label: "Projects" }]} />
        <div className="p-6 text-ink-muted text-sm flex items-center gap-2"><Spinner /> Loading…</div>
      </>
    );
  }
  if (projectQ.isError || !projectQ.data) {
    return (
      <>
        <TopBar crumbs={[{ label: company.name }, { label: "Settings" }, { label: "Projects" }]} />
        <div className="p-6">
          <Card>
            <EmptyState
              icon={<FolderOpen size={20} />}
              title="Project not found"
              description="It may have been archived or you no longer have access."
              action={
                <Button variant="secondary" size="sm" leftIcon={<ArrowLeft size={12} />}
                        onClick={() => nav("/settings/projects")}>
                  Back to projects
                </Button>
              }
            />
          </Card>
        </div>
      </>
    );
  }
  const project = projectQ.data;
  const isArchived = project.archivedAt != null;

  function refreshAll() {
    qc.invalidateQueries({ queryKey: ["company-project", company!.id, projectId] });
    qc.invalidateQueries({ queryKey: ["company-projects", company!.id] });
    qc.invalidateQueries({ queryKey: ["project-members", projectId] });
    reloadMemberships();
  }

  return (
    <>
      <TopBar
        crumbs={[
          { label: company.name },
          { label: "Settings" },
          { label: "Projects", to: "/settings/projects" },
          { label: project.name },
        ]}
        actions={(
          <Link to="/settings/projects" className="text-xs text-ink-secondary hover:text-ink-primary inline-flex items-center gap-1.5">
            <ArrowLeft size={12} /> All projects
          </Link>
        )}
      />

      <SettingsLayout>
        <GeneralSection project={project} companyId={company.id} archived={isArchived} onSaved={refreshAll} />
        <MembersSection projectId={projectId} companyId={company.id} archived={isArchived} />
        <DangerSection projectId={projectId} projectName={project.name} companyId={company.id}
                        archived={isArchived}
                        onArchived={() => { refreshAll(); nav("/settings/projects"); }} />
      </SettingsLayout>
    </>
  );
}

/* ─────────────────────────  General  ──────────────────────────── */

function GeneralSection({
  project, companyId, archived, onSaved,
}: { project: { id: number; name: string; slug: string; description: string | null };
     companyId: number; archived: boolean; onSaved: () => void }) {
  const [name, setName] = useState(project.name);
  const [description, setDescription] = useState(project.description ?? "");
  useEffect(() => { setName(project.name); setDescription(project.description ?? ""); },
            [project.id, project.name, project.description]);

  const save = useMutation({
    mutationFn: (b: ProjectUpdate) => projectApi.update(companyId, project.id, b),
    onSuccess: onSaved,
  });
  const dirty = name.trim() !== project.name || (description.trim() || "") !== (project.description ?? "");
  const err = (save.error as any)?.response?.data?.detail
           ?? (save.error as any)?.response?.data?.message;

  return (
    <Section
      id="general"
      title="General"
      description="Display name and description show up in the project picker, automation workspace, and reports."
      action={(
        <Button variant="primary" size="sm" leftIcon={<Save size={12} />}
                disabled={!dirty || archived}
                loading={save.isPending}
                onClick={() => save.mutate({ name: name.trim(), description: description.trim() || undefined })}>
          Save
        </Button>
      )}
    >
      {archived && <ArchivedBanner />}

      <FormRow label="Name" hint="Shown across the UI.">
        <input value={name} onChange={(e) => setName(e.target.value)}
               maxLength={128} className="input" disabled={archived} />
      </FormRow>

      <FormRow label="Slug" hint="Used in URLs. Cannot be changed once created.">
        <input value={project.slug} readOnly className="input font-mono opacity-60" />
      </FormRow>

      <FormRow label="Description" hint="Optional. Markdown not supported.">
        <textarea value={description} onChange={(e) => setDescription(e.target.value)}
                  rows={3} maxLength={2000}
                  className="input resize-y min-h-[5rem]" disabled={archived} />
      </FormRow>

      {err && <ErrBox text={String(err)} />}
    </Section>
  );
}

/* ─────────────────────────  Members  ──────────────────────────── */

function MembersSection({ projectId, companyId, archived }: { projectId: number; companyId: number; archived: boolean }) {
  const qc = useQueryClient();
  const currentUserId = useAuthStore((s) => s.userId);
  const [adding, setAdding] = useState(false);

  const membersQ = useQuery({
    queryKey: ["project-members", projectId],
    queryFn: () => memberApi.listProject(companyId, projectId),
    refetchOnWindowFocus: false,
  });
  const companyMembersQ = useQuery({
    queryKey: ["company-members", companyId],
    queryFn: () => memberApi.list(companyId),
    refetchOnWindowFocus: false,
    enabled: adding,   // lazy — only fetch the "add candidate" pool when the dialog opens
  });

  function refresh() {
    qc.invalidateQueries({ queryKey: ["project-members", projectId] });
  }

  const remove = useMutation({
    mutationFn: (userId: number) => memberApi.removeProject(companyId, projectId, userId),
    onSuccess: refresh,
  });
  const changeRole = useMutation({
    mutationFn: ({ userId, role }: { userId: number; role: ProjectRole }) =>
      memberApi.changeProjectRole(companyId, projectId, userId, role),
    onSuccess: refresh,
  });

  const members = membersQ.data ?? [];

  return (
    <Section
      id="members"
      title="Members"
      description="People who can work on this project. Company OWNERs always have implicit access — they appear here for visibility but can't be removed from a single project."
      action={(
        <Button variant="primary" size="sm" leftIcon={<UserPlus size={12} />}
                disabled={archived}
                onClick={() => setAdding(true)}>
          Add member
        </Button>
      )}
    >
      {membersQ.isLoading ? (
        <div className="text-ink-muted text-xs flex items-center gap-2"><Spinner /> Loading…</div>
      ) : members.length === 0 ? (
        <div className="text-[11px] text-ink-muted">No members yet.</div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-[10px] uppercase tracking-wider text-ink-muted bg-surface-muted/40">
              <tr>
                <th className="px-3 py-2 text-left font-semibold">User</th>
                <th className="px-3 py-2 text-left font-semibold">Role</th>
                <th className="px-3 py-2 text-left font-semibold">Since</th>
                <th className="px-3 py-2"></th>
              </tr>
            </thead>
            <tbody>
              {members.map((m) => (
                <MemberRow key={`${m.userId}-${m.role}`}
                            m={m}
                            archived={archived}
                            isSelf={currentUserId === m.userId}
                            onChangeRole={(role) => {
                              // Defense-in-depth: backend rejects, but we also block
                              // the click client-side so the user never sees a 403.
                              if (currentUserId === m.userId) return;
                              changeRole.mutate({ userId: m.userId, role });
                            }}
                            onRemove={() => {
                              if (currentUserId === m.userId) return;
                              if (confirm(`Remove ${m.username} from this project?`)) remove.mutate(m.userId);
                            }} />
              ))}
            </tbody>
          </table>
        </div>
      )}

      {adding && (
        <AddProjectMemberDialog
          companyId={companyId}
          projectId={projectId}
          existingUserIds={new Set(members.map((m) => m.userId))}
          companyMembers={companyMembersQ.data ?? []}
          loading={companyMembersQ.isLoading}
          onClose={() => setAdding(false)}
          onAdded={() => { setAdding(false); refresh(); }}
        />
      )}
    </Section>
  );
}

function MemberRow({
  m, archived, isSelf, onChangeRole, onRemove,
}: {
  m: ProjectMemberView;
  archived: boolean;
  /** True → this row is the signed-in user; can't change own role or remove self. */
  isSelf: boolean;
  onChangeRole: (r: ProjectRole) => void;
  onRemove: () => void;
}) {
  const isOwner = m.role === "OWNER";
  const Icon = isOwner ? Crown : m.role === "QA_MANAGER" ? ShieldCheck : Wrench;
  const iconClr = isOwner ? "text-warning-500" : m.role === "QA_MANAGER" ? "text-brand-400" : "text-ink-muted";

  return (
    <tr className="border-t border-surface-border hover:bg-surface-muted/30">
      <td className="px-3 py-2.5">
        <div className="flex items-center gap-2.5">
          <div className="h-7 w-7 rounded-full bg-brand-500/15 text-brand-300 text-xs font-semibold flex items-center justify-center">
            {m.username.slice(0, 1).toUpperCase()}
          </div>
          <span className="font-medium">{m.username}</span>
        </div>
      </td>
      <td className="px-3 py-2.5">
        {isOwner ? (
          <span className="inline-flex items-center gap-1.5 text-xs">
            <Icon size={12} className={iconClr} /> Company OWNER · implicit
          </span>
        ) : (
          <div className="flex items-center gap-2">
            <select value={m.role}
                    onChange={(e) => onChangeRole(e.target.value as ProjectRole)}
                    disabled={archived || isSelf}
                    title={isSelf ? "You can't change your own role" : undefined}
                    className="h-7 px-2 rounded border border-surface-border bg-surface text-xs font-medium focus:outline-none focus:ring-1 focus:ring-brand-500 disabled:opacity-60 disabled:cursor-not-allowed">
              {ROLE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
            {isSelf && (
              <span className="text-[10px] uppercase tracking-wider text-ink-muted">You</span>
            )}
          </div>
        )}
      </td>
      <td className="px-3 py-2.5 text-xs text-ink-muted">
        {new Date(m.addedAt).toLocaleDateString()}
      </td>
      <td className="px-3 py-2.5 text-right">
        {!isOwner && (
          <button onClick={onRemove}
                  disabled={archived || isSelf}
                  title={isSelf ? "You can't remove yourself" : "Remove from project"}
                  className="p-1.5 rounded text-ink-muted hover:text-danger-500 hover:bg-surface-muted disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:text-ink-muted">
            <Trash2 size={13} />
          </button>
        )}
      </td>
    </tr>
  );
}

function AddProjectMemberDialog({
  companyId, projectId, existingUserIds, companyMembers, loading, onClose, onAdded,
}: {
  companyId: number;
  projectId: number;
  existingUserIds: Set<number>;
  companyMembers: MemberView[];
  loading: boolean;
  onClose: () => void;
  onAdded: () => void;
}) {
  const candidates = useMemo(
    () => companyMembers.filter((m) => !m.owner && !existingUserIds.has(m.userId)),
    [companyMembers, existingUserIds],
  );
  const [userId, setUserId] = useState<number | null>(null);
  const [role, setRole] = useState<ProjectRole>("TESTER");

  const add = useMutation({
    mutationFn: () => memberApi.addProject(companyId, projectId, userId!, role),
    onSuccess: onAdded,
  });
  const err = (add.error as any)?.response?.data?.detail
           ?? (add.error as any)?.response?.data?.message;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-md">
        <div className="px-5 py-4 border-b border-surface-border flex items-start justify-between gap-3">
          <div>
            <div className="text-sm font-semibold">Add a member</div>
            <div className="text-[11px] text-ink-muted mt-0.5">
              Pick someone from the company and grant them a role on this project.
              Need to add a user who isn't in the company yet? Use Settings → Members first.
            </div>
          </div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted">
            <X size={14} />
          </button>
        </div>
        <div className="p-5 space-y-4">
          <label className="block">
            <span className="label block mb-1.5">User</span>
            {loading ? (
              <div className="text-xs text-ink-muted flex items-center gap-2"><Spinner /> Loading company members…</div>
            ) : candidates.length === 0 ? (
              <div className="rounded-md border border-surface-border bg-surface px-3 py-2 text-[11px] text-ink-muted">
                Every company member already has access to this project.
              </div>
            ) : (
              <select value={userId ?? ""} onChange={(e) => setUserId(Number(e.target.value))}
                      className="input">
                <option value="">Select a user…</option>
                {candidates.map((m) => (
                  <option key={m.userId} value={m.userId}>
                    {m.username}{m.email ? ` · ${m.email}` : ""}
                  </option>
                ))}
              </select>
            )}
          </label>

          <label className="block">
            <span className="label block mb-1.5">Role</span>
            <div className="space-y-1.5">
              {ROLE_OPTIONS.map((o) => (
                <label key={o.value}
                       className={cn(
                         "flex items-start gap-2 p-2.5 rounded-md border cursor-pointer",
                         role === o.value
                           ? "border-brand-500/40 bg-brand-500/5"
                           : "border-surface-border hover:border-brand-500/30 bg-surface",
                       )}>
                  <input type="radio" checked={role === o.value} onChange={() => setRole(o.value)}
                         className="mt-0.5 accent-brand-500" />
                  <span className="min-w-0">
                    <span className="text-xs font-medium block">{o.label}</span>
                    <span className="text-[10px] text-ink-muted">
                      {o.value === "QA_MANAGER"
                        ? "Manages members + devices for this project. Write/run tests."
                        : "Write/run tests in this project."}
                    </span>
                  </span>
                </label>
              ))}
            </div>
          </label>

          {err && <ErrBox text={String(err)} />}
        </div>
        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="primary"
                  loading={add.isPending}
                  disabled={userId == null || candidates.length === 0}
                  onClick={() => add.mutate()}>Add</Button>
        </div>
      </Card>
    </div>
  );
}

/* ─────────────────────────  Danger zone  ──────────────────────── */

function DangerSection({
  projectId, projectName, companyId, archived, onArchived,
}: { projectId: number; projectName: string; companyId: number; archived: boolean; onArchived: () => void }) {
  const [confirmText, setConfirmText] = useState("");
  const archive = useMutation({
    mutationFn: () => projectApi.archive(companyId, projectId),
    onSuccess: onArchived,
  });
  const err = (archive.error as any)?.response?.data?.detail
           ?? (archive.error as any)?.response?.data?.message;
  const canArchive = confirmText === projectName && !archived;

  return (
    <Section
      id="danger"
      tone="danger"
      title="Danger zone"
      description="Archive removes the project from every picker and read view across the platform. Tests, runs, and devices stay in the database — restoration is a manual operation."
    >
      {archived ? (
        <ArchivedBanner />
      ) : (
        <>
          <FormRow label="Confirm" hint={`Type "${projectName}" to enable the archive button.`}>
            <input value={confirmText} onChange={(e) => setConfirmText(e.target.value)}
                   placeholder={projectName} className="input font-mono" />
          </FormRow>

          {err && <ErrBox text={String(err)} />}

          <div className="flex justify-end">
            <Button variant="danger" size="sm" leftIcon={<Trash2 size={12} />}
                    disabled={!canArchive}
                    loading={archive.isPending}
                    onClick={() => archive.mutate()}>
              Archive project
            </Button>
          </div>
        </>
      )}
    </Section>
  );
}

/* ─────────────────────────  Tiny helpers  ─────────────────────── */

function ErrBox({ text }: { text: string }) {
  return (
    <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">
      {text}
    </div>
  );
}

function ArchivedBanner() {
  return (
    <div className="rounded-md border border-warning-500/30 bg-warning-500/10 text-warning-500 px-3 py-2 text-xs">
      This project is archived. Editing is disabled.
    </div>
  );
}
