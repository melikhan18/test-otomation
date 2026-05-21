import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Crown, Pencil, Plus, ShieldCheck, Trash2, User as UserIcon, Wrench, X,
} from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { Section, SettingsLayout } from "@/components/settings/SettingsLayout";
import {
  memberApi, projectApi,
  type AddMember, type MemberView, type ProjectGrantInput, type ProjectRole, type ProjectView, type UpdateMember,
} from "@/lib/tenancy";
import { notificationApi } from "@/lib/notifications";
import { useActiveCompany, useAuthStore } from "@/store/auth";
import { cn } from "@/lib/cn";

const ROLE_OPTIONS: { value: ProjectRole; label: string; help: string }[] = [
  { value: "QA_MANAGER", label: "QA Manager", help: "Write + run tests, manage members and devices inside this project" },
  { value: "TESTER",     label: "Tester",     help: "Write + run tests in this project" },
];

/**
 * /settings/members — the company's people page with a project-scoped matrix.
 * Each row is a user; the OWNER badge plus one chip per project (with QM/TESTER tag)
 * tells you exactly where this person can act. Owners edit the matrix inline through
 * a dialog that grants/revokes/changes per-project roles.
 */
export default function MembersPage() {
  const company = useActiveCompany();
  const reloadMemberships = useAuthStore((s) => s.reloadMemberships);
  const qc = useQueryClient();

  const membersQ = useQuery({
    queryKey: ["company-members", company?.id ?? null],
    queryFn: () => memberApi.list(company!.id),
    enabled: company != null,
    refetchOnWindowFocus: false,
  });

  const projectsQ = useQuery({
    queryKey: ["company-projects", company?.id ?? null],
    queryFn: () => projectApi.list(company!.id),
    enabled: company != null,
    refetchOnWindowFocus: false,
  });

  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<MemberView | null>(null);

  if (!company) {
    return (
      <>
        <TopBar crumbs={[{ label: "Settings" }, { label: "Members" }]} />
        <div className="p-6 text-ink-muted text-sm">Select a company to manage members.</div>
      </>
    );
  }

  const members = membersQ.data ?? [];
  const projects = (projectsQ.data ?? []).filter((p) => p.archivedAt == null);

  function refresh() {
    qc.invalidateQueries({ queryKey: ["company-members", company!.id] });
    reloadMemberships();
  }

  return (
    <>
      <TopBar crumbs={[{ label: company.name }, { label: "Settings" }, { label: "Members" }]} />

      <SettingsLayout>
        <Section
          id="members"
          title="Members"
          description={(
            <span className="inline-flex items-center gap-1.5 flex-wrap">
              {members.length} member{members.length === 1 ? "" : "s"} in {company.name}.{" "}
              You're
              <StatusBadge tone={company.owner ? "warning" : "info"}>{company.owner ? "OWNER" : "MEMBER"}</StatusBadge>
            </span>
          )}
          action={(
            <Button variant="primary" size="sm" leftIcon={<Plus size={12} />} onClick={() => setAdding(true)}>
              Add member
            </Button>
          )}
        >
          {membersQ.isLoading || projectsQ.isLoading ? (
            <div className="text-ink-muted text-xs flex items-center gap-2"><Spinner /> Loading…</div>
          ) : members.length === 0 ? (
            <Card className="border-dashed">
              <EmptyState
                icon={<UserIcon size={18} />}
                title="No members yet"
                description="Add an existing user by username, or invite by email."
              />
            </Card>
          ) : (
            <div className="overflow-x-auto rounded-md border border-surface-border bg-surface">
              <table className="w-full text-sm">
                <thead className="text-[10px] uppercase tracking-wider text-ink-muted bg-surface-muted/40">
                  <tr>
                    <th className="px-4 py-2 text-left font-semibold">User</th>
                    <th className="px-3 py-2 text-left font-semibold">Access</th>
                    <th className="px-3 py-2 text-left font-semibold">Joined</th>
                    <th className="px-3 py-2"></th>
                  </tr>
                </thead>
                <tbody>
                  {members.map((m) => (
                    <MemberRow key={m.userId} member={m} onEdit={() => setEditing(m)}
                               onRemove={() => {
                                 if (confirm(`Remove ${m.username} from ${company.name}?`)) {
                                   memberApi.remove(company.id, m.userId).then(refresh);
                                 }
                               }} />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Section>
      </SettingsLayout>

      {adding && (
        <AddMemberDialog
          companyId={company.id}
          projects={projects}
          onClose={() => setAdding(false)}
          onAdded={() => { setAdding(false); refresh(); }}
        />
      )}

      {editing && (
        <EditMemberDialog
          companyId={company.id}
          projects={projects}
          member={editing}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); refresh(); }}
        />
      )}
    </>
  );
}

/* ─────────────────────────────  Row  ──────────────────────────────── */

function MemberRow({
  member, onEdit, onRemove,
}: { member: MemberView; onEdit: () => void; onRemove: () => void }) {
  return (
    <tr className="border-t border-surface-border hover:bg-surface-muted/30">
      <td className="px-4 py-2.5">
        <div className="flex items-center gap-2.5">
          <div className="h-7 w-7 rounded-full bg-brand-500/15 text-brand-300 text-xs font-semibold flex items-center justify-center">
            {member.username.slice(0, 1).toUpperCase()}
          </div>
          <div className="min-w-0">
            <div className="font-medium truncate">{member.username}</div>
            {member.email && (
              <div className="text-[11px] text-ink-muted truncate">{member.email}</div>
            )}
          </div>
        </div>
      </td>
      <td className="px-3 py-2.5">
        <AccessChips owner={member.owner} grants={member.grants} />
      </td>
      <td className="px-3 py-2.5 text-xs text-ink-muted">
        {new Date(member.joinedAt).toLocaleDateString()}
      </td>
      <td className="px-3 py-2.5 text-right">
        <div className="inline-flex items-center gap-1">
          <button onClick={onEdit}
                  className="p-1.5 rounded text-ink-muted hover:text-ink-primary hover:bg-surface-muted"
                  title="Edit access">
            <Pencil size={13} />
          </button>
          <button onClick={onRemove}
                  className="p-1.5 rounded text-ink-muted hover:text-danger-500 hover:bg-surface-muted"
                  title="Remove from company">
            <Trash2 size={13} />
          </button>
        </div>
      </td>
    </tr>
  );
}

function AccessChips({ owner, grants }: { owner: boolean; grants: MemberView["grants"] }) {
  if (owner) {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs">
        <Crown size={12} className="text-warning-500" /> Company OWNER
      </span>
    );
  }
  if (grants.length === 0) {
    return <span className="text-[11px] text-ink-muted">No project access</span>;
  }
  return (
    <div className="flex flex-wrap gap-1">
      {grants.map((g) => (
        <span key={g.projectId}
              className={cn(
                "inline-flex items-center gap-1 text-[10px] font-medium uppercase tracking-wider px-1.5 py-0.5 rounded",
                g.role === "QA_MANAGER"
                  ? "bg-brand-500/15 text-brand-300 border border-brand-500/30"
                  : "bg-surface-muted text-ink-secondary border border-surface-border",
              )}>
          {g.role === "QA_MANAGER" ? <ShieldCheck size={10} /> : <Wrench size={10} />}
          {g.projectName} · {g.role === "QA_MANAGER" ? "QM" : "T"}
        </span>
      ))}
    </div>
  );
}

/* ─────────────────────────  Dialog scaffolding  ──────────────────── */

function AddMemberDialog({
  companyId, projects, onClose, onAdded,
}: {
  companyId: number;
  projects: ProjectView[];
  onClose: () => void;
  onAdded: () => void;
}) {
  const [mode, setMode] = useState<"invite" | "direct">("invite");
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [owner, setOwner] = useState(false);
  const [grants, setGrants] = useState<ProjectGrantInput[]>([]);

  const directAdd = useMutation({
    mutationFn: () => memberApi.add(companyId, { username: username.trim(), owner, grants } satisfies AddMember),
    onSuccess: onAdded,
  });
  const inviteByEmail = useMutation({
    mutationFn: () => notificationApi.invite(companyId, email.trim(), owner, grants),
    onSuccess: onAdded,
  });
  const submit = mode === "invite" ? inviteByEmail : directAdd;
  const err = (submit.error as any)?.response?.data?.detail
            ?? (submit.error as any)?.response?.data?.message;

  const canSubmit = (mode === "invite" ? !!email.trim() : !!username.trim())
                 && (owner || grants.length > 0);

  return (
    <DialogShell
      title="Add member"
      subtitle="Either grant company-wide OWNER access, or pick the projects this user should work in and what their role is on each."
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="primary"
                  loading={submit.isPending}
                  disabled={!canSubmit}
                  onClick={() => submit.mutate()}>
            {mode === "invite" ? "Send invite" : "Add"}
          </Button>
        </>
      )}
    >
      <div className="flex items-center gap-1 p-0.5 rounded-md border border-surface-border bg-surface text-xs">
        <ToggleTab label="Invite by email" active={mode === "invite"} onClick={() => setMode("invite")} />
        <ToggleTab label="Direct add"      active={mode === "direct"} onClick={() => setMode("direct")} />
      </div>

      {mode === "invite" ? (
        <label className="block">
          <span className="label block mb-1.5">Email</span>
          <input autoFocus type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                 placeholder="ali@acme.com" className="input font-mono" />
          <div className="text-[10px] text-ink-muted mt-1.5">
            The user must already have an account with this email. They'll see an invite in their bell.
          </div>
        </label>
      ) : (
        <label className="block">
          <span className="label block mb-1.5">Username</span>
          <input autoFocus value={username} onChange={(e) => setUsername(e.target.value)}
                 placeholder="e.g. mehmet" className="input font-mono" />
          <div className="text-[10px] text-ink-muted mt-1.5">
            Adds immediately, no confirmation step. Use this for trusted teammates.
          </div>
        </label>
      )}

      <GrantsEditor projects={projects} owner={owner} setOwner={setOwner} grants={grants} setGrants={setGrants} />

      {err && <ErrBox text={String(err)} />}
    </DialogShell>
  );
}

function EditMemberDialog({
  companyId, projects, member, onClose, onSaved,
}: {
  companyId: number;
  projects: ProjectView[];
  member: MemberView;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [owner, setOwner] = useState(member.owner);
  const [grants, setGrants] = useState<ProjectGrantInput[]>(
    member.grants.map((g) => ({ projectId: g.projectId, role: g.role })),
  );

  const save = useMutation({
    mutationFn: () => memberApi.update(companyId, member.userId, { owner, grants } satisfies UpdateMember),
    onSuccess: onSaved,
  });
  const err = (save.error as any)?.response?.data?.detail
            ?? (save.error as any)?.response?.data?.message;

  const canSave = owner || grants.length > 0;

  return (
    <DialogShell
      title={`Edit ${member.username}'s access`}
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={save.isPending} disabled={!canSave}
                  onClick={() => save.mutate()}>Save</Button>
        </>
      )}
    >
      <GrantsEditor projects={projects} owner={owner} setOwner={setOwner} grants={grants} setGrants={setGrants} />
      {err && <ErrBox text={String(err)} />}
    </DialogShell>
  );
}

/* ─────────────────────  Shared grants editor  ────────────────────── */

function GrantsEditor({
  projects, owner, setOwner, grants, setGrants,
}: {
  projects: ProjectView[];
  owner: boolean;
  setOwner: (v: boolean) => void;
  grants: ProjectGrantInput[];
  setGrants: (g: ProjectGrantInput[]) => void;
}) {
  // Index for fast lookup as user toggles checkboxes.
  const grantByProject = useMemo(() => {
    const m = new Map<number, ProjectRole>();
    grants.forEach((g) => m.set(g.projectId, g.role));
    return m;
  }, [grants]);

  function setRoleFor(projectId: number, role: ProjectRole | null) {
    const next = grants.filter((g) => g.projectId !== projectId);
    if (role) next.push({ projectId, role });
    setGrants(next);
  }

  // Clear grants when OWNER is enabled — they become irrelevant.
  useEffect(() => {
    if (owner && grants.length > 0) setGrants([]);
    // We intentionally don't run when grants change, only when owner does.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [owner]);

  return (
    <div className="space-y-3">
      <label className={cn(
        "flex items-start gap-2 p-2.5 rounded-md border cursor-pointer",
        owner ? "border-warning-500/40 bg-warning-500/10"
              : "border-surface-border hover:border-warning-500/30 bg-surface",
      )}>
        <input type="checkbox" checked={owner} onChange={(e) => setOwner(e.target.checked)}
               className="mt-0.5 accent-warning-500" />
        <span className="min-w-0">
          <span className="text-xs font-medium flex items-center gap-1.5">
            <Crown size={11} className="text-warning-500" /> Company OWNER
          </span>
          <span className="text-[10px] text-ink-muted">
            Full company control + implicit access to every project. Overrides the per-project grants below.
          </span>
        </span>
      </label>

      {!owner && (
        <div className="space-y-1.5">
          <div className="label">Per-project access</div>
          {projects.length === 0 ? (
            <div className="rounded-md border border-surface-border bg-surface px-3 py-2 text-[11px] text-ink-muted">
              No active projects yet. Create one first.
            </div>
          ) : projects.map((p) => {
            const current = grantByProject.get(p.id) ?? null;
            return (
              <ProjectRoleRow
                key={p.id}
                projectName={p.name}
                projectSlug={p.slug}
                role={current}
                onChange={(r) => setRoleFor(p.id, r)}
              />
            );
          })}
        </div>
      )}
    </div>
  );
}

function ProjectRoleRow({
  projectName, projectSlug, role, onChange,
}: {
  projectName: string;
  projectSlug: string;
  role: ProjectRole | null;
  onChange: (r: ProjectRole | null) => void;
}) {
  const enabled = role != null;
  return (
    <div className={cn(
      "flex items-center gap-3 p-2.5 rounded-md border",
      enabled ? "border-brand-500/40 bg-brand-500/5" : "border-surface-border bg-surface",
    )}>
      <input type="checkbox" checked={enabled}
             onChange={(e) => onChange(e.target.checked ? "TESTER" : null)}
             className="accent-brand-500" />
      <div className="flex-1 min-w-0">
        <div className="text-xs font-medium truncate">{projectName}</div>
        <div className="text-[10px] font-mono text-ink-muted">{projectSlug}</div>
      </div>
      {enabled && (
        <select value={role!} onChange={(e) => onChange(e.target.value as ProjectRole)}
                className="h-7 px-2 rounded border border-surface-border bg-surface text-xs font-medium focus:outline-none focus:ring-1 focus:ring-brand-500">
          {ROLE_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </select>
      )}
    </div>
  );
}

/* ─────────────────────  Dialog plumbing  ─────────────────────────── */

function DialogShell({
  title, subtitle, onClose, footer, children,
}: {
  title: string;
  subtitle?: string;
  onClose: () => void;
  footer: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-lg">
        <div className="px-5 py-4 border-b border-surface-border flex items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="text-sm font-semibold">{title}</div>
            {subtitle && <div className="text-[11px] text-ink-muted mt-0.5">{subtitle}</div>}
          </div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted">
            <X size={14} />
          </button>
        </div>
        <div className="p-5 space-y-4 max-h-[60vh] overflow-y-auto">{children}</div>
        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">{footer}</div>
      </Card>
    </div>
  );
}

function ToggleTab({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button onClick={onClick}
            className={cn(
              "flex-1 h-7 rounded font-medium transition-colors",
              active ? "bg-brand-500/15 text-brand-300" : "text-ink-secondary hover:text-ink-primary",
            )}>
      {label}
    </button>
  );
}

function ErrBox({ text }: { text: string }) {
  return (
    <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">
      {text}
    </div>
  );
}
