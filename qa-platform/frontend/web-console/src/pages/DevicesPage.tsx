import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Check, ChevronRight, Copy, KeyRound, Lock, Plus, Search, Smartphone, X,
} from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { EmptyState } from "@/components/ui/EmptyState";
import { Skeleton } from "@/components/ui/Skeleton";
import DeviceAccessDialog from "@/components/DeviceAccessDialog";
import { deviceApi, type Device } from "@/lib/devices";
import { sessionApi } from "@/lib/sessions";
import { useAuthStore, useEffectiveRole } from "@/store/auth";
import { cn } from "@/lib/cn";

export default function DevicesPage() {
  const activeCompanyId = useAuthStore((s) => s.activeCompanyId);
  const effectiveRole = useEffectiveRole();
  const isOwner = effectiveRole === "OWNER";
  const qc = useQueryClient();
  const nav = useNavigate();
  const [enrollToken, setEnrollToken] = useState<{ token: string; expiresAt: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState<"all" | "ONLINE" | "OFFLINE" | "IN_USE">("all");

  const devicesQuery = useQuery({
    queryKey: ["devices", activeCompanyId],
    queryFn: deviceApi.list,
    refetchInterval: 5_000,
    // Skip the call entirely until the user has picked a company — otherwise the
    // backend rejects with 400 "missing X-Company-Id" every 5s.
    enabled: activeCompanyId != null,
  });

  const issueToken = useMutation({
    mutationFn: deviceApi.issueEnrollmentToken,
    onSuccess: (t) => setEnrollToken(t),
  });

  const startSession = useMutation({
    mutationFn: (deviceId: number) => sessionApi.create(deviceId),
    onSuccess: (s) => { qc.invalidateQueries({ queryKey: ["devices"] }); nav(`/sessions/${s.id}`); },
    onError: (err: any) => setError(err?.response?.data?.detail ?? "Could not start session"),
  });

  const stats = useMemo(() => {
    const all = devicesQuery.data ?? [];
    return {
      total:    all.length,
      online:   all.filter((d) => d.status === "ONLINE").length,
      inUse:    all.filter((d) => d.status === "IN_USE").length,
      offline:  all.filter((d) => d.status === "OFFLINE").length,
    };
  }, [devicesQuery.data]);

  const filtered = useMemo(() => {
    const list = devicesQuery.data ?? [];
    return list.filter((d) => {
      if (filter !== "all" && d.status !== filter) return false;
      if (!search) return true;
      const hay = `${d.manufacturer} ${d.model} ${d.serial} ${d.androidVersion}`.toLowerCase();
      return hay.includes(search.toLowerCase());
    });
  }, [devicesQuery.data, search, filter]);

  return (
    <>
      <TopBar
        crumbs={[{ label: "Devices" }]}
        actions={isOwner && (
          <Button
            variant="primary"
            size="sm"
            leftIcon={<Plus size={14} />}
            onClick={() => issueToken.mutate()}
            loading={issueToken.isPending}
          >
            Enrollment token
          </Button>
        )}
      />

      <div className="px-6 py-6 space-y-6">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Stat label="Total devices" value={stats.total} tone="info" />
          <Stat label="Online"        value={stats.online} tone="success" />
          <Stat label="In use"        value={stats.inUse} tone="warning" />
          <Stat label="Offline"       value={stats.offline} tone="neutral" />
        </div>

        <Card className="flex flex-col md:flex-row md:items-center gap-3 px-4 py-3">
          <div className="relative flex-1">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-muted" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by manufacturer, model, serial…"
              className="input pl-9"
            />
          </div>
          <div className="flex items-center gap-1 rounded-md border border-surface-border bg-surface p-1">
            {(["all", "ONLINE", "IN_USE", "OFFLINE"] as const).map((f) => (
              <button
                key={f}
                onClick={() => setFilter(f)}
                className={cn(
                  "px-3 py-1 rounded text-xs font-medium transition-colors",
                  filter === f ? "bg-surface-muted text-ink-primary" : "text-ink-secondary hover:text-ink-primary",
                )}
              >
                {f === "all" ? "All" : f === "IN_USE" ? "In use" : f === "ONLINE" ? "Online" : "Offline"}
              </button>
            ))}
          </div>
        </Card>

        {error && (
          <div className="rounded-md bg-danger-500/10 border border-danger-500/30 text-danger-500 px-3 py-2 text-sm flex items-center justify-between">
            <span>{error}</span>
            <button onClick={() => setError(null)} className="hover:opacity-80"><X size={14} /></button>
          </div>
        )}

        {devicesQuery.isLoading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-4">
            {Array.from({ length: 6 }).map((_, i) => (
              <Card key={i} className="p-5 space-y-4">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-2.5 min-w-0 flex-1">
                    <Skeleton className="h-9 w-9 rounded-lg" />
                    <div className="flex-1 space-y-1.5">
                      <Skeleton className="h-3.5 w-32" />
                      <Skeleton className="h-2.5 w-20" />
                    </div>
                  </div>
                  <Skeleton className="h-5 w-14" />
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <Skeleton className="h-3 w-full" />
                  <Skeleton className="h-3 w-full" />
                  <Skeleton className="h-3 w-full" />
                  <Skeleton className="h-3 w-full" />
                </div>
                <Skeleton className="h-9 w-full" />
              </Card>
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <Card>
            <EmptyState
              icon={<Smartphone size={20} />}
              title={devicesQuery.data?.length ? "No devices match your filters" : "No devices yet"}
              description={devicesQuery.data?.length
                ? "Try adjusting the search or status filter."
                : "Generate an enrollment token and install the agent APK on your Android device or emulator."}
              action={isOwner && !devicesQuery.data?.length && (
                <Button variant="primary" size="sm" leftIcon={<KeyRound size={14} />}
                        onClick={() => issueToken.mutate()} loading={issueToken.isPending}>
                  Generate enrollment token
                </Button>
              )}
            />
          </Card>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-4">
            {filtered.map((d) => (
              <DeviceCard
                key={d.id}
                device={d}
                canManageAccess={isOwner}
                disabled={startSession.isPending || d.status !== "ONLINE"}
                onConnect={() => { setError(null); startSession.mutate(d.id); }}
                onAccessChanged={() => devicesQuery.refetch()}
              />
            ))}
          </div>
        )}
      </div>

      {enrollToken && (
        <EnrollmentTokenDialog token={enrollToken} onClose={() => setEnrollToken(null)} />
      )}
    </>
  );
}

function Stat({ label, value, tone }: { label: string; value: number; tone: "info" | "success" | "warning" | "neutral" }) {
  const dot = { info: "bg-brand-500", success: "bg-success-500", warning: "bg-warning-500", neutral: "bg-ink-muted" }[tone];
  return (
    <Card className="px-5 py-4">
      <div className="flex items-center justify-between">
        <span className="label">{label}</span>
        <span className={cn("h-2 w-2 rounded-full", dot)} />
      </div>
      <div className="text-2xl font-semibold mt-2 text-ink-primary">{value}</div>
    </Card>
  );
}

function DeviceCard({
  device, onConnect, disabled, onAccessChanged, canManageAccess,
}: {
  device: Device;
  onConnect: () => void;
  disabled: boolean;
  onAccessChanged?: () => void;
  /** Show the per-device project access dialog trigger. OWNER-only on backend,
   *  so we hide it for everyone else to keep the UI honest. */
  canManageAccess: boolean;
}) {
  const tone = device.status === "ONLINE" ? "success" : device.status === "IN_USE" ? "warning" : "neutral";
  const [accessOpen, setAccessOpen] = useState(false);

  return (
    <Card className="p-5 flex flex-col gap-4 transition-colors hover:border-brand-500/40 group">
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2.5 min-w-0">
          <div className="h-9 w-9 rounded-lg bg-surface border border-surface-border flex items-center justify-center shrink-0">
            <Smartphone size={16} className="text-brand-400" />
          </div>
          <div className="min-w-0">
            <div className="text-sm font-semibold truncate">{device.manufacturer} {device.model}</div>
            <div className="text-[11px] text-ink-muted truncate" title={device.serial}>{device.serial}</div>
          </div>
        </div>
        <StatusBadge tone={tone}>{device.status === "IN_USE" ? "In use" : device.status}</StatusBadge>
      </div>

      <dl className="grid grid-cols-2 gap-2 text-xs">
        <Field label="Android" value={device.androidVersion} />
        <Field label="Display" value={`${device.screenWidth}×${device.screenHeight}`} />
        <Field label="Agent"   value={device.agentVersion ?? "—"} />
        <Field label="Last seen" value={device.lastSeenAt ? formatRel(device.lastSeenAt) : "—"} />
      </dl>

      <div className="flex items-center gap-2 mt-auto">
        <Button
          variant={device.status === "ONLINE" ? "primary" : "secondary"}
          size="md"
          onClick={onConnect}
          disabled={disabled}
          rightIcon={<ChevronRight size={14} />}
          className="flex-1"
        >
          {device.status === "IN_USE" ? "In use by another user"
           : device.status === "OFFLINE" ? "Device offline"
           : "Open session"}
        </Button>
        {canManageAccess && (
          <button
            onClick={() => setAccessOpen(true)}
            className="h-9 w-9 rounded-md border border-surface-border bg-surface hover:border-brand-500/40 text-ink-secondary hover:text-ink-primary flex items-center justify-center shrink-0"
            title="Manage which projects can use this device"
          >
            <Lock size={13} />
          </button>
        )}
      </div>

      {accessOpen && (
        <DeviceAccessDialog
          deviceId={device.id}
          deviceLabel={`${device.manufacturer} ${device.model}`}
          onClose={() => setAccessOpen(false)}
          onSaved={onAccessChanged}
        />
      )}
    </Card>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-[10px] uppercase tracking-wider text-ink-muted">{label}</dt>
      <dd className="text-ink-primary truncate">{value}</dd>
    </div>
  );
}

function formatRel(iso: string): string {
  const ms = Date.now() - new Date(iso).getTime();
  if (ms < 60_000) return "just now";
  const m = Math.floor(ms / 60_000);
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.floor(h / 24)}d ago`;
}

function EnrollmentTokenDialog({
  token, onClose,
}: { token: { token: string; expiresAt: string }; onClose: () => void }) {
  const [copied, setCopied] = useState(false);
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-lg">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div>
            <div className="text-sm font-semibold flex items-center gap-2">
              <KeyRound size={14} className="text-brand-400" /> Enrollment token
            </div>
            <div className="text-xs text-ink-muted mt-0.5">Single use · expires {new Date(token.expiresAt).toLocaleTimeString()}</div>
          </div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted">
            <X size={14} />
          </button>
        </div>
        <div className="px-5 py-5 space-y-4">
          <p className="text-sm text-ink-secondary">
            Paste this token into the <span className="kbd">Device Farm Agent</span> app on your Android device to enroll it under your product.
          </p>
          <div className="rounded-md border border-surface-border bg-surface p-3 font-mono text-xs break-all">
            {token.token}
          </div>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={onClose}>Close</Button>
            <Button
              variant="primary"
              leftIcon={copied ? <Check size={14} /> : <Copy size={14} />}
              onClick={() => { navigator.clipboard.writeText(token.token); setCopied(true); setTimeout(() => setCopied(false), 1500); }}
            >
              {copied ? "Copied" : "Copy token"}
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
}
