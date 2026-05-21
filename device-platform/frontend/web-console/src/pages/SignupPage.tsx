import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AlertCircle, ArrowRight, AtSign, Building2, Lock, Smartphone, User } from "lucide-react";
import { useAuthStore } from "@/store/auth";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/cn";

/**
 * Self-service sign-up. Three flavours toggled by the "start with company" choice:
 *  – Personal: solo testers who'll be invited to an existing company later.
 *  – Company:  a brand-new workspace is provisioned with the user as OWNER.
 *
 * Either way the response is a login envelope, so we drop straight into the
 * authenticated app — no extra "now log in" step.
 */
export default function SignupPage() {
  const signup = useAuthStore((s) => s.signup);
  const nav = useNavigate();

  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [createCompany, setCreateCompany] = useState(true);
  const [companyName, setCompanyName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await signup({
        username: username.trim(),
        email: email.trim().toLowerCase(),
        password,
        createCompanyName: createCompany && companyName.trim() ? companyName.trim() : undefined,
      });
      nav(createCompany ? "/automation/workspace" : "/devices");
    } catch (err: any) {
      setError(err?.response?.data?.detail
            ?? err?.response?.data?.message
            ?? "Sign-up failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="min-h-full grid lg:grid-cols-2 bg-surface">
      <div className="hidden lg:flex flex-col justify-between p-12 border-r border-surface-border bg-gradient-to-br from-surface via-surface to-brand-900/10 relative overflow-hidden">
        <div className="absolute inset-0 opacity-30" style={{
          backgroundImage: "radial-gradient(circle at 25% 30%, rgba(59,110,240,0.18), transparent 40%), radial-gradient(circle at 80% 70%, rgba(59,110,240,0.10), transparent 50%)",
        }} />
        <div className="relative flex items-center gap-3">
          <div className="h-10 w-10 rounded-lg bg-brand-500/10 border border-brand-500/30 flex items-center justify-center">
            <Smartphone size={20} className="text-brand-400" />
          </div>
          <div>
            <div className="text-base font-semibold">Device Platform</div>
            <div className="text-[11px] text-ink-muted">Mobile QA Cloud</div>
          </div>
        </div>
        <div className="relative space-y-5 max-w-md">
          <h1 className="text-4xl font-bold leading-tight tracking-tight">
            Start testing on real devices in under a minute.
          </h1>
          <p className="text-ink-secondary leading-relaxed">
            Spin up a workspace, enroll your first device, and run scenarios from the browser.
            Free for self-hosted deployments.
          </p>
        </div>
        <div className="relative text-[11px] text-ink-muted">© Device Platform · v0.1.0</div>
      </div>

      <div className="flex items-center justify-center p-6 sm:p-12">
        <div className="w-full max-w-sm space-y-6">
          <div className="lg:hidden flex items-center gap-3">
            <div className="h-10 w-10 rounded-lg bg-brand-500/10 border border-brand-500/30 flex items-center justify-center">
              <Smartphone size={20} className="text-brand-400" />
            </div>
            <div className="text-base font-semibold">Device Platform</div>
          </div>
          <div>
            <h2 className="text-2xl font-semibold tracking-tight">Create account</h2>
            <p className="text-sm text-ink-secondary mt-1">Set up your credentials and (optionally) a workspace.</p>
          </div>

          <form onSubmit={onSubmit} className="space-y-3.5">
            <Field id="username" label="Username" icon={<User size={14} />}>
              <input id="username" autoFocus value={username}
                onChange={(e) => setUsername(e.target.value)}
                minLength={3} maxLength={64} required autoComplete="username" className="input pl-9" />
            </Field>

            <Field id="email" label="Email" icon={<AtSign size={14} />}>
              <input id="email" type="email" value={email}
                onChange={(e) => setEmail(e.target.value)}
                required autoComplete="email" className="input pl-9" />
            </Field>

            <Field id="password" label="Password" icon={<Lock size={14} />}>
              <input id="password" type="password" value={password}
                onChange={(e) => setPassword(e.target.value)}
                minLength={8} required autoComplete="new-password" className="input pl-9" />
            </Field>

            <div className="space-y-2 pt-1">
              <span className="label block">Workspace</span>
              <div className="space-y-1.5">
                <label className={cn(
                  "flex items-start gap-2 p-2.5 rounded-md border cursor-pointer",
                  createCompany ? "border-brand-500/40 bg-brand-500/5"
                                : "border-surface-border hover:border-brand-500/30",
                )}>
                  <input type="radio" checked={createCompany} onChange={() => setCreateCompany(true)} className="mt-0.5 accent-brand-500" />
                  <span className="min-w-0">
                    <span className="text-xs font-medium block">Create a new company</span>
                    <span className="text-[10px] text-ink-muted">You become OWNER and get a default project.</span>
                  </span>
                </label>
                <label className={cn(
                  "flex items-start gap-2 p-2.5 rounded-md border cursor-pointer",
                  !createCompany ? "border-brand-500/40 bg-brand-500/5"
                                 : "border-surface-border hover:border-brand-500/30",
                )}>
                  <input type="radio" checked={!createCompany} onChange={() => setCreateCompany(false)} className="mt-0.5 accent-brand-500" />
                  <span className="min-w-0">
                    <span className="text-xs font-medium block">I'm joining an existing one</span>
                    <span className="text-[10px] text-ink-muted">Wait for an admin to invite you by email.</span>
                  </span>
                </label>
              </div>

              {createCompany && (
                <Field id="companyName" label="Company name" icon={<Building2 size={14} />}>
                  <input id="companyName" value={companyName}
                    onChange={(e) => setCompanyName(e.target.value)}
                    required maxLength={128} className="input pl-9" placeholder="Acme Corp" />
                </Field>
              )}
            </div>

            {error && (
              <div className="flex items-start gap-2 rounded-md bg-danger-500/10 border border-danger-500/30 px-3 py-2.5 text-sm text-danger-500">
                <AlertCircle size={14} className="mt-0.5 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <Button type="submit" variant="primary" size="lg" className="w-full"
              loading={busy} rightIcon={<ArrowRight size={14} />}>
              {busy ? "Creating account" : "Create account"}
            </Button>
          </form>

          <div className="text-center text-xs text-ink-muted">
            Already have an account?{" "}
            <Link to="/login" className="text-brand-300 hover:text-brand-400 font-medium">
              Sign in
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}

function Field({
  id, label, icon, children,
}: { id: string; label: string; icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <label htmlFor={id} className="block">
      <span className="label block mb-1.5">{label}</span>
      <div className="relative">
        <span className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-muted">{icon}</span>
        {children}
      </div>
    </label>
  );
}
