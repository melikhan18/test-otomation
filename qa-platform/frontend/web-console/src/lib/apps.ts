import { api } from "./api";

/* ───────────────────────────── Types ──────────────────────────────── */

export type AppSummary = {
  id: number;
  packageName: string;
  displayName: string;
  description: string | null;
  /** Base64-inline data URL parsed from the first uploaded APK; null if no APK yet. */
  iconData: string | null;
  /** Newest versionCode on file. Null when no version has been uploaded yet. */
  latestVersionCode: number | null;
  latestVersionName: string | null;
  versionCount: number;
  createdAt: string;
  updatedAt: string;
};

export type AppVersionView = {
  id: number;
  appId: number;
  versionCode: number;
  versionName: string | null;
  fileSizeBytes: number;
  sha256: string;
  /** Public MinIO URL the agent uses to download. */
  downloadUrl: string;
  notes: string | null;
  uploadedByUserId: number;
  uploadedAt: string;
};

export type AppView = AppSummary & {
  /** Full version history, newest versionCode first. Empty when the app has no APKs yet. */
  versions: AppVersionView[];
};

export type AppCreate = {
  packageName: string;
  displayName: string;
  description?: string | null;
};

export type AppUpdate = {
  displayName: string;
  description?: string | null;
};

/* ─────────────────────────────  API  ─────────────────────────────── */

export const appApi = {
  list:    () => api.get<AppSummary[]>("/api/automation/apps").then((r) => r.data),
  get:     (id: number) => api.get<AppView>(`/api/automation/apps/${id}`).then((r) => r.data),
  create:  (body: AppCreate) => api.post<AppView>("/api/automation/apps", body).then((r) => r.data),
  update:  (id: number, body: AppUpdate) =>
    api.put<AppView>(`/api/automation/apps/${id}`, body).then((r) => r.data),
  /** Soft-delete: backend sets archived_at; the row stops showing up in list(). */
  archive: (id: number) => api.delete<void>(`/api/automation/apps/${id}`).then((r) => r.data),

  /* ── Versions ────────────────────────────────────────────────── */

  listVersions: (appId: number) =>
    api.get<AppVersionView[]>(`/api/automation/apps/${appId}/versions`).then((r) => r.data),

  /**
   * Upload an APK. The server parses the manifest, validates packageName against the
   * parent app, computes SHA-256, and streams the bytes to MinIO. Returns the new
   * {@link AppVersionView} row.
   *
   * @param onProgress optional 0–1 progress callback (multipart upload progress)
   */
  uploadVersion: (
    appId: number,
    file: File,
    notes: string | null,
    onProgress?: (pct: number) => void,
  ) => {
    const fd = new FormData();
    fd.append("file", file);
    if (notes) fd.append("notes", notes);
    return api
      .post<AppVersionView>(`/api/automation/apps/${appId}/versions`, fd, {
        // Axios picks the right boundary; do NOT set Content-Type explicitly or
        // the form boundary header is lost and the server rejects the body.
        onUploadProgress: (e) => {
          if (!onProgress || !e.total) return;
          onProgress(e.loaded / e.total);
        },
      })
      .then((r) => r.data);
  },

  deleteVersion: (appId: number, versionId: number) =>
    api.delete<void>(`/api/automation/apps/${appId}/versions/${versionId}`).then((r) => r.data),
};
