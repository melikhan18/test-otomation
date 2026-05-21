import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AlertCircle, ArrowRight, Lock, Smartphone, User } from "lucide-react";
import { useAuthStore } from "@/store/auth";
import { Button } from "@/components/ui/Button";

export default function LoginPage() {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("Admin@123");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const login = useAuthStore((s) => s.login);
  const nav = useNavigate();

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login(username, password);
      nav("/devices");
    } catch (err: any) {
      setError(err?.response?.data?.detail ?? "Invalid credentials");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="min-h-full grid lg:grid-cols-2 bg-surface">
      {/* Hero pane */}
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

        <div className="relative space-y-6 max-w-md">
          <h1 className="text-4xl font-bold leading-tight tracking-tight">
            Stream, control and inspect Android devices from anywhere.
          </h1>
          <p className="text-ink-secondary leading-relaxed">
            Self-hosted device farm with sub-300 ms latency, exclusive session reservation, and an
            Appium-compatible UI inspector — built for QA teams that need real device coverage at scale.
          </p>
          <dl className="grid grid-cols-3 gap-6 pt-4">
            {[
              ["10+", "Parallel devices"],
              ["< 350 ms", "Glass-to-glass"],
              ["100%", "On-prem"],
            ].map(([v, l]) => (
              <div key={l}>
                <dt className="text-2xl font-semibold text-ink-primary">{v}</dt>
                <dd className="text-[11px] uppercase tracking-wider text-ink-muted mt-1">{l}</dd>
              </div>
            ))}
          </dl>
        </div>

        <div className="relative text-[11px] text-ink-muted">© Device Platform · v0.1.0</div>
      </div>

      {/* Form pane */}
      <div className="flex items-center justify-center p-6 sm:p-12">
        <div className="w-full max-w-sm space-y-7">
          <div className="lg:hidden flex items-center gap-3">
            <div className="h-10 w-10 rounded-lg bg-brand-500/10 border border-brand-500/30 flex items-center justify-center">
              <Smartphone size={20} className="text-brand-400" />
            </div>
            <div className="text-base font-semibold">Device Platform</div>
          </div>

          <div>
            <h2 className="text-2xl font-semibold tracking-tight">Sign in</h2>
            <p className="text-sm text-ink-secondary mt-1">Welcome back. Enter your credentials to continue.</p>
          </div>

          <form onSubmit={onSubmit} className="space-y-4">
            <Field id="username" label="Username" icon={<User size={14} />}>
              <input
                id="username"
                autoFocus
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                className="input pl-9"
              />
            </Field>

            <Field id="password" label="Password" icon={<Lock size={14} />}>
              <input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                className="input pl-9"
              />
            </Field>

            {error && (
              <div className="flex items-start gap-2 rounded-md bg-danger-500/10 border border-danger-500/30 px-3 py-2.5 text-sm text-danger-500">
                <AlertCircle size={14} className="mt-0.5 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <Button
              type="submit"
              variant="primary"
              size="lg"
              className="w-full"
              loading={busy}
              rightIcon={<ArrowRight size={14} />}
            >
              {busy ? "Signing in" : "Sign in"}
            </Button>
          </form>

          <div className="rounded-md border border-surface-border bg-surface-raised/50 px-4 py-3 text-[11px] text-ink-muted">
            Demo credentials: <span className="kbd">admin</span> / <span className="kbd">Admin@123</span>
          </div>

          <div className="text-center text-xs text-ink-muted">
            No account yet?{" "}
            <Link to="/signup" className="text-brand-300 hover:text-brand-400 font-medium">
              Create one
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
