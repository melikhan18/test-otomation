import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { AlertTriangle, Play, X } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Spinner } from "@/components/ui/Spinner";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { deviceApi } from "@/lib/devices";
import { runApi, testDataApi, type RunCreate } from "@/lib/automation";

type Props = {
  scenarioId: number;
  scenarioName: string;
  onClose: () => void;
};

/**
 * Device + environment picker shown when the user hits "Run" on a scenario.
 * Submits the run and navigates to the run detail page.
 */
export default function RunDialog({ scenarioId, scenarioName, onClose }: Props) {
  const nav = useNavigate();
  const devicesQ = useQuery({ queryKey: ["devices"],          queryFn: deviceApi.list,                refetchInterval: 5_000 });
  const envQ     = useQuery({ queryKey: ["automation-envs"],  queryFn: testDataApi.environments });

  const [deviceId, setDeviceId] = useState<number | null>(null);
  const [environment, setEnvironment] = useState<string>("default");
  // Default pacing: 500 ms is small enough not to slow real tests, big enough
  // to outlast the average screen transition / network hop after a tap.
  const [interStepDelayMs, setInterStepDelayMs] = useState<number>(500);
  const [adaptiveWait, setAdaptiveWait] = useState<boolean>(false);

  const onlineDevices = useMemo(
    () => (devicesQ.data ?? []).filter((d) => d.status === "ONLINE"),
    [devicesQ.data],
  );
  const busyDevices = useMemo(
    () => (devicesQ.data ?? []).filter((d) => d.status !== "ONLINE"),
    [devicesQ.data],
  );

  const create = useMutation({
    mutationFn: (b: RunCreate) => runApi.create(b),
    onSuccess: (r) => { onClose(); nav(`/automation/runs/${r.id}`); },
  });

  const err = (create.error as any)?.response?.data?.detail;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <Card className="w-full max-w-2xl flex flex-col max-h-[90vh]">
        <div className="px-5 py-4 border-b border-surface-border flex items-center justify-between">
          <div>
            <div className="text-sm font-semibold flex items-center gap-2">
              <Play size={14} className="text-success-500" />
              Run scenario
            </div>
            <div className="text-xs text-ink-muted mt-0.5">
              <code className="font-mono">{scenarioName}</code> will run once on the selected device.
            </div>
          </div>
          <button onClick={onClose} className="text-ink-muted hover:text-ink-primary p-1.5 rounded hover:bg-surface-muted">
            <X size={14} />
          </button>
        </div>

        <div className="p-5 space-y-5 overflow-auto">
          <div>
            <span className="label block mb-1.5">Target device</span>
            {devicesQ.isLoading ? (
              <div className="text-xs text-ink-muted flex items-center gap-2"><Spinner /> Loading devices…</div>
            ) : (
              <>
                {onlineDevices.length === 0 && (
                  <div className="rounded-md border border-warning-500/30 bg-warning-500/10 px-3 py-2 text-xs text-warning-500 flex items-center gap-2 mb-3">
                    <AlertTriangle size={12} />
                    No online devices available. Start the agent on a real device or emulator first.
                  </div>
                )}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {onlineDevices.map((d) => (
                    <button
                      key={d.id}
                      onClick={() => setDeviceId(d.id)}
                      className={
                        "text-left px-3 py-2 rounded-md border transition-colors " +
                        (deviceId === d.id
                          ? "border-brand-500/50 bg-brand-500/10"
                          : "border-surface-border hover:border-brand-500/30 bg-surface")
                      }
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0">
                          <div className="text-sm font-medium truncate">{d.manufacturer} {d.model}</div>
                          <div className="text-[11px] text-ink-muted">Android {d.androidVersion} · {d.screenWidth}×{d.screenHeight}</div>
                        </div>
                        <StatusBadge tone="success">{d.status}</StatusBadge>
                      </div>
                    </button>
                  ))}
                  {busyDevices.map((d) => (
                    <div key={d.id} className="px-3 py-2 rounded-md border border-surface-border opacity-50 cursor-not-allowed">
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0">
                          <div className="text-sm font-medium truncate">{d.manufacturer} {d.model}</div>
                          <div className="text-[11px] text-ink-muted">{d.androidVersion}</div>
                        </div>
                        <StatusBadge tone={d.status === "IN_USE" ? "warning" : "neutral"}>{d.status}</StatusBadge>
                      </div>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>

          <div>
            <span className="label block mb-1.5">Environment</span>
            <div className="flex flex-wrap gap-1.5">
              {(envQ.data ?? ["default"]).map((e) => (
                <button
                  key={e}
                  onClick={() => setEnvironment(e)}
                  className={
                    "px-3 h-7 rounded-md text-xs font-medium border transition-colors " +
                    (environment === e
                      ? "bg-brand-500/15 border-brand-500/40 text-brand-300"
                      : "border-surface-border text-ink-secondary hover:text-ink-primary")
                  }
                >
                  {e}
                </button>
              ))}
            </div>
            <div className="text-[10px] text-ink-muted mt-1.5">
              Test data references will resolve to this environment (falling back to <code>default</code>).
            </div>
          </div>

          <div>
            <span className="label block mb-1.5">Pacing between steps</span>

            <label className="flex items-start gap-2 cursor-pointer select-none mb-3 p-2.5 rounded-md border border-surface-border hover:border-brand-500/30 transition-colors">
              <input
                type="checkbox"
                checked={adaptiveWait}
                onChange={(e) => setAdaptiveWait(e.target.checked)}
                className="mt-0.5 accent-brand-500"
              />
              <span className="min-w-0">
                <span className="text-xs font-medium block">Adaptive wait <span className="text-[10px] text-ink-muted font-normal">(recommended)</span></span>
                <span className="text-[10px] text-ink-muted">
                  After each UI-changing step, poll the accessibility tree and proceed
                  as soon as it stabilises (max 5s). Faster on snappy screens, more patient on slow ones.
                </span>
              </span>
            </label>

            <div className={adaptiveWait ? "opacity-40 pointer-events-none" : ""}>
              <div className="flex items-center gap-3">
                <input
                  type="range"
                  min={0}
                  max={5000}
                  step={100}
                  value={interStepDelayMs}
                  onChange={(e) => setInterStepDelayMs(Number(e.target.value))}
                  className="flex-1 accent-brand-500"
                  disabled={adaptiveWait}
                />
                <input
                  type="number"
                  min={0}
                  max={30000}
                  step={100}
                  value={interStepDelayMs}
                  onChange={(e) => setInterStepDelayMs(Math.max(0, Math.min(30000, Number(e.target.value) || 0)))}
                  className="w-24 h-8 px-2 rounded-md border border-surface-border bg-surface text-xs font-mono text-right focus:outline-none focus:ring-1 focus:ring-brand-500"
                  disabled={adaptiveWait}
                />
                <span className="text-[10px] text-ink-muted">ms</span>
              </div>
              <div className="text-[10px] text-ink-muted mt-1.5">
                Fixed sleep after every step. Used when adaptive wait is off.
              </div>
            </div>
          </div>

          {err && (
            <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">
              {err}
            </div>
          )}
        </div>

        <div className="px-5 py-3 border-t border-surface-border flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            variant="primary"
            leftIcon={<Play size={12} />}
            disabled={!deviceId}
            loading={create.isPending}
            onClick={() => deviceId && create.mutate({ scenarioId, deviceId, environment, interStepDelayMs, adaptiveWait })}
          >
            Run on device
          </Button>
        </div>
      </Card>
    </div>
  );
}
