import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from "react";
import { H264AnnexBDecoder } from "@/lib/videoDecoder";
import { openVideoSocket, type StreamMetadata } from "@/lib/sessionSocket";

type Props = {
  sessionId: number;
  sessionToken: string;
  onMetadata?: (m: StreamMetadata) => void;
  className?: string;
};

export type DeviceVideoPlayerHandle = {
  /** Capture the current canvas frame as a PNG data URL. */
  captureSnapshot: () => string | null;
};

type Status = "connecting" | "waiting" | "live" | "reconnecting" | "error";

const DeviceVideoPlayer = forwardRef<DeviceVideoPlayerHandle, Props>(function DeviceVideoPlayer(
  { sessionId, sessionToken, onMetadata, className }, ref,
) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [status, setStatus] = useState<Status>("connecting");
  const [error, setError] = useState<string | null>(null);

  useImperativeHandle(ref, () => ({
    captureSnapshot: () => {
      const c = canvasRef.current;
      if (!c) { console.warn("[DeviceVideoPlayer] captureSnapshot: canvas ref is null"); return null; }
      if (c.width === 0) { console.warn("[DeviceVideoPlayer] captureSnapshot: canvas.width=0 (no metadata yet?)"); return null; }
      try { return c.toDataURL("image/png"); }
      catch (e) {
        // Most likely SecurityError: WebCodecs VideoFrame draw can leave the canvas
        // origin-tainted on some Chromium/GPU combos. Surface so we can switch to the
        // agent-side ScreenshotEngine fallback if it keeps happening.
        console.warn("[DeviceVideoPlayer] captureSnapshot: toDataURL threw", e);
        return null;
      }
    },
  }), []);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    let unmounted = false;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let attempt = 0;
    let ws: WebSocket | null = null;
    let decoder: H264AnnexBDecoder | null = null;
    let isLive = false;

    function rebuildDecoder() {
      decoder?.close();
      decoder = new H264AnnexBDecoder(
        (frame) => {
          if (unmounted) { frame.close(); return; }
          if (canvas.width !== frame.displayWidth || canvas.height !== frame.displayHeight) {
            canvas.width = frame.displayWidth;
            canvas.height = frame.displayHeight;
          }
          ctx.drawImage(frame, 0, 0, canvas.width, canvas.height);
          frame.close();
          if (!isLive) { isLive = true; setStatus("live"); }
        },
        (e) => { setError(e.message); setStatus("error"); },
      );
      try { decoder.ensureSupported(); }
      catch (e: any) { setError(e.message); setStatus("error"); return false; }
      return true;
    }

    function connect() {
      if (unmounted) return;
      if (!rebuildDecoder()) return;
      attempt++;
      isLive = false;
      setStatus(attempt === 1 ? "connecting" : "reconnecting");
      ws = openVideoSocket(sessionId, sessionToken, {
        onOpen: () => { if (!unmounted && !isLive) setStatus("waiting"); attempt = 0; },
        onMetadata: (m) => {
          canvas.width = m.width;
          canvas.height = m.height;
          decoder!.configure(m.codec === "h264" ? "avc1.42E01E" : m.codec);
          onMetadata?.(m);
        },
        onKeyframe: (data) => decoder!.feed("key", data),
        onDelta:    (data) => decoder!.feed("delta", data),
        onClose: (code, reason) => {
          if (unmounted) return;
          if (code === 4401) { setStatus("error"); setError("unauthorized"); return; }
          setStatus("reconnecting");
          setError(reason || `closed (${code})`);
          const delay = Math.min(1000 * Math.pow(2, Math.min(attempt, 5)), 15_000);
          reconnectTimer = setTimeout(connect, delay);
        },
        onError: () => { if (!isLive) setStatus("reconnecting"); },
      });
    }

    connect();

    return () => {
      unmounted = true;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      try { ws?.close(1000, "unmount"); } catch { /* ignore */ }
      decoder?.close();
    };
  }, [sessionId, sessionToken]);

  return (
    <div className={"relative " + (className ?? "")}>
      <canvas
        ref={canvasRef}
        className="block w-full h-full bg-black rounded-xl"
        style={{ objectFit: "contain" }}
      />
      {status !== "live" && (
        <div className="absolute inset-0 flex items-center justify-center text-zinc-400 text-sm bg-black/70 rounded-xl pointer-events-none">
          {status === "connecting"   && "Connecting…"}
          {status === "waiting"      && "Waiting for video — grant screen capture in the agent app"}
          {status === "reconnecting" && `Reconnecting…${error ? ` (${error})` : ""}`}
          {status === "error"        && (error ?? "stream error")}
        </div>
      )}
    </div>
  );
});

export default DeviceVideoPlayer;
