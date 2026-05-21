import { useEffect, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Lock, Unlock, X } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Spinner } from "@/components/ui/Spinner";
import { deviceApi } from "@/lib/devices";
import { projectApi } from "@/lib/tenancy";
import { useActiveCompany } from "@/store/auth";
import { cn } from "@/lib/cn";

type Props = {
  deviceId: number;
  deviceLabel: string;
  /** Override the project picker's source company — used by the platform-admin
   *  devices page when editing a device that lives in a different tenant than
   *  the admin's active company. Falls back to {@link useActiveCompany} when null. */
  companyIdOverride?: number | null;
  onClose: () => void;
  onSaved?: () => void;
};

/**
 * Restrict a device to a subset of projects. Default state is "available to all" —
 * flip the toggle to enable the whitelist. The "all projects" mode clears the
 * whitelist server-side so a future re-enable doesn't inherit stale grants.
 *
 * Save is explicit (not auto-on-toggle) because mixing modes mid-edit is confusing.
 */
export default function DeviceAccessDialog({ deviceId, deviceLabel, companyIdOverride, onClose, onSaved }: Props) {
  const activeCompany = useActiveCompany();
  const companyId = companyIdOverride ?? activeCompany?.id ?? null;

  const accessQ = useQuery({
    queryKey: ["device-access", deviceId],
    queryFn: () => deviceApi.getAccess(deviceId),
  });
  const projectsQ = useQuery({
    queryKey: ["company-projects", companyId],
    queryFn: () => projectApi.list(companyId!),
    enabled: companyId != null,
  });

  const [restricted, setRestricted] = useState(false);
  const [projectIds, setProjectIds] = useState<Set<number>>(new Set());

  useEffect(() => {
    if (accessQ.data) {
      setRestricted(accessQ.data.restricted);
      setProjectIds(new Set(accessQ.data.projectIds));
    }
  }, [accessQ.data]);

  const save = useMutation({
    mutationFn: () => deviceApi.updateAccess(deviceId, {
      restricted,
      projectIds: restricted ? Array.from(projectIds) : [],
    }),
    onSuccess: () => { onSaved?.(); onClose(); },
  });

  const err = (save.error as any)?.response?.data?.detail
            ?? (save.error as any)?.response?.data?.message;

  const projects = (projectsQ.data ?? []).filter((p) => p.archivedAt == null);

  function toggleProject(id: number) {
    setProjectIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-md max-h-[80vh] flex flex-col">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div>
            <div className="text-sm font-semibold flex items-center gap-2">
              {restricted ? <Lock size={14} className="text-warning-500" />
                          : <Unlock size={14} className="text-success-500" />}
              Project access
            </div>
            <div className="text-[11px] text-ink-muted mt-0.5">
              <code className="font-mono">{deviceLabel}</code>
            </div>
          </div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted">
            <X size={14} />
          </button>
        </div>

        <div className="p-5 flex-1 overflow-auto space-y-4">
          {accessQ.isLoading ? (
            <div className="text-ink-muted text-sm flex items-center gap-2"><Spinner /> Loading…</div>
          ) : (
            <>
              <div className="space-y-2">
                <label className={cn(
                  "flex items-start gap-2 p-2.5 rounded-md border cursor-pointer",
                  !restricted ? "border-success-500/40 bg-success-500/5" : "border-surface-border hover:border-brand-500/30"
                )}>
                  <input
                    type="radio"
                    checked={!restricted}
                    onChange={() => setRestricted(false)}
                    className="mt-0.5 accent-success-500"
                  />
                  <span className="min-w-0">
                    <span className="text-xs font-medium block">Available to all projects</span>
                    <span className="text-[10px] text-ink-muted">
                      Every project in this company can see and reserve the device. Default.
                    </span>
                  </span>
                </label>
                <label className={cn(
                  "flex items-start gap-2 p-2.5 rounded-md border cursor-pointer",
                  restricted ? "border-warning-500/40 bg-warning-500/5" : "border-surface-border hover:border-brand-500/30"
                )}>
                  <input
                    type="radio"
                    checked={restricted}
                    onChange={() => setRestricted(true)}
                    className="mt-0.5 accent-warning-500"
                  />
                  <span className="min-w-0">
                    <span className="text-xs font-medium block">Restricted to specific projects</span>
                    <span className="text-[10px] text-ink-muted">
                      Only the projects you tick below will see this device.
                    </span>
                  </span>
                </label>
              </div>

              {restricted && (
                <div>
                  <div className="text-[10px] uppercase tracking-wider text-ink-muted mb-2">
                    Allowed projects
                  </div>
                  <div className="space-y-1 max-h-48 overflow-auto">
                    {projects.length === 0 ? (
                      <div className="text-ink-muted text-xs">No projects in this company.</div>
                    ) : projects.map((p) => (
                      <label
                        key={p.id}
                        className="flex items-center justify-between gap-3 p-2 rounded border border-surface-border hover:border-brand-500/30 cursor-pointer"
                      >
                        <div className="min-w-0">
                          <div className="text-xs font-medium truncate">{p.name}</div>
                          <div className="text-[10px] text-ink-muted font-mono">{p.slug}</div>
                        </div>
                        <input
                          type="checkbox"
                          checked={projectIds.has(p.id)}
                          onChange={() => toggleProject(p.id)}
                          className="h-4 w-4 accent-brand-500"
                        />
                      </label>
                    ))}
                  </div>
                  {projectIds.size === 0 && (
                    <div className="mt-2 text-[11px] text-warning-500">
                      No projects selected — the device will be hidden everywhere until you add at least one.
                    </div>
                  )}
                </div>
              )}

              {err && (
                <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">
                  {String(err)}
                </div>
              )}
            </>
          )}
        </div>

        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={save.isPending} onClick={() => save.mutate()}>
            Save
          </Button>
        </div>
      </Card>
    </div>
  );
}
