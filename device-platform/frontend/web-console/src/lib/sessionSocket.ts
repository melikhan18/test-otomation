import { FrameType, decodeFrame, encodeFrame, jsonFrame, payloadAsString, type Frame } from "./frameProtocol";

export type StreamMetadata = {
  codec: string;
  /** Encoded stream dimensions (decoder size). */
  width: number;
  height: number;
  fps: number;
  /** Physical device screen dimensions (used for tap coordinates). */
  realWidth: number;
  realHeight: number;
};

export type VideoEvents = {
  onMetadata?: (m: StreamMetadata) => void;
  onKeyframe?: (data: Uint8Array) => void;
  onDelta?:    (data: Uint8Array) => void;
  onOpen?:     () => void;
  onClose?:    (code: number, reason: string) => void;
  onError?:    (e: Event) => void;
};

export type ControlEvents = {
  onInspectResponse?: (json: any) => void;
  onOpen?:  () => void;
  onClose?: (code: number, reason: string) => void;
  onError?: (e: Event) => void;
};

function buildWsUrl(path: string, token: string): string {
  const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
  const base = `${proto}//${window.location.host}`;
  return `${base}${path}?token=${encodeURIComponent(token)}`;
}

export function openVideoSocket(sessionId: number, token: string, events: VideoEvents): WebSocket {
  const ws = new WebSocket(buildWsUrl(`/ws/session/${sessionId}/video`, token));
  ws.binaryType = "arraybuffer";
  ws.onopen = () => events.onOpen?.();
  ws.onerror = (e) => events.onError?.(e);
  ws.onclose = (e) => events.onClose?.(e.code, e.reason);
  ws.onmessage = (m) => {
    if (typeof m.data === "string") return;
    let frame: Frame;
    try { frame = decodeFrame(m.data as ArrayBuffer); } catch { return; }
    switch (frame.type) {
      case FrameType.STREAM_METADATA: {
        try {
          const j = JSON.parse(payloadAsString(frame.payload));
          events.onMetadata?.({
            codec: j.codec,
            width: j.width,
            height: j.height,
            fps: j.fps,
            realWidth:  j.realWidth  ?? j.width,
            realHeight: j.realHeight ?? j.height,
          });
        } catch { /* ignore */ }
        break;
      }
      case FrameType.VIDEO_KEYFRAME: events.onKeyframe?.(frame.payload); break;
      case FrameType.VIDEO_DELTA:    events.onDelta?.(frame.payload); break;
    }
  };
  return ws;
}

export function openControlSocket(sessionId: number, token: string, events: ControlEvents): {
  socket: WebSocket;
  sendTap: (x: number, y: number) => void;
  sendSwipe: (x1: number, y1: number, x2: number, y2: number, durationMs?: number) => void;
  sendKey: (keyCode: number) => void;
  sendText: (value: string) => void;
  sendInspect: (requestId: string) => void;
} {
  const ws = new WebSocket(buildWsUrl(`/ws/session/${sessionId}/control`, token));
  ws.binaryType = "arraybuffer";
  ws.onopen = () => events.onOpen?.();
  ws.onerror = (e) => events.onError?.(e);
  ws.onclose = (e) => events.onClose?.(e.code, e.reason);
  ws.onmessage = (m) => {
    if (typeof m.data === "string") return;
    let frame: Frame;
    try { frame = decodeFrame(m.data as ArrayBuffer); } catch { return; }
    if (frame.type === FrameType.INSPECT_RESPONSE) {
      try { events.onInspectResponse?.(JSON.parse(payloadAsString(frame.payload))); }
      catch { /* ignore */ }
    }
  };

  const sendControl = (obj: unknown) => {
    if (ws.readyState !== WebSocket.OPEN) return;
    ws.send(jsonFrame(FrameType.CONTROL_COMMAND, obj));
  };
  const sendInspectInternal = (obj: unknown) => {
    if (ws.readyState !== WebSocket.OPEN) return;
    ws.send(jsonFrame(FrameType.INSPECT_REQUEST, obj));
  };

  return {
    socket: ws,
    sendTap:   (x, y) => sendControl({ type: "tap", x, y }),
    sendSwipe: (x1, y1, x2, y2, durationMs = 300) =>
      sendControl({ type: "swipe", startX: x1, startY: y1, endX: x2, endY: y2, durationMs }),
    sendKey:   (keyCode) => sendControl({ type: "key", keyCode }),
    sendText:  (value) => sendControl({ type: "text", value }),
    sendInspect: (requestId) => sendInspectInternal({ requestId }),
  };
}

export const KeyCodes = {
  BACK:    4,
  HOME:    3,
  RECENTS: 187,
};

// Re-export for convenience in components
export { encodeFrame, FrameType };
