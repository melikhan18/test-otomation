import { useMemo, useRef } from "react";
import { ScanLine } from "lucide-react";
import { nodeAt, type UiNode } from "@/lib/xpath";

type Props = {
  /** PNG data URL captured at the moment the tree was dumped, or null if no snapshot yet. */
  imageSrc: string | null;
  /** Root node of the dump — required for click-to-select. */
  root: UiNode | null;
  /** Currently selected path (highlights its leaf's bounds). */
  selectedPath: UiNode[] | null;
  /** Device screen dimensions (in real pixels) — used to map clicks and bbox coords to %. */
  screenWidth: number;
  screenHeight: number;
  /** Called with the path to the deepest node under (x,y) when the user clicks the snapshot. */
  onSelect: (path: UiNode[] | null) => void;
  className?: string;
};

/**
 * Static snapshot of the device captured at inspect time. Click on any element to drill
 * into the UI tree, or select a node in the tree to see its bounding box highlighted here.
 */
export default function InspectorCanvas({
  imageSrc, root, selectedPath, screenWidth, screenHeight, onSelect, className,
}: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const selectedNode = selectedPath?.[selectedPath.length - 1];

  const bboxStyle = useMemo(() => {
    if (!selectedNode || screenWidth === 0 || screenHeight === 0) return null;
    const [l, t, r, b] = selectedNode.bounds;
    return {
      left:   `${(l / screenWidth)  * 100}%`,
      top:    `${(t / screenHeight) * 100}%`,
      width:  `${((r - l) / screenWidth)  * 100}%`,
      height: `${((b - t) / screenHeight) * 100}%`,
    } as React.CSSProperties;
  }, [selectedNode, screenWidth, screenHeight]);

  const ancestorBoxes = useMemo(() => {
    if (!selectedPath || screenWidth === 0 || screenHeight === 0) return [];
    return selectedPath.slice(0, -1).map((n) => {
      const [l, t, r, b] = n.bounds;
      return {
        left:   `${(l / screenWidth)  * 100}%`,
        top:    `${(t / screenHeight) * 100}%`,
        width:  `${((r - l) / screenWidth)  * 100}%`,
        height: `${((b - t) / screenHeight) * 100}%`,
      } as React.CSSProperties;
    });
  }, [selectedPath, screenWidth, screenHeight]);

  function handleClick(e: React.MouseEvent) {
    if (!root || screenWidth === 0 || screenHeight === 0) return;
    const el = containerRef.current; if (!el) return;
    const rect = el.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return;
    const x = (e.clientX - rect.left) / rect.width  * screenWidth;
    const y = (e.clientY - rect.top)  / rect.height * screenHeight;
    onSelect(nodeAt(root, x, y));
  }

  const hasSnapshot = !!imageSrc && screenWidth > 0 && screenHeight > 0;
  const aspect = screenWidth > 0 && screenHeight > 0
    ? `${screenWidth} / ${screenHeight}`
    : "9 / 19.5";

  return (
    <div
      ref={containerRef}
      onClick={handleClick}
      className={"relative bg-black rounded-2xl ring-1 ring-surface-border overflow-hidden " +
                 (hasSnapshot ? "cursor-crosshair " : "") + (className ?? "")}
      style={{ aspectRatio: aspect }}
    >
      {hasSnapshot ? (
        <img src={imageSrc!} alt="device snapshot" className="absolute inset-0 w-full h-full select-none pointer-events-none" draggable={false} />
      ) : (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 text-ink-muted text-xs">
          <ScanLine size={20} />
          <span>No snapshot</span>
          <span className="text-[10px] text-ink-muted/70">Press Inspect on the tree panel</span>
        </div>
      )}

      {/* Ancestor outlines — subtle */}
      {ancestorBoxes.map((s, i) => (
        <div
          key={i}
          className="absolute border border-brand-500/30 pointer-events-none transition-all duration-150"
          style={s}
        />
      ))}

      {/* Selected node — strong highlight */}
      {bboxStyle && (
        <div
          className="absolute border-2 border-brand-400 bg-brand-500/20 pointer-events-none transition-all duration-150 shadow-[0_0_0_1px_rgba(0,0,0,0.5)_inset]"
          style={bboxStyle}
        />
      )}
    </div>
  );
}
