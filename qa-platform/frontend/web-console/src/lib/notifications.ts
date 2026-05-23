import { api } from "./api";
import type { ProjectGrantInput } from "./tenancy";

export type NotificationStatus =
  | "UNREAD" | "READ" | "DISMISSED" | "ACCEPTED" | "DECLINED";

export type NotificationType =
  | "COMPANY_INVITATION"
  | "RUN_COMPLETED"
  | "RUN_FAILED"
  | "SUITE_RUN_COMPLETED"
  | "MEMBER_ADDED"
  | "ROLE_CHANGED"
  | "PROJECT_INVITED"
  | string;   // forward-compat: backend may emit new types we don't know yet

export type NotificationView = {
  id: number;
  type: NotificationType;
  status: NotificationStatus;
  payload: Record<string, any>;
  actorUserId: number | null;
  actorUsername: string | null;
  createdAt: string;
  resolvedAt: string | null;
  expiresAt: string | null;
};

export const notificationApi = {
  list:        () => api.get<NotificationView[]>("/api/notifications").then((r) => r.data),
  unreadCount: () => api.get<{ count: number }>("/api/notifications/unread-count").then((r) => r.data.count),
  markRead:    (id: number) => api.patch<NotificationView>(`/api/notifications/${id}/read`).then((r) => r.data),
  markAllRead: () => api.post<void>("/api/notifications/mark-all-read").then((r) => r.data),
  dismiss:     (id: number) => api.patch<NotificationView>(`/api/notifications/${id}/dismiss`).then((r) => r.data),

  accept:      (id: number) => api.post<void>(`/api/notifications/${id}/accept`).then((r) => r.data),
  decline:     (id: number) => api.post<void>(`/api/notifications/${id}/decline`).then((r) => r.data),

  /**
   * Email-based invite. Either owner=true (company OWNER, grants ignored) or a
   * non-empty list of per-project grants.
   */
  invite:      (companyId: number, email: string, owner: boolean, grants: ProjectGrantInput[]) =>
    api.post(`/api/companies/${companyId}/members/invite`, { email, owner, grants }).then((r) => r.data),
};

/**
 * Open an SSE channel that pushes fresh notifications without polling. Returns
 * the EventSource so the caller can attach event listeners + .close() on unmount.
 *
 * Auth: EventSource can't set headers, so we pass the access token as a query
 * param. Server-side that's the only un-filtered route.
 */
export function openNotificationStream(accessToken: string): EventSource {
  const url = `/api/notifications/stream?access_token=${encodeURIComponent(accessToken)}`;
  return new EventSource(url, { withCredentials: false });
}
