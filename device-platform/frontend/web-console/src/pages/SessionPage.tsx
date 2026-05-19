import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft, ChevronLeft, Circle, LogOut, RefreshCcw, Square,
} from "lucide-react";
import TopBar from "@/components/TopBar";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { Spinner } from "@/components/ui/Spinner";
import DeviceVideoPlayer, { type DeviceVideoPlayerHandle } from "@/components/DeviceVideoPlayer";
import TouchOverlay from "@/components/TouchOverlay";
import InspectorPanel from "@/components/InspectorPanel";
import InspectorCanvas from "@/components/InspectorCanvas";
import { sessionApi } from "@/lib/sessions";
import { KeyCodes, openControlSocket, type StreamMetadata } from "@/lib/sessionSocket";
import type { UiNode } from "@/lib/xpath";
import { useAuthStore } from "@/store/auth";

// Module-scope map of pending "auto-release" timers keyed by sessionId.
// Lets a fresh mount cancel a queued end (StrictMode dev double-mount, fast back-and-forth nav).
const pendingSessionEnds = new Map<number, ReturnType<typeof setTimeout>>();

export default function SessionPage() {
  const { sessionId } = useParams();
  const id = Number(sessionId);
  const qc = useQueryClient();
  const nav = useNavigate();

  const { data, error, isLoading } = useQuery({
    queryKey: ["session", id],
    queryFn: () => sessionApi.get(id),
    enabled: !Number.isNaN(id),
    refetchOnWindowFocus: false,
  });

  const touch = useMutation({
    mutationFn: () => sessionApi.touch(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["session", id] }),
  });

  // Flag the explicit end so the auto-release on unmount doesn't fire a duplicate DELETE.
  const explicitlyEnded = useRef(false);

  const end = useMutation({
    mutationFn: () => {
      explicitlyEnded.current = true;
      return sessionApi.end(id);
    },
    onSuccess: () => nav("/devices"),
  });

  useEffect(() => {
    if (!data) return;
    const t = setInterval(() => touch.mutate(), 5 * 60_000);
    return () => clearInterval(t);
  }, [data?.id]);

  // Auto-release the session when the user navigates away (back button, route change,
  // tab close). Otherwise the device stays IN_USE until the 30-minute Redis lock expires.
  //
  // Two paths:
  //   - In-app navigation → component unmounts → useEffect cleanup → sessionApi.end()
  //   - Browser tab close / reload → pagehide event → fetch keepalive DELETE
  //                                                  (axios is unreliable during unload)
  //
  // A small (250 ms) delay on the unmount path absorbs React StrictMode's dev-only
  // mount/unmount/mount cycle — a fresh mount within that window cancels the pending end.
  useEffect(() => {
    const sid = data?.id;
    if (!sid) return;

    // A second mount cancels any pending end queued by the previous unmount.
    const pending = pendingSessionEnds.get(sid);
    if (pending) {
      clearTimeout(pending);
      pendingSessionEnds.delete(sid);
    }

    const releaseOnUnload = () => {
      if (explicitlyEnded.current) return;
      const token = useAuthStore.getState().accessToken;
      if (!token) return;
      // keepalive: true survives the page unload — unlike axios.
      fetch(`/api/sessions/${sid}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` },
        keepalive: true,
      }).catch(() => { /* fire-and-forget */ });
    };
    window.addEventListener("pagehide", releaseOnUnload);

    return () => {
      window.removeEventListener("pagehide", releaseOnUnload);
      if (explicitlyEnded.current) return;
      const timer = setTimeout(() => {
        pendingSessionEnds.delete(sid);
        sessionApi.end(sid).catch(() => { /* already ended or gone — fine */ });
      }, 250);
      pendingSessionEnds.set(sid, timer);
    };
  }, [data?.id]);

  const [meta, setMeta] = useState<StreamMetadata | null>(null);
  const controlRef = useRef<ReturnType<typeof openControlSocket> | null>(null);
  const playerRef = useRef<DeviceVideoPlayerHandle>(null);
  const [textInput, setTextInput] = useState("");

  const [inspectTree, setInspectTree] = useState<UiNode | null>(null);
  const [inspectError, setInspectError] = useState<string | null>(null);
  const [inspectBusy, setInspectBusy] = useState(false);
  const [inspectSelected, setInspectSelected] = useState<UiNode[] | null>(null);
  const [inspectSnapshot, setInspectSnapshot] = useState<string | null>(null);
  const pendingInspectId = useRef<string | null>(null);

  useEffect(() => {
    if (!data?.sessionToken) return;
    const handle = openControlSocket(data.id, data.sessionToken, {
      onInspectResponse: (r) => {
        if (pendingInspectId.current && r.requestId && r.requestId !== pendingInspectId.current) return;
        setInspectBusy(false);
        pendingInspectId.current = null;
        if (r.error) { setInspectError(String(r.error)); setInspectTree(null); return; }
        setInspectError(null);
        setInspectTree(r.root as UiNode);
        setInspectSelected(null);
      },
    });
    controlRef.current = handle;
    return () => { try { handle.socket.close(1000, "unmount"); } catch { /* ignore */ } controlRef.current = null; };
  }, [data?.id, data?.sessionToken]);

  function runInspect() {
    if (!controlRef.current) return;
    // Capture the current live canvas synchronously so the snapshot matches the tree dump.
    setInspectSnapshot(playerRef.current?.captureSnapshot() ?? null);
    setInspectBusy(true);
    setInspectError(null);
    const rid = (crypto as any).randomUUID ? crypto.randomUUID() : String(Date.now());
    pendingInspectId.current = rid;
    controlRef.current.sendInspect(rid);
    setTimeout(() => {
      if (pendingInspectId.current === rid) {
        setInspectBusy(false);
        setInspectError("Timeout waiting for inspect response");
        pendingInspectId.current = null;
      }
    }, 8000);
  }

  const liveAspect = useMemo(
    () => (meta ? `${meta.width} / ${meta.height}` : "9 / 19.5"),
    [meta?.width, meta?.height],
  );

  if (isLoading) {
    return (
      <>
        <TopBar crumbs={[{ label: "Devices", to: "/devices" }, { label: "Loading…" }]} />
        <div className="px-6 py-8 text-ink-muted flex items-center gap-2 text-sm"><Spinner /> Loading session…</div>
      </>
    );
  }
  if (error || !data) {
    return (
      <>
        <TopBar crumbs={[{ label: "Devices", to: "/devices" }, { label: "Not found" }]} />
        <div className="px-6 py-8 text-danger-500">Session not found</div>
      </>
    );
  }
  if (!data.sessionToken) {
    return (
      <>
        <TopBar crumbs={[{ label: "Devices", to: "/devices" }, { label: `Session ${id}` }]} />
        <div className="px-6 py-8 text-danger-500">Session token missing</div>
      </>
    );
  }

  return (
    <>
      <TopBar
        crumbs={[
          { label: "Devices", to: "/devices" },
          { label: `Session ${data.id}` },
        ]}
        actions={
          <>
            <Button variant="ghost" size="sm" onClick={() => nav("/devices")} leftIcon={<ArrowLeft size={14} />}>
              Back
            </Button>
            <Button variant="ghost" size="sm" onClick={() => touch.mutate()} leftIcon={<RefreshCcw size={12} />}>
              Refresh lock
            </Button>
            <Button variant="danger" size="sm" onClick={() => end.mutate()} leftIcon={<LogOut size={14} />}>
              End session
            </Button>
          </>
        }
      />

      {/* Session strip — compact session info above the 3-column workspace */}
      <div className="px-6 pt-6">
        <Card className="flex flex-wrap items-center gap-x-6 gap-y-2 px-5 py-3">
          <div className="flex items-center gap-3">
            <StatusBadge tone={data.status === "ACTIVE" ? "success" : "neutral"}>{data.status}</StatusBadge>
            <div>
              <div className="text-sm font-semibold">Session #{data.id}</div>
              <div className="text-[11px] text-ink-muted">
                Device #{data.deviceId} · started {new Date(data.createdAt).toLocaleTimeString()}
              </div>
            </div>
          </div>
          {meta && (
            <>
              <Divider />
              <MiniStat label="Real screen" value={`${meta.realWidth}×${meta.realHeight}`} />
              <MiniStat label="Stream"      value={`${meta.width}×${meta.height} @ ${meta.fps}fps`} />
              <MiniStat label="Codec"       value={(meta.codec || "h264").toUpperCase()} />
            </>
          )}
        </Card>
      </div>

      {/* 3-column workspace: live control · UI tree · snapshot inspector
          Stacks single-column on small viewports. */}
      <div className="px-6 py-6 grid gap-6 grid-cols-1 xl:grid-cols-[360px_1fr_360px]">
        {/* Live control panel */}
        <div className="flex flex-col items-center gap-4 min-w-0">
          <SectionLabel>Live device</SectionLabel>
          <Card className="bg-surface-raised/40 px-3 py-3 w-full max-w-[400px] mx-auto">
            <div
              className="relative mx-auto rounded-[24px] bg-black ring-1 ring-surface-border shadow-[0_8px_32px_-8px_rgba(0,0,0,0.6)] overflow-hidden"
              style={{ aspectRatio: liveAspect, maxHeight: "min(70vh, 760px)" }}
            >
              <DeviceVideoPlayer
                ref={playerRef}
                sessionId={data.id}
                sessionToken={data.sessionToken}
                onMetadata={setMeta}
              />
              {meta && (
                <TouchOverlay
                  screenWidth={meta.realWidth}
                  screenHeight={meta.realHeight}
                  onTap={(x, y) => controlRef.current?.sendTap(x, y)}
                  onSwipe={(x1, y1, x2, y2) => controlRef.current?.sendSwipe(x1, y1, x2, y2)}
                />
              )}
              <div className="pointer-events-none absolute inset-0 rounded-[24px] ring-1 ring-inset ring-white/5" />
            </div>
            <div className="flex justify-center gap-2 mt-3">
              <KeyBtn icon={<ChevronLeft size={14} />} label="Back"    onClick={() => controlRef.current?.sendKey(KeyCodes.BACK)} />
              <KeyBtn icon={<Circle    size={12} />}   label="Home"    onClick={() => controlRef.current?.sendKey(KeyCodes.HOME)} />
              <KeyBtn icon={<Square    size={12} />}   label="Recents" onClick={() => controlRef.current?.sendKey(KeyCodes.RECENTS)} />
            </div>
          </Card>

          <Card className="w-full max-w-[400px] mx-auto px-4 py-3">
            <div className="flex items-center gap-2">
              <input
                value={textInput}
                onChange={(e) => setTextInput(e.target.value)}
                placeholder="Type text…"
                className="input flex-1"
                onKeyDown={(e) => {
                  if (e.key === "Enter" && textInput) {
                    controlRef.current?.sendText(textInput);
                    setTextInput("");
                  }
                }}
              />
              <Button
                variant="primary"
                disabled={!textInput}
                onClick={() => { if (textInput) { controlRef.current?.sendText(textInput); setTextInput(""); } }}
              >Send</Button>
            </div>
          </Card>
        </div>

        {/* UI tree */}
        <div className="flex flex-col gap-3 min-w-0">
          <SectionLabel>UI hierarchy</SectionLabel>
          <Card className="flex flex-col flex-1 min-h-0 p-4">
            <InspectorPanel
              tree={inspectTree}
              error={inspectError}
              busy={inspectBusy}
              onRefresh={runInspect}
              selectedPath={inspectSelected}
              onSelect={setInspectSelected}
            />
          </Card>
        </div>

        {/* Snapshot inspector — bidirectional with the tree */}
        <div className="flex flex-col gap-3 min-w-0">
          <SectionLabel>Inspector snapshot</SectionLabel>
          <Card className="bg-surface-raised/40 px-3 py-3 w-full max-w-[400px] mx-auto">
            <InspectorCanvas
              imageSrc={inspectSnapshot}
              root={inspectTree}
              selectedPath={inspectSelected}
              screenWidth={meta?.realWidth ?? 0}
              screenHeight={meta?.realHeight ?? 0}
              onSelect={setInspectSelected}
            />
          </Card>
          <div className="w-full max-w-[400px] mx-auto text-[11px] text-ink-muted text-center">
            Click an element on the snapshot to drill into the tree.
          </div>
        </div>
      </div>
    </>
  );
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return <div className="text-[10px] uppercase tracking-[0.08em] font-semibold text-ink-muted">{children}</div>;
}

function KeyBtn({ icon, label, onClick }: { icon: React.ReactNode; label: string; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className="flex-1 max-w-[120px] inline-flex items-center justify-center gap-2 h-9 rounded-md border border-surface-border bg-surface hover:bg-surface-muted text-xs font-medium text-ink-secondary hover:text-ink-primary transition-colors"
    >
      {icon}{label}
    </button>
  );
}

function MiniStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <div className="text-[10px] uppercase tracking-wider text-ink-muted">{label}</div>
      <div className="text-xs font-medium text-ink-primary truncate mt-0.5">{value}</div>
    </div>
  );
}

function Divider() {
  return <div className="h-7 w-px bg-surface-border hidden sm:block" />;
}
