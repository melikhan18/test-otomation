import { useEffect } from "react";
import { create } from "zustand";
import { persist } from "zustand/middleware";

/**
 * Theme preference store + provider.
 *
 *   - "light" / "dark" → user pinned the choice explicitly.
 *   - "system"         → follow the OS via prefers-color-scheme; default for
 *                        new users.
 *
 * ThemeProvider listens to the OS query when in system mode, so a Mac user
 * toggling appearance at the OS level updates the app immediately.
 */
export type ThemeMode = "light" | "dark" | "system";

type ThemeState = {
  mode: ThemeMode;
  setMode: (m: ThemeMode) => void;
};

export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      mode: "system",
      setMode: (mode) => set({ mode }),
    }),
    { name: "qa-platform-theme" },
  ),
);

function applyResolved(resolved: "light" | "dark") {
  const root = document.documentElement;
  root.classList.toggle("dark", resolved === "dark");
}

function systemPrefersDark(): boolean {
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

/**
 * Resolves {@link ThemeMode} into the concrete "light" or "dark" the page is
 * currently rendering. Useful for components that need to flip an asset (e.g.
 * a logo) without forcing a class check on document.
 */
export function useResolvedTheme(): "light" | "dark" {
  const mode = useThemeStore((s) => s.mode);
  if (mode === "system") return systemPrefersDark() ? "dark" : "light";
  return mode;
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const mode = useThemeStore((s) => s.mode);

  useEffect(() => {
    if (mode === "system") {
      const mq = window.matchMedia("(prefers-color-scheme: dark)");
      applyResolved(mq.matches ? "dark" : "light");
      const listener = (e: MediaQueryListEvent) => applyResolved(e.matches ? "dark" : "light");
      mq.addEventListener("change", listener);
      return () => mq.removeEventListener("change", listener);
    }
    applyResolved(mode);
  }, [mode]);

  return <>{children}</>;
}
