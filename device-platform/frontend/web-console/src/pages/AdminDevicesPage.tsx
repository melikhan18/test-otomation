import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertTriangle, ArrowRight, Building2, Filter, Lock, Search, Smartphone, Unlock, X,
} from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { SkeletonTable } from "@/components/ui/Skeleton";
import { StatusBadge } from "@/components/ui/StatusBadge";
import DeviceAccessDialog from "@/components/DeviceAccessDialog";
import { Section, SettingsLayout } from "@/components/settings/SettingsLayout";
import { adminDeviceApi, deviceApi, type AdminDevice } from "@/lib/devices";
import { companyApi, type CompanyView } from "@/lib/tenancy";
import { cn } from "@/lib/cn";
import { toast } from "@/components/toast/toastStore";

/**
 * /admin/devices — platform-wide device roster for vendor / platform admins.
 * Lists every device across every tenant with its company, restricted state and
 * how many projects can currently see it. Inline toggles for the restricted
 * flag; per-device access dialog reused from the tenant-side Devices page.
 */
export default function AdminDevicesPage() {
  const qc = useQueryClient();
  const [search, setSearch] = useState("");
  const [companyFilter, setCompanyFilter] = useState<number | "all">("all");
  const [statusFilter, setStatusFilter] = useState<"all" | "ONLINE" | "OFFLINE" | "IN_USE">("all");
  const [accessDialog, setAccessDialog] = useState<AdminDevice | null>(null);
  const [reassignDialog, setReassignDialog] = useState<AdminDevice | null>(null);

  const devicesQ = useQuery({
    queryKey: ["admin-devices"],
    queryFn: adminDeviceApi.listAll,
    refetchInterval: 10_000,
  });
  // Companies list — platform admin sees every active tenant — for the filter
  // dropdown and to resolve each device's companyId → name.
  const companiesQ = useQuery({
    queryKey: ["admin-companies"],
    queryFn: companyApi.list,
  });

  const companyById = useMemo(() => {
    const m = new Map<number, CompanyView>();
    (companiesQ.data ?? []).forEach((c) => m.set(c.id, c));
    return m;
  }, [companiesQ.data]);

  const filtered = useMemo(() => {
    const all = devicesQ.data ?? [];
    const q = search.trim().toLowerCase();
    return all.filter((d) => {
      if (statusFilter !== "all" && d.status !== statusFilter) return false;
      if (companyFilter !== "all" && d.companyId !== companyFilter) return false;
      if (!q) return true;
      const hay = `${d.manufacturer} ${d.model} ${d.serial} ${d.androidVersion}`.toLowerCase();
      const company = d.companyId != null ? companyById.get(d.companyId) : null;
      const companyHay = (company?.name ?? "") + " " + (company?.slug ?? "");
      return hay.includes(q) || companyHay.toLowerCase().includes(q);
    });
  }, [devicesQ.data, search, statusFilter, companyFilter, companyById]);

  const toggleRestricted = useMutation({
    mutationFn: async (d: AdminDevice) => {
      // Read current access whitelist so we don't wipe it when flipping the flag.
      const current = await deviceApi.getAccess(d.id);
      return deviceApi.updateAccess(d.id, {
        restricted: !d.restricted,
        projectIds: !d.restricted ? current.projectIds : [],
      });
    },
    onSuccess: (_data, d) => {
      qc.invalidateQueries({ queryKey: ["admin-devices"] });
      toast.success(d.restricted ? "Device opened to all projects" : "Device restricted");
    },
    onError: (e: any) => toast.error(e?.response?.data?.detail ?? "Couldn't update access"),
  });

  function refresh() {
    qc.invalidateQueries({ queryKey: ["admin-devices"] });
  }

  return (
    <>
      <TopBar crumbs={[{ label: "Platform" }, { label: "Devices" }]} />

      <SettingsLayout width="wide">
        <Section
          id="filters"
          title="Filters"
          description="Search across every tenant. Restrict by company or status to narrow down."
        >
          <div className="flex flex-col md:flex-row md:items-center gap-3">
            <div className="relative flex-1">
              <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-muted" />
              <input value={search} onChange={(e) => setSearch(e.target.value)}
                     placeholder="Search by device, serial, company…"
                     className="input pl-9 pr-9" />
              {search && (
                <button onClick={() => setSearch("")}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-muted hover:text-ink-primary">
                  <X size={12} />
                </button>
              )}
            </div>
            <select value={companyFilter === "all" ? "" : String(companyFilter)}
                    onChange={(e) => setCompanyFilter(e.target.value ? Number(e.target.value) : "all")}
                    className="input md:w-56">
              <option value="">All companies</option>
              {(companiesQ.data ?? []).map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
            <div className="flex items-center gap-1 rounded-md border border-surface-border bg-surface p-1">
              {(["all", "ONLINE", "IN_USE", "OFFLINE"] as const).map((f) => (
                <button key={f} onClick={() => setStatusFilter(f)}
                        className={cn(
                          "px-3 py-1 rounded text-xs font-medium transition-colors",
                          statusFilter === f ? "bg-surface-muted text-ink-primary"
                                              : "text-ink-secondary hover:text-ink-primary",
                        )}>
                  {f === "all" ? "All" : f === "IN_USE" ? "In use" : f === "ONLINE" ? "Online" : "Offline"}
                </button>
              ))}
            </div>
          </div>
        </Section>

        <Section
          id="devices"
          title="Devices"
          description={`${devicesQ.data?.length ?? 0} total · ${filtered.length} shown`}
        >
          {devicesQ.isLoading ? (
            <SkeletonTable rows={5} />
          ) : (devicesQ.data ?? []).length === 0 ? (
            <Card className="border-dashed">
              <EmptyState
                icon={<Smartphone size={18} />}
                title="No devices enrolled yet"
                description="Generate an enrollment token from a company-scoped Devices page to bring devices online."
              />
            </Card>
          ) : filtered.length === 0 ? (
            <Card className="border-dashed">
              <EmptyState
                icon={<Filter size={18} />}
                title="No matches"
                description="Try clearing search/filters or pick a different company."
              />
            </Card>
          ) : (
            <div className="overflow-x-auto rounded-md border border-surface-border bg-surface">
              <table className="w-full text-sm">
                <thead className="text-[10px] uppercase tracking-wider text-ink-muted bg-surface-muted/40">
                  <tr>
                    <th className="px-4 py-3 text-left font-semibold w-[26%]">Device</th>
                    <th className="px-4 py-3 text-left font-semibold w-[20%]">Company</th>
                    <th className="px-4 py-3 text-left font-semibold w-[10%]">Status</th>
                    <th className="px-4 py-3 text-left font-semibold w-[14%]">Access</th>
                    <th className="px-4 py-3 text-left font-semibold w-[12%]">Last seen</th>
                    <th className="px-4 py-3 text-right font-semibold w-[18%]">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((d) => (
                    <DeviceRow key={d.id} d={d}
                                company={d.companyId != null ? companyById.get(d.companyId) ?? null : null}
                                onToggleRestricted={() => toggleRestricted.mutate(d)}
                                togglePending={toggleRestricted.isPending}
                                onOpenAccess={() => setAccessDialog(d)}
                                onReassign={() => setReassignDialog(d)} />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Section>
      </SettingsLayout>

      {accessDialog && (
        <DeviceAccessDialog
          deviceId={accessDialog.id}
          deviceLabel={`${accessDialog.manufacturer} ${accessDialog.model}`}
          companyIdOverride={accessDialog.companyId}
          onClose={() => setAccessDialog(null)}
          onSaved={refresh}
        />
      )}

      {reassignDialog && (
        <ReassignDialog
          device={reassignDialog}
          currentCompany={reassignDialog.companyId != null ? companyById.get(reassignDialog.companyId) ?? null : null}
          companies={companiesQ.data ?? []}
          onClose={() => setReassignDialog(null)}
          onSaved={() => { setReassignDialog(null); refresh(); }}
        />
      )}
    </>
  );
}

/* ──────────────────────  Reassign dialog  ────────────────────── */

function ReassignDialog({
  device, currentCompany, companies, onClose, onSaved,
}: {
  device: AdminDevice;
  currentCompany: CompanyView | null;
  companies: CompanyView[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [companyId, setCompanyId] = useState<number | null>(null);
  const reassign = useMutation({
    mutationFn: () => adminDeviceApi.reassign(device.id, companyId!),
    onSuccess: () => {
      const target = companies.find((c) => c.id === companyId);
      toast.success("Device reassigned", {
        description: target ? `Moved to ${target.name}.` : undefined,
      });
      onSaved();
    },
    onError: (e: any) => toast.error(e?.response?.data?.detail ?? "Couldn't reassign device"),
  });
  const err = (reassign.error as any)?.response?.data?.detail
           ?? (reassign.error as any)?.response?.data?.message;

  const candidates = companies.filter((c) => c.id !== device.companyId);
  const targetCompany = companies.find((c) => c.id === companyId) ?? null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-md">
        <div className="px-5 py-4 border-b border-surface-border flex items-start justify-between gap-3">
          <div>
            <div className="text-sm font-semibold flex items-center gap-2">
              <Building2 size={14} className="text-brand-400" />
              Reassign device
            </div>
            <div className="text-[11px] text-ink-muted mt-0.5">
              <code className="font-mono">{device.manufacturer} {device.model}</code>{" "}
              · <span className="font-mono">{device.serial}</span>
            </div>
          </div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted">
            <X size={14} />
          </button>
        </div>

        <div className="p-5 space-y-4">
          <div className="flex items-center gap-3 text-sm">
            <CompanyChip company={currentCompany} fallbackId={device.companyId} />
            <ArrowRight size={14} className="text-ink-muted shrink-0" />
            <CompanyChip company={targetCompany} placeholder="Pick a new company…" />
          </div>

          <label className="block">
            <span className="label block mb-1.5">New company</span>
            <select value={companyId ?? ""} onChange={(e) => setCompanyId(e.target.value ? Number(e.target.value) : null)}
                    className="input">
              <option value="">Select a company…</option>
              {candidates.map((c) => (
                <option key={c.id} value={c.id}>{c.name} · {c.slug}</option>
              ))}
            </select>
          </label>

          <div className="rounded-md border border-warning-500/30 bg-warning-500/10 text-warning-500 px-3 py-2 text-xs flex items-start gap-2">
            <AlertTriangle size={13} className="mt-0.5 shrink-0" />
            <div>
              The current project whitelist will be cleared and the device flipped back to
              <strong> available to all projects </strong>
              in the new company. Active sessions and historical runs keep their references.
            </div>
          </div>

          {err && (
            <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">
              {String(err)}
            </div>
          )}
        </div>

        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="primary"
                  loading={reassign.isPending}
                  disabled={companyId == null}
                  onClick={() => reassign.mutate()}>
            Reassign
          </Button>
        </div>
      </Card>
    </div>
  );
}

function CompanyChip({
  company, fallbackId, placeholder,
}: { company: CompanyView | null; fallbackId?: number | null; placeholder?: string }) {
  if (!company) {
    return (
      <span className="inline-flex items-center gap-1.5 px-2 h-7 rounded border border-dashed border-surface-border text-[11px] text-ink-muted">
        <Building2 size={11} />
        {fallbackId != null ? `company #${fallbackId}` : (placeholder ?? "—")}
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1.5 px-2 h-7 rounded border border-surface-border bg-surface text-xs">
      <Building2 size={11} className="text-brand-400" />
      <span className="font-medium truncate max-w-[8rem]">{company.name}</span>
      <span className="text-[10px] font-mono uppercase text-ink-muted">{company.slug}</span>
    </span>
  );
}

/* ───────────────────────────  Row  ─────────────────────────────── */

function DeviceRow({
  d, company, onToggleRestricted, togglePending, onOpenAccess, onReassign,
}: {
  d: AdminDevice;
  company: CompanyView | null;
  onToggleRestricted: () => void;
  togglePending: boolean;
  onOpenAccess: () => void;
  onReassign: () => void;
}) {
  const tone = d.status === "ONLINE" ? "success" : d.status === "IN_USE" ? "warning" : "neutral";

  return (
    <tr className="border-t border-surface-border hover:bg-surface-muted/30">
      <td className="px-4 py-3 align-top">
        <div className="flex items-center gap-3 min-w-0">
          <div className="h-9 w-9 rounded-md bg-surface-raised border border-surface-border flex items-center justify-center shrink-0">
            <Smartphone size={14} className="text-brand-400" />
          </div>
          <div className="min-w-0">
            <div className="text-sm font-medium truncate">{d.manufacturer} {d.model}</div>
            <div className="text-[11px] text-ink-muted font-mono truncate" title={d.serial}>
              {d.serial}
            </div>
            <div className="text-[10px] text-ink-muted">Android {d.androidVersion}</div>
          </div>
        </div>
      </td>
      <td className="px-4 py-3 align-top">
        {company ? (
          <div className="min-w-0">
            <div className="text-sm font-medium truncate flex items-center gap-1.5">
              <Building2 size={12} className="text-ink-muted shrink-0" />
              {company.name}
            </div>
            <div className="text-[10px] font-mono uppercase text-ink-muted">{company.slug}</div>
          </div>
        ) : d.companyId != null ? (
          <span className="text-[11px] text-ink-muted">company #{d.companyId} (not visible)</span>
        ) : (
          <StatusBadge tone="warning">Orphan</StatusBadge>
        )}
      </td>
      <td className="px-4 py-3 align-top">
        <StatusBadge tone={tone}>
          {d.status === "IN_USE" ? "In use" : d.status}
        </StatusBadge>
      </td>
      <td className="px-4 py-3 align-top">
        <button onClick={onOpenAccess}
                className={cn(
                  "inline-flex items-center gap-1.5 text-xs px-2.5 h-7 rounded border transition-colors",
                  d.restricted
                    ? "border-warning-500/40 bg-warning-500/10 text-warning-500 hover:bg-warning-500/20"
                    : "border-surface-border bg-surface text-ink-secondary hover:text-ink-primary hover:border-brand-500/30",
                )}
                title={d.restricted
                        ? `${d.accessProjectCount} project${d.accessProjectCount === 1 ? "" : "s"} whitelisted`
                        : "Available to all projects in the company"}>
          {d.restricted ? <Lock size={11} /> : <Unlock size={11} />}
          {d.restricted ? `${d.accessProjectCount} project${d.accessProjectCount === 1 ? "" : "s"}` : "All projects"}
        </button>
      </td>
      <td className="px-4 py-3 align-top text-xs text-ink-muted">
        {d.lastSeenAt ? formatRel(d.lastSeenAt) : "—"}
      </td>
      <td className="px-4 py-3 align-top text-right">
        <div className="inline-flex flex-col items-end gap-1">
          <button onClick={onToggleRestricted}
                  disabled={togglePending}
                  className="text-[10px] uppercase tracking-wider text-ink-secondary hover:text-ink-primary px-2 h-6 rounded border border-surface-border hover:border-brand-500/30 disabled:opacity-50"
                  title={d.restricted ? "Make available to all projects" : "Restrict to specific projects"}>
            {d.restricted ? "Unrestrict" : "Restrict"}
          </button>
          <button onClick={onReassign}
                  className="text-[10px] uppercase tracking-wider text-ink-secondary hover:text-ink-primary px-2 h-6 rounded border border-surface-border hover:border-brand-500/30"
                  title="Move to another company">
            Reassign
          </button>
        </div>
      </td>
    </tr>
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
