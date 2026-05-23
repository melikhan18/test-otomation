import { useRef } from "react";

type Props = {
  /**
   * Physical screen dimensions of the target device, in real pixels.
   * Mouse positions are mapped into this coordinate space and sent verbatim to the agent.
   */
  screenWidth: number;
  screenHeight: number;
  onTap:   (x: number, y: number) => void;
  onSwipe: (x1: number, y1: number, x2: number, y2: number) => void;
  className?: string;
};

/**
 * Translates mouse events on the video player into device-space tap/swipe.
 *
 * - mouseDown + mouseUp without movement (< 8 px) → tap
 * - mouseDown → mouseUp with displacement → swipe
 */
export default function TouchOverlay({ screenWidth, screenHeight, onTap, onSwipe, className }: Props) {
  const startRef = useRef<{ x: number; y: number; t: number } | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  function toDeviceCoord(e: React.MouseEvent): { x: number; y: number } | null {
    const el = containerRef.current;
    if (!el) return null;
    const rect = el.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return null;
    const cx = e.clientX - rect.left;
    const cy = e.clientY - rect.top;
    return {
      x: Math.max(0, Math.min(screenWidth,  Math.round((cx / rect.width)  * screenWidth))),
      y: Math.max(0, Math.min(screenHeight, Math.round((cy / rect.height) * screenHeight))),
    };
  }

  return (
    <div
      ref={containerRef}
      className={"absolute inset-0 cursor-crosshair " + (className ?? "")}
      onMouseDown={(e) => {
        if (e.button !== 0) return;
        const p = toDeviceCoord(e); if (!p) return;
        startRef.current = { ...p, t: performance.now() };
      }}
      onMouseUp={(e) => {
        if (e.button !== 0) return;
        const end = toDeviceCoord(e); if (!end) return;
        const start = startRef.current;
        startRef.current = null;
        if (!start) return;
        const dx = end.x - start.x;
        const dy = end.y - start.y;
        if (Math.hypot(dx, dy) < 8) onTap(start.x, start.y);
        else onSwipe(start.x, start.y, end.x, end.y);
      }}
      onMouseLeave={() => { startRef.current = null; }}
    />
  );
}
