// Wire protocol: each WebSocket binary message is exactly one frame
//   [1 byte type][payload bytes...]
// Mirrors the backend FrameType enum.

export const FrameType = {
  VIDEO_KEYFRAME:   0x01,
  VIDEO_DELTA:      0x02,
  CONTROL_COMMAND:  0x03,
  INSPECT_REQUEST:  0x04,
  INSPECT_RESPONSE: 0x05,
  HEARTBEAT:        0x06,
  STREAM_METADATA:  0x07,
  FORCE_KEYFRAME:   0x08,
} as const;
export type FrameTypeValue = (typeof FrameType)[keyof typeof FrameType];

export type Frame = { type: number; payload: Uint8Array };

export function encodeFrame(type: number, payload: Uint8Array = new Uint8Array(0)): ArrayBuffer {
  const out = new Uint8Array(1 + payload.byteLength);
  out[0] = type & 0xff;
  if (payload.byteLength > 0) out.set(payload, 1);
  return out.buffer;
}

export function decodeFrame(buf: ArrayBuffer): Frame {
  const v = new Uint8Array(buf);
  if (v.byteLength === 0) throw new Error("empty frame");
  return { type: v[0], payload: v.subarray(1) };
}

export function payloadAsString(payload: Uint8Array): string {
  return new TextDecoder().decode(payload);
}

export function jsonFrame(type: number, obj: unknown): ArrayBuffer {
  return encodeFrame(type, new TextEncoder().encode(JSON.stringify(obj)));
}
