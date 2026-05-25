import { Navigate, Outlet, createBrowserRouter, useParams, useSearchParams } from "react-router-dom";
import { useAuthStore, useEffectiveRole, type Platform } from "@/store/auth";
import LoginPage from "@/pages/LoginPage";
import SignupPage from "@/pages/SignupPage";
import AccountPage from "@/pages/AccountPage";
import AdminUsersPage from "@/pages/AdminUsersPage";
import AdminDevicesPage from "@/pages/AdminDevicesPage";
import AdminCompaniesPage from "@/pages/AdminCompaniesPage";
import CompanySettingsPage from "@/pages/CompanySettingsPage";
import ProjectsSettingsPage from "@/pages/ProjectsSettingsPage";
import ProjectDetailPage from "@/pages/ProjectDetailPage";
import DevicesPage from "@/pages/DevicesPage";
import SessionPage from "@/pages/SessionPage";
import ElementsPage from "@/pages/automation/ElementsPage";
import DataPage from "@/pages/automation/DataPage";
import AppsPage from "@/pages/automation/AppsPage";
import WorkspacePage from "@/pages/automation/WorkspacePage";
import RunDetailPage from "@/pages/automation/RunDetailPage";
import SuiteRunDetailPage from "@/pages/automation/SuiteRunDetailPage";
import ReportsPage from "@/pages/automation/ReportsPage";
import WebWorkspacePage from "@/pages/automation/WebWorkspacePage";
import WebElementsPage from "@/pages/automation/WebElementsPage";
import WebDataPage from "@/pages/automation/WebDataPage";
import MembersPage from "@/pages/MembersPage";
import AppLayout from "@/components/AppLayout";

function RequireAuth() {
  const accessToken = useAuthStore((s) => s.accessToken);
  if (!accessToken) return <Navigate to="/login" replace />;
  return <Outlet />;
}

/** Gates routes that only platform admins should reach (e.g. /admin/users). */
function RequirePlatformAdmin() {
  const platformAdmin = useAuthStore((s) => s.platformAdmin);
  if (!platformAdmin) return <Navigate to="/" replace />;
  return <Outlet />;
}

/**
 * Route-level role gating for the company-scoped settings pages. {@code OWNER}
 * locks down rename / archive / members; {@code MANAGER} (OWNER or QA_MANAGER
 * on at least one project) gates the project list + project detail.
 *
 * Pages also hide themselves from the sidebar at the same threshold, so this is
 * the defense-in-depth layer that catches direct URL hits (bookmarks, deep
 * links) for users who shouldn't be there.
 */
function RequireRole({ minRole }: { minRole: "OWNER" | "MANAGER" }) {
  const role = useEffectiveRole();
  const allowed =
    minRole === "OWNER"   ? role === "OWNER"
                          : role === "OWNER" || role === "QA_MANAGER";
  if (!allowed) return <Navigate to="/" replace />;
  return <Outlet />;
}

/**
 * Route-level platform gate. Stops the user from sitting on a /automation/web
 * URL after switching to ANDROID (the page would render but POSTs would carry
 * WEB-shaped bodies to the Android backend and fail with "deviceId: required").
 * The redirect target is the matching home for whichever platform is active.
 */
function RequirePlatform({ platform }: { platform: Platform }) {
  const active = useAuthStore((s) => s.activePlatform);
  if (active !== platform) {
    const home =
      active === "WEB"     ? "/automation/web"
    : active === "ANDROID" ? "/devices"
    :                        "/";
    return <Navigate to={home} replace />;
  }
  return <Outlet />;
}

/** Sends `/automation/scenarios/:scenarioId` and `/automation/suites/:suiteId`
 *  into the unified workspace so old bookmarks keep working. */
function RedirectScenario() {
  const { scenarioId } = useParams();
  return <Navigate to={`/automation/workspace?scenario=${scenarioId}`} replace />;
}
function RedirectSuite() {
  const { suiteId } = useParams();
  return <Navigate to={`/automation/workspace?suite=${suiteId}`} replace />;
}
/** /automation/runs?scenarioId=… → /automation/reports?tab=runs[&scenarioId=…] */
function RedirectRuns() {
  const [sp] = useSearchParams();
  const scenarioId = sp.get("scenarioId");
  const next = new URLSearchParams({ tab: "runs" });
  if (scenarioId) next.set("scenarioId", scenarioId);
  return <Navigate to={`/automation/reports?${next.toString()}`} replace />;
}

export const router = createBrowserRouter([
  { path: "/login",  element: <LoginPage /> },
  { path: "/signup", element: <SignupPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: "/", element: <Navigate to="/devices" replace /> },
          { path: "/devices", element: <DevicesPage /> },
          { path: "/sessions/:sessionId", element: <SessionPage /> },

          { path: "/account", element: <AccountPage /> },

          // OWNER-only: company rename / archive + company-wide members matrix.
          {
            element: <RequireRole minRole="OWNER" />,
            children: [
              { path: "/settings/company", element: <CompanySettingsPage /> },
              { path: "/settings/members", element: <MembersPage /> },
            ],
          },
          // OWNER + QA_MANAGER: project list and per-project settings (project
          // detail). TESTERs work via the automation pages and don't need this.
          {
            element: <RequireRole minRole="MANAGER" />,
            children: [
              { path: "/settings/projects",     element: <ProjectsSettingsPage /> },
              { path: "/settings/projects/:id", element: <ProjectDetailPage /> },
            ],
          },

          {
            element: <RequirePlatformAdmin />,
            children: [
              { path: "/admin/users",     element: <AdminUsersPage /> },
              { path: "/admin/devices",   element: <AdminDevicesPage /> },
              { path: "/admin/companies", element: <AdminCompaniesPage /> },
            ],
          },

          { path: "/automation",                element: <Navigate to="/automation/workspace" replace /> },
          { path: "/automation/workspace",      element: <WorkspacePage /> },
          { path: "/automation/elements",       element: <ElementsPage /> },
          { path: "/automation/data",           element: <DataPage /> },
          { path: "/automation/apps",           element: <AppsPage /> },
          // Reports is the unified hub — suite runs, all runs, scenarios tabs live here.
          { path: "/automation/reports",                element: <ReportsPage /> },
          // Legacy path: /automation/runs?... preserves scenario filter when redirecting.
          { path: "/automation/runs",                   element: <RedirectRuns /> },
          { path: "/automation/runs/:runId",            element: <RunDetailPage /> },
          { path: "/automation/suite-runs/:suiteRunId", element: <SuiteRunDetailPage /> },

          // Legacy paths — redirect into the workspace with the right selection.
          { path: "/automation/scenarios",                element: <Navigate to="/automation/workspace" replace /> },
          { path: "/automation/scenarios/:scenarioId",    element: <RedirectScenario /> },
          { path: "/automation/suites",                   element: <Navigate to="/automation/workspace" replace /> },
          { path: "/automation/suites/:suiteId",          element: <RedirectSuite /> },

          // ── Web platform (server-side Playwright) ──────────────────────────
          // Whole subtree gated on activePlatform === "WEB" so users can't end
          // up on a stale WEB page after switching to Android.
          {
            element: <RequirePlatform platform="WEB" />,
            children: [
              { path: "/automation/web",          element: <WebWorkspacePage /> },
              { path: "/automation/web/elements", element: <WebElementsPage /> },
              { path: "/automation/web/data",     element: <WebDataPage /> },
              // Legacy: /automation/web/suites is consolidated into the workspace.
              { path: "/automation/web/suites",   element: <Navigate to="/automation/web" replace /> },
            ],
          },
        ],
      },
    ],
  },
  { path: "*", element: <Navigate to="/" replace /> },
]);
