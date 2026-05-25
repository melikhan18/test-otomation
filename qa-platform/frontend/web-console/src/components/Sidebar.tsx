import { useEffect, useState } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import {
  BarChart3, Building2, ChevronLeft, ChevronRight, Database, FolderKanban, FolderOpen,
  LogOut, Monitor, Moon, Package, ShieldCheck, Smartphone, Sun, Target, UserCircle2,
  UserCog, Users,
} from "lucide-react";
import { cn } from "@/lib/cn";
import { useAuthStore, useEffectiveRole, type EffectiveRole, type Platform } from "@/store/auth";
import { useThemeStore, type ThemeMode } from "@/theme";
import WorkspaceSwitcher from "@/components/WorkspaceSwitcher";

type NavItem = { to: string; label: string; icon: React.ReactNode };
type NavSection = { title: string; items: NavItem[] };

function buildSections(platformAdmin: boolean, role: EffectiveRole, platform: Platform): NavSection[] {
  const isOwner   = role === "OWNER";
  const isManager = role === "QA_MANAGER" || isOwner;

  // Platform-aware Automation section. Both Android and Web ship the full
  // workspace/elements/test-data parity so users don't relearn the layout
  // when they switch platforms. Web has no `Apps` (no APK install phase);
  // suites live inside the workspace tree just like Android.
  const automationItems: NavItem[] = platform === "WEB"
    ? [
        { to: "/automation/web",          label: "Workspace", icon: <FolderKanban size={16} /> },
        { to: "/automation/reports",      label: "Reports",   icon: <BarChart3 size={16} /> },
        { to: "/automation/web/elements", label: "Elements",  icon: <Target size={16} /> },
        { to: "/automation/web/data",     label: "Test data", icon: <Database size={16} /> },
      ]
    : [
        { to: "/automation/workspace", label: "Workspace", icon: <FolderKanban size={16} /> },
        { to: "/automation/reports",   label: "Reports",   icon: <BarChart3 size={16} /> },
        { to: "/automation/elements",  label: "Elements",  icon: <Target size={16} /> },
        { to: "/automation/data",      label: "Test data", icon: <Database size={16} /> },
        { to: "/automation/apps",      label: "Apps",      icon: <Package size={16} /> },
      ];

  // Top-level "Devices" link only makes sense for platforms with physical
  // devices (Android today; iOS once it lands). Web has no device list.
  const workspaceItems: NavItem[] = platform === "WEB"
    ? []
    : [{ to: "/devices", label: "Devices", icon: <Smartphone size={16} /> }];

  const sections: NavSection[] = [];
  if (workspaceItems.length > 0) sections.push({ title: "Workspace", items: workspaceItems });
  sections.push({ title: "Automation", items: automationItems });

  // Settings — hide everything except Account from TESTERs. QA_MANAGERs keep
  // Projects (so they can drill into the one they manage); Company + Members
  // are OWNER-only.
  const settingsItems: NavItem[] = [];
  if (isOwner)   settingsItems.push({ to: "/settings/company",  label: "Company",  icon: <Building2 size={16} /> });
  if (isManager) settingsItems.push({ to: "/settings/projects", label: "Projects", icon: <FolderOpen size={16} /> });
  if (isOwner)   settingsItems.push({ to: "/settings/members",  label: "Members",  icon: <Users size={16} /> });
  settingsItems.push({ to: "/account", label: "Account", icon: <UserCircle2 size={16} /> });
  sections.push({ title: "Settings", items: settingsItems });

  if (platformAdmin) {
    sections.push({
      title: "Platform",
      items: [
        { to: "/admin/companies", label: "Companies", icon: <Building2 size={16} /> },
        { to: "/admin/users",     label: "Users",     icon: <UserCog size={16} /> },
        { to: "/admin/devices",   label: "Devices",   icon: <Smartphone size={16} /> },
      ],
    });
  }

  return sections;
}

/* ───────────────────────  Collapse persistence  ───────────────────────
 * Survives reloads via localStorage. SSR-safe (window check) even though
 * Vite doesn't SSR — keeps the hook portable. */
const COLLAPSE_KEY = "qp.sidebar.collapsed";

function useSidebarCollapsed(): [boolean, (next: boolean) => void] {
  const [collapsed, setCollapsed] = useState<boolean>(() => {
    if (typeof window === "undefined") return false;
    return window.localStorage.getItem(COLLAPSE_KEY) === "1";
  });
  useEffect(() => {
    try { window.localStorage.setItem(COLLAPSE_KEY, collapsed ? "1" : "0"); }
    catch { /* private browsing / quota — ignore */ }
  }, [collapsed]);
  return [collapsed, setCollapsed];
}

export default function Sidebar() {
  const nav = useNavigate();
  // Zustand v5: selector returning a new object every render triggers an infinite
  // re-render loop. Subscribe to each field individually instead.
  const username = useAuthStore((s) => s.username);
  const role = useAuthStore((s) => s.role);
  const platformAdmin = useAuthStore((s) => s.platformAdmin);
  const activePlatform = useAuthStore((s) => s.activePlatform);
  const logout = useAuthStore((s) => s.logout);
  const effectiveRole = useEffectiveRole();
  const [collapsed, setCollapsed] = useSidebarCollapsed();

  const sections = buildSections(platformAdmin, effectiveRole, activePlatform);

  return (
    <aside className={cn(
      "hidden lg:flex flex-col shrink-0 border-r border-surface-border bg-surface-raised overflow-hidden",
      "transition-[width] duration-200 ease-out",
      collapsed ? "w-16" : "w-64",
    )}>
      {/* Top: brand header + workspace picker. Header is the only row that
       *  always has the collapse toggle — keeps the affordance reachable
       *  in both states without claiming nav space. */}
      <div className="shrink-0 border-b border-surface-border bg-surface-raised">
        <div className={cn(
          "h-16 flex items-center border-b border-surface-border",
          collapsed ? "justify-center px-2" : "gap-2.5 px-5",
        )}>
          <div className="h-8 w-8 rounded-lg bg-brand-500/10 border border-brand-500/30 flex items-center justify-center shrink-0">
            <Smartphone size={16} className="text-brand-400" />
          </div>
          {!collapsed && (
            <>
              <div className="flex-1 min-w-0">
                <div className="text-sm font-semibold leading-tight text-ink-primary">QA Platform</div>
                <div className="text-[10px] text-ink-muted leading-tight">Multi-platform QA Cloud</div>
              </div>
              <button
                onClick={() => setCollapsed(true)}
                className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted transition-colors"
                title="Collapse sidebar"
                aria-label="Collapse sidebar"
              >
                <ChevronLeft size={14} />
              </button>
            </>
          )}
        </div>
        {/* When collapsed, the workspace switcher is too dense to be useful;
         *  show a dedicated expand button instead. */}
        {collapsed ? (
          <button
            onClick={() => setCollapsed(false)}
            className="w-full flex items-center justify-center py-2 text-ink-muted hover:text-ink-primary hover:bg-surface-muted transition-colors"
            title="Expand sidebar"
            aria-label="Expand sidebar"
          >
            <ChevronRight size={14} />
          </button>
        ) : (
          <div className="pb-3">
            <WorkspaceSwitcher />
          </div>
        )}
      </div>

      {/* Scrollable nav. Same fade mask in both modes; padding shrinks
       *  in collapsed mode so the icons centre cleanly. */}
      <div className="flex-1 min-h-0 relative">
        <nav
          className={cn(
            "absolute inset-0 overflow-y-auto py-4 space-y-5",
            collapsed ? "px-1.5" : "px-3",
          )}
          style={{
            maskImage: "linear-gradient(to bottom, transparent 0, black 24px, black calc(100% - 24px), transparent 100%)",
            WebkitMaskImage: "linear-gradient(to bottom, transparent 0, black 24px, black calc(100% - 24px), transparent 100%)",
          }}
        >
          {sections.map((section) => (
            <div key={section.title}>
              {!collapsed && (
                <div className="label px-2 mb-2">{section.title}</div>
              )}
              <div className="space-y-0.5">
                {section.items.map((it) => (
                  <NavLink
                    key={it.to}
                    to={it.to}
                    // `end` on every item — none of our nav routes use
                    // descendant matching (Workspace, Elements, Test data
                    // are siblings, not parent/child), so without this
                    // `/automation/web` lights up while we're on
                    // `/automation/web/elements`.
                    end
                    title={collapsed ? it.label : undefined}
                    className={({ isActive }) => cn(
                      "group relative flex items-center rounded-md text-sm",
                      "transition-[background-color,color] duration-150 ease-out",
                      collapsed ? "justify-center px-0 py-2" : "gap-3 px-2.5 py-2",
                      isActive
                        ? "bg-surface-muted text-ink-primary"
                        : "text-ink-secondary hover:text-ink-primary hover:bg-surface-muted/60",
                    )}
                  >
                    {({ isActive }) => (
                      <>
                        {/* Active-state left bar — kept in both modes; in
                         *  collapsed mode it's the only visual feedback
                         *  besides the background tint. */}
                        <span
                          aria-hidden
                          className={cn(
                            "absolute left-0 top-1.5 bottom-1.5 w-[2px] rounded-r bg-brand-500",
                            "transition-[opacity,transform] duration-200 ease-out",
                            isActive ? "opacity-100 translate-x-0" : "opacity-0 -translate-x-1",
                          )}
                        />
                        <span className={cn(
                          "transition-colors duration-150",
                          isActive ? "text-brand-400" : "text-ink-muted group-hover:text-ink-secondary",
                        )}>
                          {it.icon}
                        </span>
                        {!collapsed && <span>{it.label}</span>}
                      </>
                    )}
                  </NavLink>
                ))}
              </div>
            </div>
          ))}
        </nav>
      </div>

      {/* Bottom: theme toggle + identity. Collapsed mode shows just the
       *  avatar + logout — theme toggle is hidden (one less affordance to
       *  cram into 64px). */}
      <div className="shrink-0 border-t border-surface-border p-3 space-y-2 bg-surface-raised">
        {!collapsed && <ThemeToggle />}
        <div className={cn(
          "flex items-center rounded-md",
          collapsed ? "justify-center" : "gap-2.5 px-2 py-2",
        )}>
          <div className="h-8 w-8 rounded-full bg-brand-500/20 text-brand-300 text-xs font-semibold flex items-center justify-center shrink-0"
               title={collapsed ? `${username}${platformAdmin ? " (admin)" : ""}` : undefined}>
            {(username ?? "?").slice(0, 1).toUpperCase()}
          </div>
          {!collapsed && (
            <>
              <div className="flex-1 min-w-0">
                <div className="text-xs font-medium truncate text-ink-primary">{username}</div>
                <div className="text-[10px] text-ink-muted flex items-center gap-1">
                  {platformAdmin ? <ShieldCheck size={10} className="text-brand-400" /> : null}
                  {platformAdmin ? "PLATFORM_ADMIN" : role}
                </div>
              </div>
              <button
                onClick={() => { logout(); nav("/login"); }}
                className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted transition-colors"
                title="Sign out"
              >
                <LogOut size={14} />
              </button>
            </>
          )}
        </div>
        {collapsed && (
          <button
            onClick={() => { logout(); nav("/login"); }}
            className="w-full flex items-center justify-center py-2 text-ink-muted hover:text-ink-primary hover:bg-surface-muted rounded transition-colors"
            title="Sign out"
            aria-label="Sign out"
          >
            <LogOut size={14} />
          </button>
        )}
      </div>
    </aside>
  );
}

/* ────────────────────────  Theme toggle  ─────────────────────────── */

function ThemeToggle() {
  const mode = useThemeStore((s) => s.mode);
  const setMode = useThemeStore((s) => s.setMode);

  const options: { value: ThemeMode; label: string; icon: React.ReactNode }[] = [
    { value: "light",  label: "Light",  icon: <Sun size={12} /> },
    { value: "system", label: "System", icon: <Monitor size={12} /> },
    { value: "dark",   label: "Dark",   icon: <Moon size={12} /> },
  ];

  return (
    <div className="grid grid-cols-3 gap-0.5 p-0.5 rounded-md border border-surface-border bg-surface">
      {options.map((o) => {
        const active = mode === o.value;
        return (
          <button key={o.value}
                  onClick={() => setMode(o.value)}
                  title={o.label}
                  className={cn(
                    "flex items-center justify-center gap-1.5 h-7 rounded text-[10px] font-medium uppercase tracking-wider transition-colors",
                    active
                      ? "bg-surface-muted text-ink-primary shadow-sm"
                      : "text-ink-muted hover:text-ink-primary hover:bg-surface-muted/50",
                  )}>
            {o.icon}
            <span className="hidden 2xl:inline">{o.label}</span>
          </button>
        );
      })}
    </div>
  );
}
