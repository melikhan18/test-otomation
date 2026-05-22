import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { AlertTriangle, Package } from "lucide-react";
import { appApi, type AppSummary } from "@/lib/apps";

type Props = {
  /** Selected app version, or null when the user wants to skip app prep entirely. */
  versionId: number | null;
  onVersionChange: (versionId: number | null) => void;
  resetHomeAfter: boolean;
  onResetHomeAfterChange: (v: boolean) => void;
  killProcessAfter: boolean;
  onKillProcessAfterChange: (v: boolean) => void;
};

/**
 * Shared picker for {@link RunDialog} and {@link SuiteRunDialog}. Keeps two pieces of
 * state internally:
 *   - {@code selectedAppId}: which app the user tapped on (drives the version dropdown)
 *   - parent's {@code versionId}: the actual value that gets POSTed
 *
 * Selecting an app auto-picks its newest version. Picking "(no app)" clears the version
 * and disables the version dropdown — the run skips the app prep phase entirely.
 */
export default function TargetAppPicker({
  versionId, onVersionChange,
  resetHomeAfter, onResetHomeAfterChange,
  killProcessAfter, onKillProcessAfterChange,
}: Props) {
  const [selectedAppId, setSelectedAppId] = useState<number | null>(null);

  const appsQ = useQuery({
    queryKey: ["automation-apps"],
    queryFn: appApi.list,
    refetchOnWindowFocus: false,
  });
  const appDetailQ = useQuery({
    queryKey: ["automation-app", selectedAppId ?? -1],
    queryFn: () => appApi.get(selectedAppId!),
    enabled: selectedAppId != null,
    refetchOnWindowFocus: false,
  });

  // Auto-pick the newest version whenever an app is freshly selected and we
  // have its detail loaded. If the parent's versionId already matches one of
  // the loaded versions we leave it alone (re-open without reset).
  useEffect(() => {
    const detail = appDetailQ.data;
    if (!detail) return;
    const known = detail.versions.some((v) => v.id === versionId);
    if (known) return;
    const top = detail.versions[0];
    onVersionChange(top?.id ?? null);
  }, [appDetailQ.data, versionId, onVersionChange]);

  const apps = appsQ.data ?? [];

  return (
    <div className="space-y-3">
      {/* App grid */}
      <div>
        <span className="label block mb-1.5">Target app</span>
        {appsQ.isLoading && <div className="text-xs text-ink-muted">Loading apps…</div>}
        {!appsQ.isLoading && apps.length === 0 && (
          <div className="rounded-md border border-warning-500/30 bg-warning-500/10 px-3 py-2 text-xs text-warning-500 flex items-start gap-2">
            <AlertTriangle size={12} className="mt-0.5 shrink-0" />
            <span>
              No apps yet. <Link to="/automation/apps" className="underline">Manage apps</Link> to
              upload an APK — runs still work without app prep.
            </span>
          </div>
        )}
        {apps.length > 0 && (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            {/* "(no app)" tile — explicit way to skip prep. */}
            <button
              type="button"
              onClick={() => { setSelectedAppId(null); onVersionChange(null); }}
              className={
                "text-left px-3 py-2 rounded-md border transition-colors " +
                (selectedAppId == null && versionId == null
                  ? "border-brand-500/50 bg-brand-500/10"
                  : "border-surface-border hover:border-brand-500/30 bg-surface")
              }
            >
              <div className="text-sm font-medium">(no app)</div>
              <div className="text-[10px] text-ink-muted">Skip app prep — run on whatever's foreground</div>
            </button>

            {apps.map((a) => (
              <AppTile
                key={a.id}
                app={a}
                selected={selectedAppId === a.id}
                onSelect={() => setSelectedAppId(a.id)}
              />
            ))}
          </div>
        )}
      </div>

      {/* Version dropdown — only when an app is selected and has versions */}
      {selectedAppId != null && (appDetailQ.data?.versions.length ?? 0) > 0 && (
        <div>
          <span className="label block mb-1.5">Version</span>
          <select
            value={versionId ?? ""}
            onChange={(e) => onVersionChange(Number(e.target.value))}
            className="w-full h-9 px-3 rounded-md border border-surface-border bg-surface text-sm focus:outline-none focus:ring-1 focus:ring-brand-500"
          >
            {(appDetailQ.data?.versions ?? []).map((v, i) => (
              <option key={v.id} value={v.id}>
                v{v.versionName ?? v.versionCode} (vc {v.versionCode}){i === 0 ? " · latest" : ""}
              </option>
            ))}
          </select>
          <div className="text-[10px] text-ink-muted mt-1">
            Agent installs/updates to this exact versionCode before step 1. Newer versions
            already on device are kept (no downgrade).
          </div>
        </div>
      )}

      {/* Reset config */}
      <div className="rounded-md border border-surface-border bg-surface px-3 py-2.5 space-y-2">
        <label className="flex items-start gap-2 cursor-pointer select-none">
          <input
            type="checkbox"
            checked={resetHomeAfter}
            onChange={(e) => onResetHomeAfterChange(e.target.checked)}
            className="mt-0.5 accent-brand-500"
          />
          <span className="min-w-0">
            <span className="text-xs font-medium block">Return to home after run</span>
            <span className="text-[10px] text-ink-muted">
              Presses HOME when the run finishes so the next test starts from a clean state.
            </span>
          </span>
        </label>
        <label className="flex items-start gap-2 cursor-pointer select-none">
          <input
            type="checkbox"
            checked={killProcessAfter}
            onChange={(e) => onKillProcessAfterChange(e.target.checked)}
            className="mt-0.5 accent-brand-500"
            disabled={!resetHomeAfter}
          />
          <span className={"min-w-0 " + (!resetHomeAfter ? "opacity-40" : "")}>
            <span className="text-xs font-medium block">Also force-stop the app</span>
            <span className="text-[10px] text-ink-muted">
              Only effective on Device Owner cihazda; standard cihazda no-op (HOME only).
            </span>
          </span>
        </label>
      </div>
    </div>
  );
}

function AppTile({
  app, selected, onSelect,
}: {
  app: AppSummary;
  selected: boolean;
  onSelect: () => void;
}) {
  const hasNoVersions = app.versionCount === 0;
  return (
    <button
      type="button"
      onClick={onSelect}
      disabled={hasNoVersions}
      title={hasNoVersions ? "Upload an APK first" : undefined}
      className={
        "text-left px-3 py-2 rounded-md border transition-colors flex items-start gap-2 " +
        (hasNoVersions
          ? "border-surface-border opacity-50 cursor-not-allowed"
          : selected
            ? "border-brand-500/50 bg-brand-500/10"
            : "border-surface-border hover:border-brand-500/30 bg-surface")
      }
    >
      {app.iconData ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={app.iconData} alt="" className="h-7 w-7 rounded object-contain shrink-0 bg-surface-muted" />
      ) : (
        <div className="h-7 w-7 rounded bg-surface-muted border border-surface-border flex items-center justify-center shrink-0">
          <Package size={12} className="text-ink-muted" />
        </div>
      )}
      <div className="min-w-0">
        <div className="text-sm font-medium truncate">{app.displayName}</div>
        <div className="text-[10px] text-ink-muted truncate font-mono">{app.packageName}</div>
        <div className="text-[10px] text-ink-muted mt-0.5">
          {hasNoVersions
            ? "no APK uploaded"
            : `${app.versionCount} version${app.versionCount === 1 ? "" : "s"} · v${app.latestVersionName ?? app.latestVersionCode}`}
        </div>
      </div>
    </button>
  );
}
