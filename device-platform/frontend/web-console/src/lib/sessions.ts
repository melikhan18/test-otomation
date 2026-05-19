import { api } from "./api";

export type SessionView = {
  id: number;
  deviceId: number;
  userId: number;
  productId: number;
  status: "ACTIVE" | "ENDED";
  createdAt: string;
  endedAt: string | null;
  sessionToken: string | null;
  expiresAt: string | null;
};

export const sessionApi = {
  create: (deviceId: number) =>
    api.post<SessionView>("/api/sessions", { deviceId }).then((r) => r.data),
  get:    (id: number) => api.get<SessionView>(`/api/sessions/${id}`).then((r) => r.data),
  touch:  (id: number) => api.post<SessionView>(`/api/sessions/${id}/touch`).then((r) => r.data),
  end:    (id: number) => api.delete<void>(`/api/sessions/${id}`).then((r) => r.data),
  active: () => api.get<SessionView[]>("/api/sessions/active").then((r) => r.data),
};
