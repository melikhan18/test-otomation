import { api } from "./api";

/* ─────────────────────────────  Types  ────────────────────────────── */

/** Per-project role. Company-level OWNER is represented by membership.owner=true. */
export type ProjectRole = "QA_MANAGER" | "TESTER";

export type CompanyView = {
  id: number;
  slug: string;
  name: string;
  /** Caller's role in the company — "OWNER", "MEMBER", or "VIEWER". */
  role: string;
  memberCount: number;
  projectCount: number;
  createdAt: string;
  /** Set when the company has been soft-deleted. Hidden from regular listings. */
  archivedAt: string | null;
};

export type CompanyCreate = { name: string; slug?: string };
export type CompanyUpdate = { name: string };

export type ProjectView = {
  id: number;
  companyId: number;
  slug: string;
  name: string;
  description: string | null;
  createdAt: string;
  archivedAt: string | null;
};

export type ProjectCreate = { name: string; slug?: string; description?: string };
export type ProjectUpdate = { name: string; description?: string };

export type ProjectGrantView = {
  projectId: number;
  projectSlug: string;
  projectName: string;
  role: ProjectRole;
};

export type ProjectGrantInput = { projectId: number; role: ProjectRole };

export type MemberView = {
  userId: number;
  username: string;
  email: string | null;
  owner: boolean;
  grants: ProjectGrantView[];
  joinedAt: string;
};

export type AddMember = {
  username: string;
  owner: boolean;
  grants: ProjectGrantInput[];
};

export type UpdateMember = {
  owner: boolean;
  grants: ProjectGrantInput[];
};

export type ProjectMemberView = {
  userId: number;
  username: string;
  /** "OWNER" (implicit via company), "QA_MANAGER", or "TESTER". */
  role: "OWNER" | ProjectRole;
  addedAt: string;
};

/* ─────────────────────────────  Companies  ───────────────────────── */

export const companyApi = {
  list:   () => api.get<CompanyView[]>("/api/companies").then((r) => r.data),
  get:    (id: number) => api.get<CompanyView>(`/api/companies/${id}`).then((r) => r.data),
  create: (body: CompanyCreate) => api.post<CompanyView>("/api/companies", body).then((r) => r.data),
  update: (id: number, body: CompanyUpdate) =>
    api.put<CompanyView>(`/api/companies/${id}`, body).then((r) => r.data),
  archive: (id: number) =>
    api.delete<void>(`/api/companies/${id}`).then((r) => r.data),

  /* ─── Platform admin ─── */
  adminListAll: () =>
    api.get<CompanyView[]>("/api/companies/admin/all").then((r) => r.data),
  unarchive: (id: number) =>
    api.post<CompanyView>(`/api/companies/${id}/unarchive`).then((r) => r.data),
};

/* ─────────────────────────────  Projects  ────────────────────────── */

export const projectApi = {
  list:   (companyId: number) =>
    api.get<ProjectView[]>(`/api/companies/${companyId}/projects`).then((r) => r.data),
  get:    (companyId: number, id: number) =>
    api.get<ProjectView>(`/api/companies/${companyId}/projects/${id}`).then((r) => r.data),
  create: (companyId: number, body: ProjectCreate) =>
    api.post<ProjectView>(`/api/companies/${companyId}/projects`, body).then((r) => r.data),
  update: (companyId: number, id: number, body: ProjectUpdate) =>
    api.put<ProjectView>(`/api/companies/${companyId}/projects/${id}`, body).then((r) => r.data),
  archive: (companyId: number, id: number) =>
    api.delete<void>(`/api/companies/${companyId}/projects/${id}`).then((r) => r.data),
};

/* ─────────────────────────────  Members  ─────────────────────────── */

export const memberApi = {
  list: (companyId: number) =>
    api.get<MemberView[]>(`/api/companies/${companyId}/members`).then((r) => r.data),

  /** Direct add by username. owner=true grants OWNER role; otherwise grants are required. */
  add: (companyId: number, body: AddMember) =>
    api.post<MemberView>(`/api/companies/${companyId}/members`, body).then((r) => r.data),

  /** Replace a member's OWNER flag + grants set. */
  update: (companyId: number, userId: number, body: UpdateMember) =>
    api.put<MemberView>(`/api/companies/${companyId}/members/${userId}`, body).then((r) => r.data),

  remove: (companyId: number, userId: number) =>
    api.delete<void>(`/api/companies/${companyId}/members/${userId}`).then((r) => r.data),

  /** Project grants the target user holds in this company. */
  userGrants: (companyId: number, userId: number) =>
    api.get<ProjectGrantView[]>(`/api/companies/${companyId}/members/${userId}/grants`).then((r) => r.data),

  /* ─── Per-project membership endpoints (used by the project access dialog) ─── */

  listProject: (companyId: number, projectId: number) =>
    api.get<ProjectMemberView[]>(`/api/companies/${companyId}/projects/${projectId}/members`).then((r) => r.data),
  addProject: (companyId: number, projectId: number, userId: number, role: ProjectRole) =>
    api.post<ProjectMemberView>(`/api/companies/${companyId}/projects/${projectId}/members`,
      { userId, role }).then((r) => r.data),
  changeProjectRole: (companyId: number, projectId: number, userId: number, role: ProjectRole) =>
    api.put<ProjectMemberView>(`/api/companies/${companyId}/projects/${projectId}/members/${userId}`,
      { role }).then((r) => r.data),
  removeProject: (companyId: number, projectId: number, userId: number) =>
    api.delete<void>(`/api/companies/${companyId}/projects/${projectId}/members/${userId}`).then((r) => r.data),
};
