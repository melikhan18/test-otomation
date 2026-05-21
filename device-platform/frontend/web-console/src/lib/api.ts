import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import { useAuthStore } from "@/store/auth";

export const api = axios.create({
  baseURL: "/",
  timeout: 15_000,
});

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const state = useAuthStore.getState();
  config.headers = config.headers ?? {};
  if (state.accessToken) {
    config.headers.Authorization = `Bearer ${state.accessToken}`;
  }
  // Active company / project — services scope their queries off these once they're
  // multi-tenant. Header-based so existing URL routing keeps working unchanged.
  if (state.activeCompanyId != null) {
    config.headers["X-Company-Id"] = String(state.activeCompanyId);
  }
  if (state.activeProjectId != null) {
    config.headers["X-Project-Id"] = String(state.activeProjectId);
  }
  return config;
});

let refreshPromise: Promise<string | null> | null = null;

api.interceptors.response.use(
  (r) => r,
  async (err: AxiosError) => {
    const original = err.config as InternalAxiosRequestConfig & { _retried?: boolean };
    if (err.response?.status === 401 && !original._retried && original.url !== "/api/auth/refresh") {
      original._retried = true;
      if (!refreshPromise) {
        refreshPromise = useAuthStore.getState().refresh().finally(() => { refreshPromise = null; });
      }
      const newToken = await refreshPromise;
      if (newToken) {
        original.headers = original.headers ?? {};
        original.headers.Authorization = `Bearer ${newToken}`;
        return api.request(original);
      }
    }
    return Promise.reject(err);
  },
);

/** Per-project role grant returned in the login envelope. */
export type ProjectRole = "QA_MANAGER" | "TESTER";

/** "OWNER" only appears when the company-level owner flag is set. */
export type ProjectAccessRole = "OWNER" | ProjectRole;

export type ProjectAccess = {
  id: number;
  slug: string;
  name: string;
  role: ProjectAccessRole;
};

export type CompanyMembership = {
  id: number;
  slug: string;
  name: string;
  /** True → company OWNER (implicit access to every active project). */
  owner: boolean;
  /**
   * Projects the caller can see in this company:
   *   - OWNER → every active project, each with role="OWNER".
   *   - MEMBER → only projects they have an explicit grant on, with the actual role.
   */
  projects: ProjectAccess[];
  joinedAt: string;
};

export type LoginResponse = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  userId: number;
  username: string;
  email: string | null;
  role: string;
  platformAdmin: boolean;
  productId: number;
  companies: CompanyMembership[];
};

export type MeResponse = {
  userId: number;
  username: string;
  email: string | null;
  role: string;
  platformAdmin: boolean;
  productId: number;
  companies: CompanyMembership[];
};

export type SignupRequest = {
  username: string;
  email: string;
  password: string;
  /** Optional — if set, a brand-new company is provisioned and the user becomes OWNER. */
  createCompanyName?: string;
};

export type ProfileUpdate = {
  email?: string;
  newPassword?: string;
  currentPassword: string;
};

export const authApi = {
  login:   (username: string, password: string) =>
    api.post<LoginResponse>("/api/auth/login", { username, password }).then((r) => r.data),
  signup:  (body: SignupRequest) =>
    api.post<LoginResponse>("/api/auth/signup", body).then((r) => r.data),
  refresh: (refreshToken: string) =>
    api.post<LoginResponse>("/api/auth/refresh", { refreshToken }).then((r) => r.data),
  me:      () => api.get<MeResponse>("/api/auth/me").then((r) => r.data),
  updateProfile: (body: ProfileUpdate) =>
    api.patch<MeResponse>("/api/auth/me", body).then((r) => r.data),
};

/* ───────────────────────────── Admin user management ──────────────────── */

export type AdminUser = {
  id: number;
  username: string;
  email: string | null;
  role: string;
  platformAdmin: boolean;
  enabled: boolean;
  createdAt: string;
  companyCount: number;
};

export const adminUserApi = {
  list:   () => api.get<AdminUser[]>("/api/admin/users").then((r) => r.data),
  create: (body: { username: string; email?: string; password: string; platformAdmin: boolean }) =>
    api.post<AdminUser>("/api/admin/users", body).then((r) => r.data),
  update: (id: number, body: { email?: string; enabled?: boolean; platformAdmin?: boolean }) =>
    api.patch<AdminUser>(`/api/admin/users/${id}`, body).then((r) => r.data),
  resetPassword: (id: number, newPassword: string) =>
    api.post<void>(`/api/admin/users/${id}/reset-password`, { newPassword }).then((r) => r.data),
};
