import { api } from "./api";

export type DeviceStatus = "ONLINE" | "OFFLINE" | "IN_USE";

export type Device = {
  id: number;
  productId: number;
  serial: string;
  manufacturer: string;
  model: string;
  androidVersion: string;
  screenWidth: number;
  screenHeight: number;
  agentVersion: string | null;
  enrolledAt: string;
  lastSeenAt: string | null;
  status: DeviceStatus;
  currentSessionId: number | null;
};

export type EnrollmentToken = { token: string; expiresAt: string };

export const deviceApi = {
  list:    () => api.get<Device[]>("/api/devices").then((r) => r.data),
  get:     (id: number) => api.get<Device>(`/api/devices/${id}`).then((r) => r.data),
  issueEnrollmentToken: () =>
    api.post<EnrollmentToken>("/api/devices/enrollment-tokens").then((r) => r.data),
};
