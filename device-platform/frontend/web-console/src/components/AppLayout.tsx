import { Outlet, useLocation } from "react-router-dom";
import Sidebar from "./Sidebar";
import NoWorkspaceGate from "./NoWorkspaceGate";
import { useTenancyRefetch } from "@/hooks/useTenancyRefetch";
import { useAuthStore } from "@/store/auth";

// Routes that don't need a company context — keep them reachable for users
// who haven't joined a workspace yet.
const TENANCY_FREE_PREFIXES = ["/account", "/admin"];

export default function AppLayout() {
  // Refetch all queries whenever the active company/project changes so
  // Workspace / Reports / Elements / Test data reload in the new scope.
  useTenancyRefetch();

  const location = useLocation();
  const companies = useAuthStore((s) => s.companies);
  const tenancyFree = TENANCY_FREE_PREFIXES.some((p) => location.pathname.startsWith(p));
  const showGate = companies.length === 0 && !tenancyFree;

  // Lock the outer shell to the viewport and scroll only the main pane — that
  // way the sidebar (and its TopBar inside the pane) stay fixed while long
  // pages can scroll freely on the right.
  return (
    <div className="h-screen flex overflow-hidden">
      <Sidebar />
      <div className="flex-1 min-w-0 flex flex-col overflow-y-auto">
        {showGate ? <NoWorkspaceGate /> : <Outlet />}
      </div>
    </div>
  );
}
