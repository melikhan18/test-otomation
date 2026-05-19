import { create } from "zustand";
import { persist } from "zustand/middleware";
import { authApi, type LoginResponse } from "@/lib/api";

type AuthState = {
  accessToken: string | null;
  refreshToken: string | null;
  userId: number | null;
  username: string | null;
  role: string | null;
  productId: number | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  refresh: () => Promise<string | null>;
};

function applyLogin(set: any, r: LoginResponse) {
  set({
    accessToken: r.accessToken,
    refreshToken: r.refreshToken,
    userId: r.userId,
    username: r.username,
    role: r.role,
    productId: r.productId,
  });
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      userId: null,
      username: null,
      role: null,
      productId: null,
      async login(username, password) {
        const r = await authApi.login(username, password);
        applyLogin(set, r);
      },
      logout() {
        set({ accessToken: null, refreshToken: null, userId: null, username: null, role: null, productId: null });
      },
      async refresh() {
        const rt = get().refreshToken;
        if (!rt) return null;
        try {
          const r = await authApi.refresh(rt);
          applyLogin(set, r);
          return r.accessToken;
        } catch {
          get().logout();
          return null;
        }
      },
    }),
    { name: "device-platform-auth" },
  ),
);
