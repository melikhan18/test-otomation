import type { ReactNode } from "react";
import { Link, useLocation } from "react-router-dom";
import { ChevronRight, Smartphone } from "lucide-react";

type Crumb = { label: string; to?: string };

export default function TopBar({
  crumbs,
  actions,
}: { crumbs?: Crumb[]; actions?: ReactNode }) {
  const loc = useLocation();
  const computed = crumbs ?? inferCrumbs(loc.pathname);

  return (
    <header className="h-16 px-6 border-b border-surface-border bg-surface-raised/30 backdrop-blur flex items-center justify-between gap-4 sticky top-0 z-10">
      <div className="flex items-center gap-1 text-sm">
        <Link to="/" className="lg:hidden flex items-center gap-2 mr-3">
          <div className="h-7 w-7 rounded-md bg-brand-500/10 border border-brand-500/30 flex items-center justify-center">
            <Smartphone size={14} className="text-brand-400" />
          </div>
        </Link>
        {computed.map((c, i) => (
          <div key={i} className="flex items-center gap-1">
            {i > 0 && <ChevronRight size={12} className="text-ink-muted mx-1" />}
            {c.to && i < computed.length - 1 ? (
              <Link to={c.to} className="text-ink-secondary hover:text-ink-primary">{c.label}</Link>
            ) : (
              <span className={i === computed.length - 1 ? "text-ink-primary font-medium" : "text-ink-secondary"}>
                {c.label}
              </span>
            )}
          </div>
        ))}
      </div>
      <div className="flex items-center gap-2">{actions}</div>
    </header>
  );
}

function inferCrumbs(pathname: string): Crumb[] {
  const parts = pathname.split("/").filter(Boolean);
  if (parts.length === 0) return [{ label: "Dashboard" }];
  const out: Crumb[] = [];
  let acc = "";
  for (const p of parts) {
    acc += "/" + p;
    out.push({ label: cap(decodeURIComponent(p)), to: acc });
  }
  return out;
}

function cap(s: string) {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
