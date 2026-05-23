import { useState } from "react";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import {
  CheckCircle2, ChevronDown, ChevronUp, Clock, Edit3, GripVertical, Image as ImageIcon, Repeat, Trash2,
} from "lucide-react";
import {
  STEP_ACTION_MAP, type StepActionDef, type StepView,
} from "@/lib/automation";
import { cn } from "@/lib/cn";

type Props = {
  step: StepView;
  isEditing: boolean;
  onEdit: () => void;
  onCancelEdit: () => void;
  onDelete: () => void;
  /** Render the inline edit form when in edit mode. */
  editForm: React.ReactNode;
};

const TONE_BG: Record<StepActionDef["tone"], string> = {
  blue:   "border-l-brand-500   bg-brand-500/5",
  green:  "border-l-success-500 bg-success-500/5",
  amber:  "border-l-warning-500 bg-warning-500/5",
  violet: "border-l-violet-500  bg-violet-500/5",
  gray:   "border-l-surface-border bg-surface-raised/40",
};
const TONE_TEXT: Record<StepActionDef["tone"], string> = {
  blue:   "text-brand-300",
  green:  "text-success-500",
  amber:  "text-warning-500",
  violet: "text-violet-300",
  gray:   "text-ink-secondary",
};

export default function StepCard({ step, isEditing, onEdit, onCancelEdit, onDelete, editForm }: Props) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: step.id });
  const def = STEP_ACTION_MAP[step.action];
  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={cn(
        "group rounded-md border border-surface-border bg-surface relative",
        "border-l-2",
        TONE_BG[def?.tone ?? "gray"],
      )}
    >
      {/* Header — drag handle, order index, action label, controls */}
      <div className="flex items-stretch min-h-[44px]">
        <button
          {...attributes}
          {...listeners}
          className="px-2 flex items-center text-ink-muted hover:text-ink-primary cursor-grab active:cursor-grabbing"
          aria-label="Drag to reorder"
        >
          <GripVertical size={14} />
        </button>

        <div className="w-7 shrink-0 flex items-center justify-center text-[11px] font-mono text-ink-muted border-r border-surface-border">
          {step.orderIndex + 1}
        </div>

        <div className="flex-1 min-w-0 px-3 py-2 flex items-center gap-3">
          <span className={cn("text-[10px] font-mono uppercase tracking-wider font-semibold shrink-0", TONE_TEXT[def?.tone ?? "gray"])}>
            {def?.label ?? step.action}
          </span>

          <StepSummary step={step} />

          <div className="ml-auto flex items-center gap-2 text-ink-muted text-[10px] shrink-0">
            {def?.hasTimeout && (
              <span title="timeout" className="inline-flex items-center gap-0.5"><Clock size={10} /> {step.timeoutMs}ms</span>
            )}
            {step.retryCount > 0 && (
              <span title="retries" className="inline-flex items-center gap-0.5"><Repeat size={10} /> {step.retryCount}</span>
            )}
            {step.screenshotAfter && (
              <span title="screenshot after" className="inline-flex items-center"><ImageIcon size={10} /></span>
            )}
          </div>
        </div>

        <div className="flex items-center gap-0.5 pr-1.5 opacity-0 group-hover:opacity-100 transition-opacity">
          {!isEditing && (
            <button onClick={onEdit}
              className="p-1.5 rounded text-ink-muted hover:text-ink-primary hover:bg-surface-muted" title="Edit">
              <Edit3 size={12} />
            </button>
          )}
          {isEditing ? (
            <button onClick={onCancelEdit}
              className="p-1.5 rounded text-ink-muted hover:text-ink-primary hover:bg-surface-muted" title="Collapse">
              <ChevronUp size={12} />
            </button>
          ) : (
            <button onClick={onEdit}
              className="p-1.5 rounded text-ink-muted hover:text-ink-primary hover:bg-surface-muted" title="Expand">
              <ChevronDown size={12} />
            </button>
          )}
          <button onClick={onDelete}
            className="p-1.5 rounded text-ink-muted hover:text-danger-500 hover:bg-surface-muted" title="Delete">
            <Trash2 size={12} />
          </button>
        </div>
      </div>

      {/* Expected result — Xray-style documentation under the action row.
          Visible only when populated and the card is collapsed. */}
      {!isEditing && step.expectedResult && (
        <div className="flex items-stretch border-t border-surface-border bg-surface-raised/20">
          <div className="px-2 flex items-start pt-1.5 text-success-500 shrink-0">
            <CheckCircle2 size={11} />
          </div>
          <div className="w-7 shrink-0 border-r border-surface-border" />
          <div className="flex-1 min-w-0 px-3 py-1.5 text-[11px] text-ink-secondary italic">
            <span className="not-italic text-[10px] uppercase tracking-wider text-success-500 font-semibold mr-2">
              expected
            </span>
            {step.expectedResult}
          </div>
        </div>
      )}

      {isEditing && (
        <div className="border-t border-surface-border bg-surface-raised/30 p-3">
          {editForm}
        </div>
      )}
    </div>
  );
}

/** Short, scannable description of what a step does — shown on the collapsed row. */
function StepSummary({ step }: { step: StepView }) {
  const el = step.targetElement;
  const data = step.data;

  return (
    <div className="flex-1 min-w-0 flex items-center gap-2 text-xs">
      {el && (
        <span className="inline-flex items-center gap-1.5 min-w-0 max-w-[40%]">
          {el.screenshotData && (
            <img src={el.screenshotData} alt="" className="w-5 h-5 rounded object-contain bg-black/30 shrink-0" />
          )}
          <span className="font-mono text-success-500 truncate">{el.name}</span>
        </span>
      )}
      {data && (
        <span className="font-mono text-brand-300 truncate min-w-0 max-w-[35%]">
          {data.sensitive ? "🔒 " : ""}{data.name}
          <span className="text-ink-muted text-[10px]"> · {data.environment}</span>
        </span>
      )}
      {!data && step.literalValue && (
        <span className="font-mono text-ink-secondary truncate min-w-0 max-w-[35%]" title={step.literalValue}>
          "{step.literalValue}"
        </span>
      )}
    </div>
  );
}
