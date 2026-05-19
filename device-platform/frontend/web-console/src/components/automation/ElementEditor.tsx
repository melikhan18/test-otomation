import { useEffect, useState } from "react";
import { Plus, Trash2, X } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { LocatorBadge } from "./LocatorBadge";
import { cn } from "@/lib/cn";
import type {
  ElementView, ElementCreate, ElementUpdate, Locator, LocatorStrategy,
} from "@/lib/automation";

const STRATEGIES: LocatorStrategy[] = ["RESOURCE_ID", "ACCESSIBILITY_ID", "TEXT", "CLASS", "XPATH"];

type Props = {
  mode: "create" | "edit";
  initial?: Partial<ElementView> & Partial<ElementCreate>;
  /** Optional thumbnail + sample attributes captured from the inspector (read-only display). */
  snapshot?: { dataUrl: string | null; bounds: string | null; className: string | null; text: string | null; resourceId: string | null };
  busy?: boolean;
  error?: string | null;
  onClose: () => void;
  onSubmit: (payload: ElementCreate | ElementUpdate) => void;
};

export default function ElementEditor({ mode, initial, snapshot, busy, error, onClose, onSubmit }: Props) {
  const [name, setName] = useState(initial?.name ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [primaryStrategy, setPrimaryStrategy] = useState<LocatorStrategy>(
    initial?.primaryStrategy ?? "RESOURCE_ID",
  );
  const [primaryValue, setPrimaryValue] = useState(initial?.primaryValue ?? "");
  const [fallbacks, setFallbacks] = useState<Locator[]>(initial?.fallbackLocators ?? []);

  useEffect(() => {
    function onKey(e: KeyboardEvent) { if (e.key === "Escape") onClose(); }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  function submit() {
    const payload = {
      name: name.trim(),
      description: description?.trim() || null,
      primaryStrategy,
      primaryValue: primaryValue.trim(),
      fallbackLocators: fallbacks.filter((f) => f.value.trim().length > 0),
      ...(mode === "create" && snapshot
        ? {
            screenshotData:   snapshot.dataUrl,
            sampleBounds:     snapshot.bounds,
            sampleClass:      snapshot.className,
            sampleText:       snapshot.text,
            sampleResourceId: snapshot.resourceId,
          }
        : {}),
    };
    onSubmit(payload as ElementCreate);
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-2xl max-h-[90vh] flex flex-col">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div>
            <div className="text-sm font-semibold">{mode === "create" ? "Save element" : "Edit element"}</div>
            <div className="text-xs text-ink-muted mt-0.5">
              Reusable selector for test steps. The first locator that resolves wins; fallbacks help with flakiness.
            </div>
          </div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted">
            <X size={14} />
          </button>
        </div>

        <div className="p-5 space-y-4 overflow-auto">
          {snapshot?.dataUrl && (
            <div className="flex gap-4 items-start">
              <img
                src={snapshot.dataUrl}
                alt="element thumbnail"
                className="w-24 h-24 object-contain rounded-md border border-surface-border bg-black"
              />
              <dl className="text-xs flex-1 space-y-1">
                {snapshot.className   && <Row label="class"    value={snapshot.className} />}
                {snapshot.resourceId  && <Row label="resource" value={snapshot.resourceId} />}
                {snapshot.text        && <Row label="text"     value={snapshot.text} />}
                {snapshot.bounds      && <Row label="bounds"   value={snapshot.bounds} />}
              </dl>
            </div>
          )}

          <Field label="Name (kebab-case, unique per product)">
            <input
              autoFocus
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="whatsapp-login-button"
              className="input"
            />
          </Field>

          <Field label="Description (optional)">
            <input
              value={description ?? ""}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="What this element represents"
              className="input"
            />
          </Field>

          <div>
            <span className="label block mb-1.5">Primary locator</span>
            <div className="flex gap-2">
              <select
                value={primaryStrategy}
                onChange={(e) => setPrimaryStrategy(e.target.value as LocatorStrategy)}
                className="input max-w-[160px]"
              >
                {STRATEGIES.map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
              <input
                value={primaryValue}
                onChange={(e) => setPrimaryValue(e.target.value)}
                placeholder="value"
                className="input flex-1 font-mono text-xs"
              />
            </div>
          </div>

          <div>
            <div className="flex items-center justify-between mb-1.5">
              <span className="label">Fallback locators ({fallbacks.length})</span>
              <Button
                size="sm" variant="ghost" leftIcon={<Plus size={12} />}
                onClick={() => setFallbacks([...fallbacks, { strategy: "XPATH", value: "" }])}
              >
                Add fallback
              </Button>
            </div>
            <div className="space-y-2">
              {fallbacks.length === 0 && (
                <div className="text-[11px] text-ink-muted px-3 py-2 rounded-md border border-dashed border-surface-border">
                  No fallbacks. If the primary locator misses, the step fails.
                </div>
              )}
              {fallbacks.map((f, i) => (
                <div key={i} className="flex gap-2 items-center">
                  <select
                    value={f.strategy}
                    onChange={(e) => {
                      const next = [...fallbacks];
                      next[i] = { ...f, strategy: e.target.value as LocatorStrategy };
                      setFallbacks(next);
                    }}
                    className="input max-w-[160px]"
                  >
                    {STRATEGIES.map((s) => <option key={s} value={s}>{s}</option>)}
                  </select>
                  <input
                    value={f.value}
                    onChange={(e) => {
                      const next = [...fallbacks];
                      next[i] = { ...f, value: e.target.value };
                      setFallbacks(next);
                    }}
                    placeholder="value"
                    className="input flex-1 font-mono text-xs"
                  />
                  <button
                    onClick={() => setFallbacks(fallbacks.filter((_, j) => j !== i))}
                    className="text-ink-muted hover:text-danger-500 p-2"
                    title="Remove"
                  >
                    <Trash2 size={12} />
                  </button>
                </div>
              ))}
            </div>
          </div>

          {error && (
            <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">
              {error}
            </div>
          )}
        </div>

        <div className="px-5 py-3 border-t border-surface-border flex items-center justify-between gap-2">
          <div className="text-[11px] text-ink-muted flex items-center gap-2">
            Resolves in order:
            <LocatorBadge strategy={primaryStrategy} />
            {fallbacks.filter((f) => f.value.trim()).slice(0, 3).map((f, i) => (
              <span key={i} className="opacity-70"><LocatorBadge strategy={f.strategy} /></span>
            ))}
          </div>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={onClose}>Cancel</Button>
            <Button
              variant="primary"
              loading={busy}
              disabled={!name.trim() || !primaryValue.trim()}
              onClick={submit}
            >
              {mode === "create" ? "Save element" : "Update"}
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="label block mb-1.5">{label}</span>
      {children}
    </label>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start gap-2">
      <dt className="w-16 text-[10px] uppercase tracking-wide text-ink-muted pt-0.5">{label}</dt>
      <dd className={cn("flex-1 text-ink-primary break-all", label !== "text" && "font-mono text-[11px]")}>{value}</dd>
    </div>
  );
}
