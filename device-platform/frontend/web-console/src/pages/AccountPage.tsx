import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Save, ShieldCheck, UserCircle2 } from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { FormRow, Section, SettingsLayout } from "@/components/settings/SettingsLayout";
import { authApi } from "@/lib/api";
import { useAuthStore } from "@/store/auth";

/**
 * Self-service profile editor. Email is the important one — without it nobody
 * can invite this user. Password change is bundled so the current-password
 * check protects both: a stolen token can't quietly hijack the recovery email.
 *
 * Single submit: the form is conceptually two sections (Profile, Security)
 * but both fields flow through the same PATCH /api/auth/me call so the user
 * doesn't have to think about which button applies what.
 */
export default function AccountPage() {
  const username = useAuthStore((s) => s.username);
  const currentEmail = useAuthStore((s) => s.email);
  const reload = useAuthStore((s) => s.reloadMemberships);

  const [email, setEmail] = useState(currentEmail ?? "");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [okMsg, setOkMsg] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () => authApi.updateProfile({
      email: email.trim() ? email.trim().toLowerCase() : undefined,
      newPassword: newPassword || undefined,
      currentPassword,
    }),
    onSuccess: async () => {
      setCurrentPassword("");
      setNewPassword("");
      setOkMsg("Saved");
      setTimeout(() => setOkMsg(null), 2_000);
      await reload();
    },
  });

  const err = (save.error as any)?.response?.data?.detail
            ?? (save.error as any)?.response?.data?.message;

  const emailDirty    = email.trim().toLowerCase() !== (currentEmail ?? "").toLowerCase();
  const passwordDirty = newPassword.length > 0;
  const canSubmit = !!currentPassword && (emailDirty || passwordDirty);

  return (
    <>
      <TopBar crumbs={[{ label: "Account" }, { label: "Profile" }]} />

      <SettingsLayout>
        <Section
          id="profile"
          title="Profile"
          description="Identity shown next to your name, used by admins to find you on invites and audit logs."
        >
          <FormRow label="Username" hint="Cannot be changed here.">
            <div className="flex items-center gap-2 h-9 px-3 rounded-md border border-surface-border bg-surface text-sm">
              <UserCircle2 size={13} className="text-brand-400" />
              <span className="font-medium">{username}</span>
            </div>
          </FormRow>

          <FormRow label="Email" hint="Required so other admins can invite you. Stored lowercased.">
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                   placeholder="you@example.com" className="input font-mono" />
          </FormRow>
        </Section>

        <Section
          id="security"
          title="Security"
          description="Leave the new password blank if you only want to change your email."
        >
          <FormRow label="New password" hint="Optional. At least 8 characters.">
            <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)}
                   placeholder="••••••••" className="input" minLength={8} />
          </FormRow>

          <FormRow label="Current password"
                   hint="Required to save any change — re-auth check that prevents stolen-token hijacks.">
            <div className="flex items-center gap-2">
              <ShieldCheck size={13} className="text-ink-muted" />
              <input type="password" value={currentPassword}
                     onChange={(e) => setCurrentPassword(e.target.value)}
                     className="input" />
            </div>
          </FormRow>

          {err && (
            <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">
              {String(err)}
            </div>
          )}
          {okMsg && (
            <div className="rounded-md border border-success-500/30 bg-success-500/10 text-success-500 px-3 py-2 text-xs">
              {okMsg}
            </div>
          )}

          <div className="flex justify-end">
            <Button variant="primary" leftIcon={<Save size={12} />}
                    loading={save.isPending}
                    disabled={!canSubmit}
                    onClick={() => save.mutate()}>
              Save changes
            </Button>
          </div>
        </Section>
      </SettingsLayout>
    </>
  );
}
