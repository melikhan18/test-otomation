import { create } from "zustand";
import { persist } from "zustand/middleware";
import {
  authApi, type CompanyMembership, type LoginResponse, type MeResponse, type SignupRequest,
} from "@/lib/api";

/**
 * Which testing product the user is currently looking at. The 4th tenancy
 * dimension — sits below Company → Project → Platform → Resources. Every API
 * call sends this as the `X-Platform` header; the gateway dispatches to the
 * matching backend stack (only ANDROID is live in F4; the rest land in F5+).
 */
export type Platform = "ANDROID" | "IOS" | "BACKEND" | "WEB";

type AuthState = {
  accessToken: string | null;
  refreshToken: string | null;
  userId: number | null;
  username: string | null;
  email: string | null;
  role: string | null;
  platformAdmin: boolean;
  productId: number | null;
  companies: CompanyMembership[];

  /** Selected company/project/platform for the current tab/window. */
  activeCompanyId: number | null;
  activeProjectId: number | null;
  activePlatform: Platform;

  login: (username: string, password: string) => Promise<void>;
  signup: (body: SignupRequest) => Promise<void>;
  logout: () => void;
  refresh: () => Promise<string | null>;
  /** Pull fresh memberships from the server (after invite accept / project add). */
  reloadMemberships: () => Promise<void>;

  setActiveCompany: (companyId: number) => void;
  setActiveProject: (projectId: number) => void;
  setActivePlatform: (platform: Platform) => void;
};

function pickInitialActive(companies: CompanyMembership[], prevCompanyId: number | null, prevProjectId: number | null) {
  // Prefer the previously-active selection if still valid, else fall back to the
  // first company with projects (or just the first company).
  const prevCompany = companies.find((c) => c.id === prevCompanyId);
  const company = prevCompany ?? companies[0] ?? null;
  if (!company) return { activeCompanyId: null, activeProjectId: null };
  const prevProject = company.projects.find((p) => p.id === prevProjectId);
  const project = prevProject ?? company.projects[0] ?? null;
  return {
    activeCompanyId: company.id,
    activeProjectId: project?.id ?? null,
  };
}

function applyLogin(set: any, get: any, r: LoginResponse | MeResponse) {
  const companies = r.companies ?? [];
  const prev = get();
  // Only preserve the previously-active selection if the *same* user is logging back
  // in (token refresh, /me reload). For a different user — including a fresh sign-up —
  // start fresh so we don't send X-Company-Id pointing at someone else's tenant.
  const sameUser = prev.userId != null && prev.userId === r.userId;
  const active = sameUser
    ? pickInitialActive(companies, prev.activeCompanyId, prev.activeProjectId)
    : pickInitialActive(companies, null, null);
  set({
    ...(("accessToken" in r) ? { accessToken: r.accessToken, refreshToken: r.refreshToken } : {}),
    userId: r.userId,
    username: r.username,
    email: r.email ?? null,
    role: r.role,
    platformAdmin: r.platformAdmin,
    productId: r.productId,
    companies,
    ...active,
  });
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      userId: null,
      username: null,
      email: null,
      role: null,
      platformAdmin: false,
      productId: null,
      companies: [],
      activeCompanyId: null,
      activeProjectId: null,
      // Default to ANDROID — the only platform with a live backend stack today.
      // The switcher renders IOS/BACKEND/WEB as "coming soon" so they can't be
      // selected until their respective stacks land (F5–F8 + per-platform work).
      activePlatform: "ANDROID",

      async login(username, password) {
        const r = await authApi.login(username, password);
        applyLogin(set, get, r);
      },

      async signup(body) {
        const r = await authApi.signup(body);
        applyLogin(set, get, r);
      },

      logout() {
        set({
          accessToken: null, refreshToken: null,
          userId: null, username: null, email: null, role: null,
          platformAdmin: false, productId: null,
          companies: [], activeCompanyId: null, activeProjectId: null,
        });
      },

      async refresh() {
        const rt = get().refreshToken;
        if (!rt) return null;
        try {
          const r = await authApi.refresh(rt);
          applyLogin(set, get, r);
          return r.accessToken;
        } catch {
          get().logout();
          return null;
        }
      },

      async reloadMemberships() {
        try {
          const r = await authApi.me();
          applyLogin(set, get, r);
        } catch {
          // Network blip — keep prior memberships.
        }
      },

      setActiveCompany(companyId) {
        const company = get().companies.find((c) => c.id === companyId);
        if (!company) return;
        // Switching company resets the project to the first one in the new company.
        const firstProject = company.projects[0] ?? null;
        set({ activeCompanyId: company.id, activeProjectId: firstProject?.id ?? null });
      },

      setActiveProject(projectId) {
        const company = get().companies.find((c) => c.id === get().activeCompanyId);
        if (!company) return;
        if (!company.projects.find((p) => p.id === projectId)) return;
        set({ activeProjectId: projectId });
      },

      setActivePlatform(platform) {
        set({ activePlatform: platform });
      },
    }),
    {
      name: "device-platform-auth",
      // Persist the bits we actually want to remember across reloads (including
      // the active company/project so refresh keeps the user where they were).
      partialize: (s) => ({
        accessToken: s.accessToken,
        refreshToken: s.refreshToken,
        userId: s.userId,
        username: s.username,
        email: s.email,
        role: s.role,
        platformAdmin: s.platformAdmin,
        productId: s.productId,
        companies: s.companies,
        activeCompanyId: s.activeCompanyId,
        activeProjectId: s.activeProjectId,
        activePlatform: s.activePlatform,
      }),
    },
  ),
);

/** Convenience selectors. */
export function useActiveCompany(): CompanyMembership | null {
  return useAuthStore((s) => s.companies.find((c) => c.id === s.activeCompanyId) ?? null);
}

export function useActiveProject() {
  return useAuthStore((s) => {
    const company = s.companies.find((c) => c.id === s.activeCompanyId);
    if (!company) return null;
    return company.projects.find((p) => p.id === s.activeProjectId) ?? null;
  });
}

/**
 * Effective role for the caller in the **currently active project**.
 *
 *   - OWNER       : company OWNER (or platform admin) — implicit on every project
 *   - QA_MANAGER  : QA_MANAGER grant on the *active* project
 *   - TESTER      : TESTER grant on the *active* project
 *   - null        : no active company/project, or no grant on the active one
 *
 * Why active-project-scoped: a user can be QA_MANAGER on BIP and TESTER on
 * Sardis simultaneously. With BIP active they should be able to edit it; the
 * moment they switch to Sardis those permissions go away even though they
 * still hold the QA_MANAGER grant elsewhere.
 *
 * UI affordances (sidebar links, device-access dialog, etc.) gate on this.
 * Backend still re-checks every mutation — this only drives what's *visible*.
 */
export type EffectiveRole = "OWNER" | "QA_MANAGER" | "TESTER" | null;

export function useEffectiveRole(): EffectiveRole {
  return useAuthStore((s) => {
    if (s.platformAdmin) return "OWNER";
    const c = s.companies.find((x) => x.id === s.activeCompanyId);
    if (!c) return null;
    if (c.owner) return "OWNER";
    const p = c.projects.find((p) => p.id === s.activeProjectId);
    if (!p) return null;
    if (p.role === "OWNER")      return "OWNER";
    if (p.role === "QA_MANAGER") return "QA_MANAGER";
    if (p.role === "TESTER")     return "TESTER";
    return null;
  });
}
