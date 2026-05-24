import { api } from "./api";

export type DeviceStatus = "ONLINE" | "OFFLINE" | "IN_USE";

export type Device = {
  id: number;
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

/** Cross-tenant device view used by /admin/devices. Carries the company id +
 *  restricted flag + how many projects currently see this device. */
export type AdminDevice = Device & {
  companyId: number | null;
  restricted: boolean;
  accessProjectCount: number;
};

export type EnrollmentToken = { token: string; expiresAt: string };

export type DeviceAccess = {
  deviceId: number;
  restricted: boolean;
  projectIds: number[];
};

export const deviceApi = {
  list:    () => api.get<Device[]>("/api/devices").then((r) => r.data),
  get:     (id: number) => api.get<Device>(`/api/devices/${id}`).then((r) => r.data),
  issueEnrollmentToken: () =>
    api.post<EnrollmentToken>("/api/devices/enrollment-tokens").then((r) => r.data),

  getAccess:    (id: number) =>
    api.get<DeviceAccess>(`/api/devices/${id}/access`).then((r) => r.data),
  updateAccess: (id: number, body: { restricted: boolean; projectIds: number[] }) =>
    api.put<DeviceAccess>(`/api/devices/${id}/access`, body).then((r) => r.data),
};

export const adminDeviceApi = {
  listAll: () => api.get<AdminDevice[]>("/api/devices/admin/all").then((r) => r.data),
  /**
   * Move a device to a different tenant. Clears the device's per-project
   * whitelist server-side because those rows reference the old company's
   * projects and would dangle otherwise.
   */
  reassign: (id: number, companyId: number) =>
    api.patch<Device>(`/api/devices/admin/${id}/company`, { companyId }).then((r) => r.data),
};
