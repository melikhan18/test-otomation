import { Navigate, Outlet, createBrowserRouter } from "react-router-dom";
import { useAuthStore } from "@/store/auth";
import LoginPage from "@/pages/LoginPage";
import DevicesPage from "@/pages/DevicesPage";
import SessionPage from "@/pages/SessionPage";
import AppLayout from "@/components/AppLayout";

function RequireAuth() {
  const accessToken = useAuthStore((s) => s.accessToken);
  if (!accessToken) return <Navigate to="/login" replace />;
  return <Outlet />;
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
        ],
      },
    ],
  },
  { path: "*", element: <Navigate to="/" replace /> },
]);
