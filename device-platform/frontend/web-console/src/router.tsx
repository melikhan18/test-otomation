import { Navigate, Outlet, createBrowserRouter, useParams, useSearchParams } from "react-router-dom";
import { useAuthStore } from "@/store/auth";
import LoginPage from "@/pages/LoginPage";
import DevicesPage from "@/pages/DevicesPage";
import SessionPage from "@/pages/SessionPage";
import ElementsPage from "@/pages/automation/ElementsPage";
import DataPage from "@/pages/automation/DataPage";
import WorkspacePage from "@/pages/automation/WorkspacePage";
import RunDetailPage from "@/pages/automation/RunDetailPage";
import SuiteRunDetailPage from "@/pages/automation/SuiteRunDetailPage";
import ReportsPage from "@/pages/automation/ReportsPage";
import AppLayout from "@/components/AppLayout";

function RequireAuth() {
  const accessToken = useAuthStore((s) => s.accessToken);
  if (!accessToken) return <Navigate to="/login" replace />;
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
  { path: "/login", element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: "/", element: <Navigate to="/devices" replace /> },
          { path: "/devices", element: <DevicesPage /> },
          { path: "/sessions/:sessionId", element: <SessionPage /> },

          { path: "/automation",                element: <Navigate to="/automation/workspace" replace /> },
          { path: "/automation/workspace",      element: <WorkspacePage /> },
          { path: "/automation/elements",       element: <ElementsPage /> },
          { path: "/automation/data",           element: <DataPage /> },
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
        ],
      },
    ],
  },
  { path: "*", element: <Navigate to="/" replace /> },
]);
