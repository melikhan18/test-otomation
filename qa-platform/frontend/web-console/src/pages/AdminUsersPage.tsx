import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Building2, Edit3, KeyRound, Plus, ShieldCheck, ToggleLeft, ToggleRight, X,
} from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { adminUserApi, type AdminUser } from "@/lib/api";
import { useAuthStore } from "@/store/auth";

/**
 * Platform-admin-only user management. Distinct from /settings/members which
 * scopes to one company — this page is global and used by the operations team
 * to bootstrap accounts, fix forgotten passwords, and disable abandoned users.
 *
 * Access guard: the page silently bounces if the caller isn't platform admin;
 * the backend already returns 403, but we hide the UI for cleanliness.
 */
export default function AdminUsersPage() {
  const isAdmin = useAuthStore((s) => s.platformAdmin);
  const qc = useQueryClient();
  const [creating, setCreating] = useState(false);

  const usersQ = useQuery({
    queryKey: ["admin-users"],
    queryFn: adminUserApi.list,
    enabled: isAdmin,
  });

  if (!isAdmin) {
    return <>
      <TopBar crumbs={[{ label: "Platform" }, { label: "Users" }]} />
      <div className="p-6 text-ink-muted text-sm">Platform admin access required.</div>
    </>;
  }

  return (
    <>
      <TopBar
        crumbs={[{ label: "Platform" }, { label: "Users" }]}
        actions={
          <Button variant="primary" size="sm" leftIcon={<Plus size={12} />} onClick={() => setCreating(true)}>
            New user
          </Button>
        }
      />

      <div className="px-6 py-6 space-y-4">
        <Card className="p-4 text-xs">
          <span className="text-ink-secondary">
            <strong className="text-ink-primary text-sm">{(usersQ.data ?? []).length}</strong>{" "}
            user{(usersQ.data ?? []).length === 1 ? "" : "s"} on this platform
          </span>
        </Card>

        {usersQ.isLoading ? (
          <div className="text-ink-muted text-sm flex items-center gap-2"><Spinner /> Loading…</div>
        ) : (usersQ.data ?? []).length === 0 ? (
          <Card>
            <EmptyState
              icon={<ShieldCheck size={20} />}
              title="No users yet"
              description="Create the first one to get started."
            />
          </Card>
        ) : (
          <Card className="p-0 overflow-hidden">
            <table className="w-full text-sm">
              <thead className="text-[10px] uppercase tracking-wider text-ink-muted bg-surface-muted/40">
                <tr>
                  <th className="px-4 py-2 text-left font-semibold">User</th>
                  <th className="px-3 py-2 text-left font-semibold">Email</th>
                  <th className="px-3 py-2 text-left font-semibold">Status</th>
                  <th className="px-3 py-2 text-left font-semibold">Platform admin</th>
                  <th className="px-3 py-2 text-right font-semibold">Companies</th>
                  <th className="px-3 py-2"></th>
                </tr>
              </thead>
              <tbody>
                {(usersQ.data ?? []).map((u) => (
                  <UserRow
                    key={u.id}
                    user={u}
                    onMutated={() => qc.invalidateQueries({ queryKey: ["admin-users"] })}
                  />
                ))}
              </tbody>
            </table>
          </Card>
        )}
      </div>

      {creating && (
        <CreateUserDialog
          onClose={() => setCreating(false)}
          onCreated={() => { setCreating(false); qc.invalidateQueries({ queryKey: ["admin-users"] }); }}
        />
      )}
    </>
  );
}

/* ─────────────────────────────  Rows  ──────────────────────────────── */

function UserRow({ user, onMutated }: { user: AdminUser; onMutated: () => void }) {
  const [editing, setEditing] = useState<"email" | "password" | null>(null);

  const toggleEnabled = useMutation({
    mutationFn: () => adminUserApi.update(user.id, { enabled: !user.enabled }),
    onSuccess: onMutated,
  });
  const togglePlatformAdmin = useMutation({
    mutationFn: () => adminUserApi.update(user.id, { platformAdmin: !user.platformAdmin }),
    onSuccess: onMutated,
  });

  return (
    <>
      <tr className="border-t border-surface-border hover:bg-surface-muted/30">
        <td className="px-4 py-2.5">
          <div className="flex items-center gap-2.5">
            <div className="h-7 w-7 rounded-full bg-brand-500/15 text-brand-300 text-xs font-semibold flex items-center justify-center">
              {user.username.slice(0, 1).toUpperCase()}
            </div>
            <div>
              <div className="font-medium">{user.username}</div>
              <div className="text-[10px] text-ink-muted">#{user.id}</div>
            </div>
          </div>
        </td>
        <td className="px-3 py-2.5">
          <div className="flex items-center gap-2">
            <span className={user.email ? "font-mono text-xs" : "text-xs text-ink-muted italic"}>
              {user.email ?? "no email"}
            </span>
            <button
              onClick={() => setEditing("email")}
              className="text-ink-muted hover:text-brand-300"
              title="Edit email"
            >
              <Edit3 size={11} />
            </button>
          </div>
        </td>
        <td className="px-3 py-2.5">
          <button
            onClick={() => toggleEnabled.mutate()}
            disabled={toggleEnabled.isPending}
            className="inline-flex items-center gap-1"
            title={user.enabled ? "Disable user" : "Enable user"}
          >
            {user.enabled
              ? <ToggleRight size={20} className="text-success-500" />
              : <ToggleLeft  size={20} className="text-ink-muted" />}
            <span className="text-[11px]">{user.enabled ? "Enabled" : "Disabled"}</span>
          </button>
        </td>
        <td className="px-3 py-2.5">
          <button
            onClick={() => togglePlatformAdmin.mutate()}
            disabled={togglePlatformAdmin.isPending}
            className="inline-flex items-center gap-1"
          >
            {user.platformAdmin
              ? <StatusBadge tone="warning">ADMIN</StatusBadge>
              : <span className="text-[11px] text-ink-muted">—</span>}
          </button>
        </td>
        <td className="px-3 py-2.5 text-right text-xs font-mono">
          <span className="inline-flex items-center gap-1 text-ink-secondary">
            <Building2 size={11} /> {user.companyCount}
          </span>
        </td>
        <td className="px-2 py-2.5 text-right">
          <button
            onClick={() => setEditing("password")}
            className="inline-flex items-center gap-1 px-2 h-7 rounded border border-surface-border text-[11px] text-ink-secondary hover:text-ink-primary hover:border-brand-500/30"
            title="Reset password"
          >
            <KeyRound size={11} /> Reset
          </button>
        </td>
      </tr>
      {editing === "email" && (
        <EditEmailDialog
          user={user}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); onMutated(); }}
        />
      )}
      {editing === "password" && (
        <ResetPasswordDialog
          user={user}
          onClose={() => setEditing(null)}
          onSaved={() => setEditing(null)}
        />
      )}
    </>
  );
}

/* ─────────────────────────────  Dialogs  ──────────────────────────── */

function CreateUserDialog({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [platformAdmin, setPlatformAdmin] = useState(false);

  const create = useMutation({
    mutationFn: () => adminUserApi.create({
      username: username.trim(), email: email.trim() || undefined,
      password, platformAdmin,
    }),
    onSuccess: onCreated,
  });
  const err = (create.error as any)?.response?.data?.detail
            ?? (create.error as any)?.response?.data?.message;

  return (
    <DialogShell title="Create user" onClose={onClose} icon={<Plus size={14} className="text-brand-400" />}>
      <div className="space-y-3.5">
        <label className="block">
          <span className="label block mb-1.5">Username</span>
          <input autoFocus value={username} onChange={(e) => setUsername(e.target.value)} className="input font-mono" />
        </label>
        <label className="block">
          <span className="label block mb-1.5">Email <span className="text-ink-muted">(optional)</span></span>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} className="input font-mono" />
        </label>
        <label className="block">
          <span className="label block mb-1.5">Initial password</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} className="input" />
        </label>
        <label className="flex items-center gap-2 cursor-pointer text-xs">
          <input type="checkbox" checked={platformAdmin} onChange={(e) => setPlatformAdmin(e.target.checked)} className="accent-warning-500" />
          Grant platform admin
        </label>
        {err && <ErrBox err={err} />}
      </div>
      <DialogFooter onCancel={onClose}>
        <Button variant="primary" loading={create.isPending}
          disabled={!username.trim() || password.length < 8}
          onClick={() => create.mutate()}>
          Create
        </Button>
      </DialogFooter>
    </DialogShell>
  );
}

function EditEmailDialog({ user, onClose, onSaved }: { user: AdminUser; onClose: () => void; onSaved: () => void }) {
  const [email, setEmail] = useState(user.email ?? "");
  const save = useMutation({
    mutationFn: () => adminUserApi.update(user.id, { email: email.trim() }),
    onSuccess: onSaved,
  });
  const err = (save.error as any)?.response?.data?.detail
            ?? (save.error as any)?.response?.data?.message;
  return (
    <DialogShell title={`Email — ${user.username}`} onClose={onClose} icon={<Edit3 size={14} className="text-brand-400" />}>
      <label className="block">
        <span className="label block mb-1.5">Email</span>
        <input autoFocus type="email" value={email} onChange={(e) => setEmail(e.target.value)} className="input font-mono" />
        <div className="text-[10px] text-ink-muted mt-1.5">
          Leave blank to clear. Users with no email can't be invited.
        </div>
      </label>
      {err && <ErrBox err={err} />}
      <DialogFooter onCancel={onClose}>
        <Button variant="primary" loading={save.isPending} onClick={() => save.mutate()}>Save</Button>
      </DialogFooter>
    </DialogShell>
  );
}

function ResetPasswordDialog({ user, onClose, onSaved }: { user: AdminUser; onClose: () => void; onSaved: () => void }) {
  const [pw, setPw] = useState("");
  const reset = useMutation({
    mutationFn: () => adminUserApi.resetPassword(user.id, pw),
    onSuccess: onSaved,
  });
  const err = (reset.error as any)?.response?.data?.detail;
  return (
    <DialogShell title={`Reset password — ${user.username}`} onClose={onClose} icon={<KeyRound size={14} className="text-warning-500" />}>
      <label className="block">
        <span className="label block mb-1.5">New password</span>
        <input autoFocus type="password" value={pw} onChange={(e) => setPw(e.target.value)} minLength={8} className="input" />
        <div className="text-[10px] text-warning-500 mt-1.5">
          Tell the user out-of-band. They should change it after first login.
        </div>
      </label>
      {err && <ErrBox err={err} />}
      <DialogFooter onCancel={onClose}>
        <Button variant="primary" loading={reset.isPending} disabled={pw.length < 8} onClick={() => reset.mutate()}>
          Reset
        </Button>
      </DialogFooter>
    </DialogShell>
  );
}

/* ─────────────────────────────  Bits  ─────────────────────────────── */

function DialogShell({ title, icon, children, onClose }: {
  title: string; icon: React.ReactNode; children: React.ReactNode; onClose: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-md">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div className="text-sm font-semibold flex items-center gap-2">
            {icon} {title}
          </div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted">
            <X size={14} />
          </button>
        </div>
        <div className="p-5">{children}</div>
      </Card>
    </div>
  );
}

function DialogFooter({ onCancel, children }: { onCancel: () => void; children: React.ReactNode }) {
  return (
    <div className="flex justify-end gap-2 mt-5 pt-3 border-t border-surface-border -mx-5 -mb-5 px-5 pb-3">
      <Button variant="secondary" onClick={onCancel}>Cancel</Button>
      {children}
    </div>
  );
}

function ErrBox({ err }: { err: any }) {
  return (
    <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">
      {String(err)}
    </div>
  );
}
