import { create } from "zustand";

/**
 * Lightweight toast store. Imperative API on purpose — calls from anywhere
 * (mutation callbacks, fetch error handlers, navigation guards) shouldn't have
 * to hold a React context handle.
 *
 *   toast.success("Saved");
 *   toast.error("Couldn't reach the device", { description: err.message });
 *   toast.show({ kind: "warning", title: "Migration pending", ttl: 0 });
 *
 * ttl=0 means the toast stays until the user dismisses it.
 */

export type ToastKind = "info" | "success" | "warning" | "error";

export type ToastAction = {
  label: string;
  onClick: () => void;
};

export type Toast = {
  id: string;
  kind: ToastKind;
  title: string;
  description?: string;
  /** Auto-dismiss after this many ms. 0 → sticky. Defaults: success/info 4s, warning 6s, error 8s. */
  ttl?: number;
  action?: ToastAction;
};

type ToastState = {
  toasts: Toast[];
  show: (t: Omit<Toast, "id"> & { id?: string }) => string;
  dismiss: (id: string) => void;
  clear: () => void;
};

let counter = 0;
const nextId = () => `t-${Date.now()}-${counter++}`;

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],
  show: (t) => {
    const id = t.id ?? nextId();
    const toast: Toast = { id, ...t };
    set((s) => ({ toasts: [...s.toasts, toast] }));
    const ttl = toast.ttl ?? defaultTtl(toast.kind);
    if (ttl > 0) {
      setTimeout(() => get().dismiss(id), ttl);
    }
    return id;
  },
  dismiss: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
  clear: () => set({ toasts: [] }),
}));

function defaultTtl(kind: ToastKind): number {
  switch (kind) {
    case "success": return 3500;
    case "info":    return 4000;
    case "warning": return 6000;
    case "error":   return 7000;
  }
}

/** Imperative facade — handy for non-React call sites (api interceptors etc.). */
export const toast = {
  show: (opts: Omit<Toast, "id"> & { id?: string }) => useToastStore.getState().show(opts),
  info:    (title: string, opts?: Partial<Omit<Toast, "id" | "kind" | "title">>) =>
    useToastStore.getState().show({ kind: "info",    title, ...opts }),
  success: (title: string, opts?: Partial<Omit<Toast, "id" | "kind" | "title">>) =>
    useToastStore.getState().show({ kind: "success", title, ...opts }),
  warning: (title: string, opts?: Partial<Omit<Toast, "id" | "kind" | "title">>) =>
    useToastStore.getState().show({ kind: "warning", title, ...opts }),
  error:   (title: string, opts?: Partial<Omit<Toast, "id" | "kind" | "title">>) =>
    useToastStore.getState().show({ kind: "error",   title, ...opts }),
  dismiss: (id: string) => useToastStore.getState().dismiss(id),
};

/** Pull just the live toasts in a component (used by ToastViewport). */
export function useToasts() {
  return useToastStore((s) => s.toasts);
}
