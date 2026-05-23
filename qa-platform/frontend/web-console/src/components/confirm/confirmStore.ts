import { create } from "zustand";

/**
 * Imperative confirm() replacement — same call shape as window.confirm but
 * themed and async-friendly:
 *
 *   if (await confirm({ title: "Archive company?", description: "...",
 *                       confirmLabel: "Archive", danger: true })) {
 *     archive.mutate();
 *   }
 *
 * Single dialog at a time. Calling confirm() while one is already open
 * auto-cancels the previous one — UI never stacks.
 */

export type ConfirmRequest = {
  title: string;
  description?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
};

type Pending = ConfirmRequest & {
  resolve: (value: boolean) => void;
};

type ConfirmState = {
  pending: Pending | null;
  open: (req: ConfirmRequest) => Promise<boolean>;
  resolve: (value: boolean) => void;
};

export const useConfirmStore = create<ConfirmState>((set, get) => ({
  pending: null,
  open: (req) => {
    // Auto-cancel any in-flight dialog so we never stack.
    const prev = get().pending;
    if (prev) prev.resolve(false);
    return new Promise<boolean>((resolve) => {
      set({ pending: { ...req, resolve } });
    });
  },
  resolve: (value) => {
    const p = get().pending;
    if (!p) return;
    p.resolve(value);
    set({ pending: null });
  },
}));

/** Imperative entry point — works from any call site (no React context). */
export function confirm(req: ConfirmRequest): Promise<boolean> {
  return useConfirmStore.getState().open(req);
}
