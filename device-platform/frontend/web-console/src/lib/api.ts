import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import { useAuthStore } from "@/store/auth";

export const api = axios.create({
  baseURL: "/",
  timeout: 15_000,
});

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
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

export type LoginResponse = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  userId: number;
  username: string;
  role: string;
  productId: number;
};

export const authApi = {
  login:   (username: string, password: string) =>
    api.post<LoginResponse>("/api/auth/login", { username, password }).then((r) => r.data),
  refresh: (refreshToken: string) =>
    api.post<LoginResponse>("/api/auth/refresh", { refreshToken }).then((r) => r.data),
  me:      () => api.get("/api/auth/me").then((r) => r.data),
};
