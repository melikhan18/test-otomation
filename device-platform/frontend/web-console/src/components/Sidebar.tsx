import { NavLink, useNavigate } from "react-router-dom";
import {
  BarChart3, Building2, Database, FolderKanban, FolderOpen, LogOut, ShieldCheck,
  Smartphone, Target, UserCircle2, UserCog, Users,
} from "lucide-react";
import { cn } from "@/lib/cn";
import { useAuthStore, useEffectiveRole, type EffectiveRole } from "@/store/auth";
import WorkspaceSwitcher from "@/components/WorkspaceSwitcher";

type NavItem = { to: string; label: string; icon: React.ReactNode };
type NavSection = { title: string; items: NavItem[] };

function buildSections(platformAdmin: boolean, role: EffectiveRole): NavSection[] {
  const isOwner   = role === "OWNER";
  const isManager = role === "QA_MANAGER" || isOwner;

  const sections: NavSection[] = [
    {
      title: "Workspace",
      items: [
        { to: "/devices", label: "Devices", icon: <Smartphone size={16} /> },
      ],
    },
    {
      title: "Automation",
      items: [
        { to: "/automation/workspace", label: "Workspace", icon: <FolderKanban size={16} /> },
        { to: "/automation/reports",   label: "Reports",   icon: <BarChart3 size={16} /> },
        { to: "/automation/elements",  label: "Elements",  icon: <Target size={16} /> },
        { to: "/automation/data",      label: "Test data", icon: <Database size={16} /> },
      ],
    },
  ];

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

export default function Sidebar() {
  const nav = useNavigate();
  // Zustand v5: selector returning a new object every render triggers an infinite
  // re-render loop. Subscribe to each field individually instead.
  const username = useAuthStore((s) => s.username);
  const role = useAuthStore((s) => s.role);
  const platformAdmin = useAuthStore((s) => s.platformAdmin);
  const logout = useAuthStore((s) => s.logout);
  const effectiveRole = useEffectiveRole();

  const sections = buildSections(platformAdmin, effectiveRole);

  return (
    <aside className="hidden lg:flex flex-col w-64 shrink-0 border-r border-surface-border bg-surface-raised/40">
      <div className="h-16 flex items-center gap-2.5 px-5 border-b border-surface-border">
        <div className="h-8 w-8 rounded-lg bg-brand-500/10 border border-brand-500/30 flex items-center justify-center">
          <Smartphone size={16} className="text-brand-400" />
        </div>
        <div>
          <div className="text-sm font-semibold leading-tight">Device Platform</div>
          <div className="text-[10px] text-ink-muted leading-tight">Mobile QA Cloud</div>
        </div>
      </div>

      <WorkspaceSwitcher />

      <nav className="flex-1 px-3 py-4 space-y-5 overflow-y-auto">
        {sections.map((section) => (
          <div key={section.title}>
            <div className="label px-2 mb-2">{section.title}</div>
            <div className="space-y-0.5">
              {section.items.map((it) => (
                <NavLink
                  key={it.to}
                  to={it.to}
                  className={({ isActive }) => cn(
                    "group flex items-center gap-3 px-2.5 py-2 rounded-md text-sm transition-colors",
                    isActive
                      ? "bg-surface-muted text-ink-primary"
                      : "text-ink-secondary hover:text-ink-primary hover:bg-surface-muted/60",
                  )}
                >
                  <span className="text-ink-muted group-hover:text-ink-secondary">{it.icon}</span>
                  <span>{it.label}</span>
                </NavLink>
              ))}
            </div>
          </div>
        ))}
      </nav>

      <div className="border-t border-surface-border p-3">
        <div className="flex items-center gap-2.5 px-2 py-2 rounded-md">
          <div className="h-8 w-8 rounded-full bg-brand-500/20 text-brand-300 text-xs font-semibold flex items-center justify-center">
            {(username ?? "?").slice(0, 1).toUpperCase()}
          </div>
          <div className="flex-1 min-w-0">
            <div className="text-xs font-medium truncate">{username}</div>
            <div className="text-[10px] text-ink-muted flex items-center gap-1">
              {platformAdmin ? <ShieldCheck size={10} className="text-brand-400" /> : null}
              {platformAdmin ? "PLATFORM_ADMIN" : role}
            </div>
          </div>
          <button
            onClick={() => { logout(); nav("/login"); }}
            className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted"
            title="Sign out"
          >
            <LogOut size={14} />
          </button>
        </div>
      </div>
    </aside>
  );
}
