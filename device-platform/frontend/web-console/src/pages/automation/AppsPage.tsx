import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertTriangle, Archive, FileUp, Package, Plus, Search, Trash2, Upload, X,
} from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { useAuthStore } from "@/store/auth";
import { appApi, type AppCreate, type AppSummary, type AppVersionView } from "@/lib/apps";

/**
 * APK Repository page. Master-detail:
 *   - Left:  searchable app list, "New app" button.
 *   - Right: version history of the selected app + drag-drop upload + archive.
 *
 * All mutations invalidate the list query so the sidebar reflects new versionCount /
 * latestVersionCode / iconData on the next paint.
 */
export default function AppsPage() {
  const qc = useQueryClient();
  const activeProjectId = useAuthStore((s) => s.activeProjectId);
  const [search, setSearch] = useState("");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [confirmArchive, setConfirmArchive] = useState<AppSummary | null>(null);

  const appsQ = useQuery({
    queryKey: ["automation-apps", activeProjectId ?? null],
    queryFn: appApi.list,
    refetchOnWindowFocus: false,
    enabled: activeProjectId != null,
  });

  // Auto-select first app on initial load so the right pane isn't blank.
  useEffect(() => {
    if (selectedId != null) return;
    const first = appsQ.data?.[0];
    if (first) setSelectedId(first.id);
  }, [appsQ.data, selectedId]);

  // If the selected app gets archived (deleted from the list), drop the selection.
  useEffect(() => {
    if (selectedId == null || !appsQ.data) return;
    if (!appsQ.data.some((a) => a.id === selectedId)) setSelectedId(appsQ.data[0]?.id ?? null);
  }, [appsQ.data, selectedId]);

  const filtered = useMemo(() => {
    const all = appsQ.data ?? [];
    if (!search) return all;
    const q = search.toLowerCase();
    return all.filter((a) =>
      a.packageName.toLowerCase().includes(q) || a.displayName.toLowerCase().includes(q),
    );
  }, [appsQ.data, search]);

  const create = useMutation({
    mutationFn: (body: AppCreate) => appApi.create(body),
    onSuccess: (a) => {
      // Prefix match: invalidates ["automation-apps", projectId] across every project
      // we may have cached. Same prefix is used by the TargetAppPicker's app list.
      qc.invalidateQueries({ queryKey: ["automation-apps"] });
      setSelectedId(a.id);
      setCreating(false);
      setCreateError(null);
    },
    onError: (e: any) => setCreateError(e?.response?.data?.detail ?? "create failed"),
  });
  const archive = useMutation({
    mutationFn: (id: number) => appApi.archive(id),
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: ["automation-apps"] });
      // Also drop the per-app detail cache so a stale archived view doesn't linger
      // if the user re-selects the same id (e.g. via deep link).
      qc.invalidateQueries({ queryKey: ["automation-app", id] });
      setConfirmArchive(null);
    },
  });

  const totalVersions = useMemo(
    () => (appsQ.data ?? []).reduce((s, a) => s + a.versionCount, 0),
    [appsQ.data],
  );

  return (
    <>
      <TopBar
        crumbs={[{ label: "Automation", to: "/automation/apps" }, { label: "Apps" }]}
        actions={
          <Button variant="primary" size="sm" leftIcon={<Plus size={14} />}
                  onClick={() => { setCreating(true); setCreateError(null); }}>
            New app
          </Button>
        }
      />

      <div className="px-6 py-6 space-y-6">
        <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
          <Stat label="Apps"      value={appsQ.data?.length ?? 0} />
          <Stat label="Versions"  value={totalVersions} />
          <Stat label="With APK"  value={(appsQ.data ?? []).filter((a) => a.versionCount > 0).length} />
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-[320px_minmax(0,1fr)] gap-4">
          {/* ── Left: searchable app list ───────────────────────────── */}
          <Card className="flex flex-col max-h-[70vh]">
            <div className="px-3 py-2 border-b border-surface-border">
              <div className="relative">
                <Search size={12} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-ink-muted" />
                <input
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Search package or name…"
                  className="w-full h-8 pl-7 pr-2 rounded border border-surface-border bg-surface text-xs focus:outline-none focus:ring-1 focus:ring-brand-500"
                />
              </div>
            </div>
            <div className="flex-1 overflow-auto p-2 space-y-1">
              {appsQ.isLoading && (
                <div className="p-4 text-center text-xs text-ink-muted flex items-center justify-center gap-2">
                  <Spinner /> Loading…
                </div>
              )}
              {!appsQ.isLoading && filtered.length === 0 && (
                <EmptyState
                  title={search ? "No matches" : "No apps yet"}
                  description={search ? "Try a different search term." : "Create an app to start uploading APKs."}
                  icon={<Package size={20} />}
                />
              )}
              {filtered.map((a) => (
                <button
                  key={a.id}
                  onClick={() => setSelectedId(a.id)}
                  className={
                    "w-full text-left px-2.5 py-2 rounded-md border transition-colors flex items-start gap-2.5 " +
                    (selectedId === a.id
                      ? "border-brand-500/50 bg-brand-500/10"
                      : "border-transparent hover:border-surface-border hover:bg-surface-muted/40")
                  }
                >
                  <AppIcon iconData={a.iconData} />
                  <div className="flex-1 min-w-0">
                    <div className="text-xs font-medium truncate">{a.displayName}</div>
                    <div className="text-[10px] text-ink-muted truncate font-mono">{a.packageName}</div>
                    <div className="text-[10px] text-ink-muted mt-0.5">
                      {a.versionCount === 0
                        ? "no APK yet"
                        : `${a.versionCount} version${a.versionCount === 1 ? "" : "s"} · v${a.latestVersionName ?? a.latestVersionCode}`}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          </Card>

          {/* ── Right: detail pane ──────────────────────────────────── */}
          <Card className="min-h-[70vh] flex flex-col">
            {selectedId == null ? (
              <div className="flex-1 flex items-center justify-center">
                <EmptyState
                  title="Select an app"
                  description="Pick an app from the list to see its versions."
                  icon={<Package size={20} />}
                />
              </div>
            ) : (
              <AppDetailPane appId={selectedId} onArchive={(a) => setConfirmArchive(a)} />
            )}
          </Card>
        </div>
      </div>

      {/* ── Create dialog ────────────────────────────────────────── */}
      {creating && (
        <CreateAppDialog
          loading={create.isPending}
          error={createError}
          onCancel={() => { setCreating(false); setCreateError(null); }}
          onSubmit={(body) => create.mutate(body)}
        />
      )}

      {/* ── Archive confirmation ─────────────────────────────────── */}
      {confirmArchive && (
        <ConfirmDialog
          title={`Archive "${confirmArchive.displayName}"?`}
          description={
            <>
              The app and its {confirmArchive.versionCount} version
              {confirmArchive.versionCount === 1 ? "" : "s"} will be hidden from new runs.
              Existing run history keeps its references — APK files stay in storage until
              retention cleanup. This is reversible from the database; the UI doesn't
              expose an un-archive button yet.
            </>
          }
          confirmLabel={archive.isPending ? "Archiving…" : "Archive"}
          disabled={archive.isPending}
          tone="danger"
          onCancel={() => setConfirmArchive(null)}
          onConfirm={() => archive.mutate(confirmArchive.id)}
        />
      )}
    </>
  );
}

/* ──────────────────────────────  Sub-components  ──────────────────────────── */

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <Card className="px-4 py-3">
      <div className="text-[10px] uppercase tracking-wider text-ink-muted">{label}</div>
      <div className="text-2xl font-semibold tabular-nums mt-1">{value}</div>
    </Card>
  );
}

function AppIcon({ iconData }: { iconData: string | null }) {
  if (iconData) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img src={iconData} alt="" className="h-8 w-8 rounded-md object-contain shrink-0 bg-surface-muted" />
    );
  }
  return (
    <div className="h-8 w-8 rounded-md bg-surface-muted border border-surface-border flex items-center justify-center shrink-0">
      <Package size={14} className="text-ink-muted" />
    </div>
  );
}

/* ─────────────────  Detail pane  ─────────────────── */

function AppDetailPane({ appId, onArchive }: { appId: number; onArchive: (a: AppSummary) => void }) {
  const qc = useQueryClient();
  const appQ = useQuery({
    queryKey: ["automation-app", appId],
    queryFn: () => appApi.get(appId),
    refetchOnWindowFocus: false,
  });

  if (appQ.isLoading) {
    return (
      <div className="flex-1 flex items-center justify-center text-xs text-ink-muted gap-2">
        <Spinner /> Loading…
      </div>
    );
  }
  if (!appQ.data) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <EmptyState title="App not found" description="It may have been archived in another tab." />
      </div>
    );
  }

  const a = appQ.data;
  const summary: AppSummary = {
    id: a.id, packageName: a.packageName, displayName: a.displayName, description: a.description,
    iconData: a.iconData, latestVersionCode: a.latestVersionCode, latestVersionName: a.latestVersionName,
    versionCount: a.versionCount, createdAt: a.createdAt, updatedAt: a.updatedAt,
  };

  const onUploaded = () => {
    qc.invalidateQueries({ queryKey: ["automation-app", appId] });
    qc.invalidateQueries({ queryKey: ["automation-apps"] });
  };

  return (
    <>
      <div className="px-5 py-4 border-b border-surface-border flex items-start gap-3">
        <AppIcon iconData={a.iconData} />
        <div className="flex-1 min-w-0">
          <div className="text-sm font-semibold truncate">{a.displayName}</div>
          <div className="text-xs text-ink-muted font-mono truncate">{a.packageName}</div>
          {a.description && (
            <div className="text-xs text-ink-secondary mt-1">{a.description}</div>
          )}
        </div>
        <button
          onClick={() => onArchive(summary)}
          className="text-ink-muted hover:text-danger-500 p-1.5 rounded hover:bg-danger-500/10 transition-colors"
          title="Archive app"
        >
          <Archive size={14} />
        </button>
      </div>

      <div className="p-5 space-y-4 overflow-auto flex-1">
        <UploadArea appId={appId} onUploaded={onUploaded} />

        <div>
          <div className="label mb-2">Version history ({a.versions.length})</div>
          {a.versions.length === 0 ? (
            <EmptyState
              title="No APK uploaded yet"
              description="Upload the first .apk above to get started."
              icon={<FileUp size={20} />}
            />
          ) : (
            <div className="space-y-1.5">
              {a.versions.map((v) => (
                <VersionRow
                  key={v.id}
                  v={v}
                  isLatest={a.latestVersionCode != null && v.versionCode === a.latestVersionCode}
                  onDeleted={onUploaded}
                />
              ))}
            </div>
          )}
        </div>
      </div>
    </>
  );
}

/* ─────────────────  Upload area  ─────────────────── */

function UploadArea({ appId, onUploaded }: { appId: number; onUploaded: () => void }) {
  const [pct, setPct] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const [notes, setNotes] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  const upload = useMutation({
    mutationFn: (file: File) => appApi.uploadVersion(appId, file, notes || null, setPct),
    onSuccess: () => {
      setPct(null); setError(null); setNotes("");
      onUploaded();
    },
    onError: (e: any) => {
      setPct(null);
      setError(e?.response?.data?.detail ?? "upload failed");
    },
  });

  const onDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragging(false);
    const file = e.dataTransfer.files?.[0];
    if (file) startUpload(file);
  };

  const startUpload = (file: File) => {
    if (!file.name.toLowerCase().endsWith(".apk")) {
      setError("File must be an .apk");
      return;
    }
    setError(null);
    setPct(0);
    upload.mutate(file);
  };

  return (
    <div>
      <div className="label mb-2">Upload new APK</div>
      <div
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={
          "rounded-md border-2 border-dashed px-4 py-6 transition-colors text-center cursor-pointer " +
          (dragging
            ? "border-brand-500/50 bg-brand-500/10"
            : "border-surface-border hover:border-brand-500/30 bg-surface")
        }
        onClick={() => upload.isPending ? null : inputRef.current?.click()}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".apk,application/vnd.android.package-archive"
          className="hidden"
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) startUpload(f);
            e.target.value = "";  // allow re-selecting the same file
          }}
        />
        {upload.isPending && pct != null ? (
          <div className="space-y-2">
            <div className="text-xs text-ink-secondary flex items-center justify-center gap-2">
              <Spinner /> Uploading… {Math.round(pct * 100)}%
            </div>
            <div className="h-1.5 rounded-full bg-surface-muted overflow-hidden">
              <div
                className="h-full bg-brand-500 transition-[width] duration-150 ease-out"
                style={{ width: `${pct * 100}%` }}
              />
            </div>
          </div>
        ) : (
          <>
            <Upload size={20} className="mx-auto text-ink-muted mb-1.5" />
            <div className="text-xs text-ink-secondary">
              Drop an .apk file here or <span className="text-brand-400 underline">browse</span>
            </div>
            <div className="text-[10px] text-ink-muted mt-1">
              Manifest is parsed server-side — packageName must match this app.
            </div>
          </>
        )}
      </div>

      <input
        value={notes}
        onChange={(e) => setNotes(e.target.value)}
        placeholder="Optional release notes (e.g. 'fixes BIP login crash')"
        className="mt-2 w-full h-8 px-2.5 rounded border border-surface-border bg-surface text-xs focus:outline-none focus:ring-1 focus:ring-brand-500"
        disabled={upload.isPending}
      />

      {error && (
        <div className="mt-2 rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs flex items-start gap-2">
          <AlertTriangle size={12} className="mt-0.5 shrink-0" />
          <span>{error}</span>
        </div>
      )}
    </div>
  );
}

/* ─────────────────  Version row  ─────────────────── */

function VersionRow({
  v, isLatest, onDeleted,
}: { v: AppVersionView; isLatest: boolean; onDeleted: () => void }) {
  const [confirming, setConfirming] = useState(false);
  const del = useMutation({
    mutationFn: () => appApi.deleteVersion(v.appId, v.id),
    onSuccess: () => { setConfirming(false); onDeleted(); },
  });

  return (
    <div className="rounded-md border border-surface-border bg-surface px-3 py-2 flex items-center gap-3">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 text-xs font-medium">
          <span className="font-mono">v{v.versionName ?? v.versionCode}</span>
          {isLatest && (
            <span className="px-1.5 py-0.5 rounded text-[9px] font-medium uppercase tracking-wider bg-success-500/15 text-success-500">
              latest
            </span>
          )}
          <span className="text-[10px] text-ink-muted font-mono">
            ({formatBytes(v.fileSizeBytes)})
          </span>
        </div>
        <div className="text-[10px] text-ink-muted mt-0.5">
          Uploaded {new Date(v.uploadedAt).toLocaleString()} · versionCode {v.versionCode}
        </div>
        {v.notes && (
          <div className="text-[11px] text-ink-secondary mt-0.5 italic truncate">"{v.notes}"</div>
        )}
      </div>
      {confirming ? (
        <div className="flex items-center gap-1.5">
          <button onClick={() => setConfirming(false)}
                  className="text-[10px] text-ink-muted hover:text-ink-primary px-2 py-1 rounded">
            Cancel
          </button>
          <button onClick={() => del.mutate()}
                  disabled={del.isPending}
                  className="text-[10px] text-danger-500 hover:bg-danger-500/10 px-2 py-1 rounded font-medium">
            {del.isPending ? "Deleting…" : "Confirm delete"}
          </button>
        </div>
      ) : (
        <button onClick={() => setConfirming(true)}
                className="text-ink-muted hover:text-danger-500 p-1.5 rounded hover:bg-danger-500/10 transition-colors"
                title="Delete this version">
          <Trash2 size={12} />
        </button>
      )}
    </div>
  );
}

function formatBytes(b: number): string {
  if (b < 1024) return `${b} B`;
  if (b < 1024 * 1024) return `${(b / 1024).toFixed(1)} KB`;
  return `${(b / 1024 / 1024).toFixed(1)} MB`;
}

/* ─────────────────  Create dialog  ─────────────────── */

function CreateAppDialog({
  loading, error, onCancel, onSubmit,
}: {
  loading: boolean; error: string | null;
  onCancel: () => void; onSubmit: (body: AppCreate) => void;
}) {
  const [packageName, setPackageName] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");

  const valid = /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$/.test(packageName) && displayName.trim().length > 0;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-lg">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div>
            <div className="text-sm font-semibold flex items-center gap-2">
              <Package size={14} className="text-brand-400" />
              New app
            </div>
            <div className="text-xs text-ink-muted mt-0.5">
              Register the package shell first; upload APKs after.
            </div>
          </div>
          <button onClick={onCancel} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted">
            <X size={14} />
          </button>
        </div>
        <div className="p-5 space-y-4">
          <div>
            <label className="label block mb-1.5">Package name</label>
            <input
              value={packageName}
              onChange={(e) => setPackageName(e.target.value)}
              placeholder="com.example.app"
              className="w-full h-9 px-3 rounded-md border border-surface-border bg-surface text-sm font-mono focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
            <div className="text-[10px] text-ink-muted mt-1">
              Must match the APK's manifest packageName when you upload it.
            </div>
          </div>
          <div>
            <label className="label block mb-1.5">Display name</label>
            <input
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              placeholder="My App"
              className="w-full h-9 px-3 rounded-md border border-surface-border bg-surface text-sm focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
          </div>
          <div>
            <label className="label block mb-1.5">Description (optional)</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={2}
              className="w-full px-3 py-2 rounded-md border border-surface-border bg-surface text-sm focus:outline-none focus:ring-1 focus:ring-brand-500 resize-none"
            />
          </div>
          {error && (
            <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">
              {error}
            </div>
          )}
        </div>
        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel}>Cancel</Button>
          <Button
            variant="primary"
            disabled={!valid}
            loading={loading}
            onClick={() => onSubmit({
              packageName: packageName.trim(),
              displayName: displayName.trim(),
              description: description.trim() || null,
            })}
          >
            Create
          </Button>
        </div>
      </Card>
    </div>
  );
}

/* ─────────────────  Generic confirm dialog  ─────────────────── */

function ConfirmDialog({
  title, description, confirmLabel, disabled, tone, onCancel, onConfirm,
}: {
  title: string;
  description: React.ReactNode;
  confirmLabel: string;
  disabled?: boolean;
  tone?: "danger" | "default";
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-md">
        <div className="px-5 py-4 border-b border-surface-border">
          <div className="text-sm font-semibold">{title}</div>
        </div>
        <div className="p-5 text-xs text-ink-secondary">{description}</div>
        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel}>Cancel</Button>
          <Button
            variant={tone === "danger" ? "danger" : "primary"}
            disabled={disabled}
            onClick={onConfirm}
          >
            {confirmLabel}
          </Button>
        </div>
      </Card>
    </div>
  );
}
