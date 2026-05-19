import { Navigate, Outlet, createBrowserRouter, useParams } from "react-router-dom";
import { useAuthStore } from "@/store/auth";
import LoginPage from "@/pages/LoginPage";
import DevicesPage from "@/pages/DevicesPage";
import SessionPage from "@/pages/SessionPage";
import ElementsPage from "@/pages/automation/ElementsPage";
import DataPage from "@/pages/automation/DataPage";
import WorkspacePage from "@/pages/automation/WorkspacePage";
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
