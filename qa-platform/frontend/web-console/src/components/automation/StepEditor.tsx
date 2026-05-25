import { useMemo, useState } from "react";
import { ChevronDown, ChevronUp } from "lucide-react";
import { Button } from "@/components/ui/Button";
import Combobox, { type ComboOption } from "./Combobox";
import ActionPicker from "./ActionPicker";
import {
  STEP_ACTIONS, STEP_ACTION_MAP,
  type ElementView, type StepAction, type StepCondition, type StepCreate, type StepUpdate, type TestDataView,
} from "@/lib/automation";
import { cn } from "@/lib/cn";

/** Surface these as one-click quick picks above the popover — chosen
 *  because they cover the bulk of real Android test scripts. */
const ANDROID_QUICK_PICKS: StepAction[] = [
  "CLICK", "ENTER_TEXT", "WAIT_FOR_VISIBLE", "SLEEP", "ASSERT_VISIBLE", "ASSERT_TEXT_CONTAINS",
];

type Props = {
  initial?: {
    action?: StepAction;
    targetElementId?: number | null;
    dataId?: number | null;
    literalValue?: string | null;
    expectedResult?: string | null;
    timeoutMs?: number | null;
    retryCount?: number | null;
    screenshotAfter?: boolean | null;
    /** Pre-populated IF predicate when editing an IF step. */
    condition?: StepCondition | null;
  };
  elements: ElementView[];
  testData: TestDataView[];
  busy?: boolean;
  /** Submit button label — varies between create ("Add") and update ("Save"). */
  submitLabel: string;
  onCancel: () => void;
  onSubmit: (body: StepCreate | StepUpdate) => void;
};

export default function StepEditor({
  initial, elements, testData, busy, submitLabel, onCancel, onSubmit,
}: Props) {
  const [action, setAction] = useState<StepAction>(initial?.action ?? "CLICK");
  const [targetElementId, setTargetElementId] = useState<number | null>(initial?.targetElementId ?? null);
  const [dataId, setDataId] = useState<number | null>(initial?.dataId ?? null);
  const [literalValue, setLiteralValue] = useState<string>(initial?.literalValue ?? "");
  const [timeoutMs, setTimeoutMs] = useState<number>(initial?.timeoutMs ?? 5000);
  const [retryCount, setRetryCount] = useState<number>(initial?.retryCount ?? 0);
  const [screenshotAfter, setScreenshotAfter] = useState<boolean>(initial?.screenshotAfter ?? false);
  const [expectedResult, setExpectedResult] = useState<string>(initial?.expectedResult ?? "");
  const [valueMode, setValueMode] = useState<"data" | "literal">(
    initial?.dataId ? "data" : "literal",
  );
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [expectedOpen, setExpectedOpen] = useState<boolean>(!!(initial?.expectedResult));
  // IF predicate state. Pre-populated when editing an existing IF row.
  const [condition, setCondition] = useState<StepCondition | null>(initial?.condition ?? null);

  const def = STEP_ACTION_MAP[action];
  const isIf = action === "IF";

  const elementOptions = useMemo<ComboOption[]>(() => elements.map((e) => ({
    value: String(e.id),
    label: e.name,
    hint: e.primaryValue,
    thumbnail: e.screenshotData,
    badge: shortStrategy(e.primaryStrategy),
  })), [elements]);

  const dataOptions = useMemo<ComboOption[]>(() => testData.map((d) => ({
    value: String(d.id),
    label: d.name,
    hint: d.sensitive ? "🔒 sensitive" : d.value.slice(0, 40),
    badge: d.environment,
  })), [testData]);

  function changeAction(next: StepAction) {
    setAction(next);
    const ndef = STEP_ACTION_MAP[next];
    if (!ndef.needsElement) setTargetElementId(null);
    if (ndef.value === "none") { setDataId(null); setLiteralValue(""); }
    if (ndef.value === "literal-only") { setDataId(null); setValueMode("literal"); }
  }

  function submit() {
    if (isIf) {
      if (!condition) return;
      onSubmit({ action, condition });
      return;
    }
    onSubmit({
      action,
      targetElementId: def.needsElement ? targetElementId : null,
      dataId:          def.value === "data-or-literal" && valueMode === "data"    ? dataId : null,
      literalValue:    def.value === "literal-only"     ? literalValue
                     : def.value === "data-or-literal" && valueMode === "literal" ? literalValue
                     : null,
      expectedResult:  expectedResult.trim() ? expectedResult.trim() : null,
      timeoutMs:       def.hasTimeout ? timeoutMs : null,
      retryCount,
      screenshotAfter,
    });
  }

  // Adapt the Android action catalog into the generic picker option model.
  // The lib stores `category` as the editor's internal key ("touch"); the
  // picker shows it as a section header, so we humanise here.
  const pickerOptions = useMemo(() => STEP_ACTIONS.map((a) => ({
    key: a.key,
    label: a.label,
    category: humaniseCategory(a.category),
    description: a.description,
    iconName: a.iconName,
    tone: a.tone,
  })), []);

  return (
    <div className="space-y-3">
      {/* Action picker — quick-pick row + searchable popover. */}
      <div>
        <span className="label block mb-1.5">Action</span>
        <ActionPicker
          value={action}
          onChange={(k) => changeAction(k as StepAction)}
          options={pickerOptions}
          quickPickKeys={ANDROID_QUICK_PICKS}
        />
      </div>

      {/* IF rows show the condition builder instead of element/value/timeout. */}
      {isIf && (
        <ConditionBuilder
          value={condition}
          onChange={setCondition}
          elements={elements}
          testData={testData}
        />
      )}

      {/* Element picker */}
      {!isIf && def.needsElement && (
        <div>
          <span className="label block mb-1.5">Target element</span>
          <Combobox
            options={elementOptions}
            value={targetElementId != null ? String(targetElementId) : null}
            onChange={(v) => setTargetElementId(v ? Number(v) : null)}
            placeholder="Pick an element from the repository…"
            emptyText="No elements — save one from the Inspector first"
          />
        </div>
      )}

      {/* Value (data ref or literal) */}
      {!isIf && def.value === "data-or-literal" && (
        <div>
          <div className="flex items-center justify-between mb-1.5">
            <span className="label">Value source</span>
            <div className="inline-flex rounded-md border border-surface-border bg-surface p-0.5">
              <button
                onClick={() => setValueMode("data")}
                className={cn(
                  "px-2 h-6 text-[10px] font-medium rounded",
                  valueMode === "data" ? "bg-surface-muted text-ink-primary" : "text-ink-secondary",
                )}
              >Test data</button>
              <button
                onClick={() => setValueMode("literal")}
                className={cn(
                  "px-2 h-6 text-[10px] font-medium rounded",
                  valueMode === "literal" ? "bg-surface-muted text-ink-primary" : "text-ink-secondary",
                )}
              >Literal</button>
            </div>
          </div>
          {valueMode === "data" ? (
            <Combobox
              options={dataOptions}
              value={dataId != null ? String(dataId) : null}
              onChange={(v) => setDataId(v ? Number(v) : null)}
              placeholder="Pick test data…"
              emptyText="No test data values yet"
            />
          ) : (
            <input
              value={literalValue}
              onChange={(e) => setLiteralValue(e.target.value)}
              placeholder="Type a literal value"
              className="input font-mono text-xs"
            />
          )}
        </div>
      )}

      {!isIf && def.value === "literal-only" && (
        <div>
          <span className="label block mb-1.5">{def.literalLabel ?? "Value"}</span>
          <input
            value={literalValue}
            onChange={(e) => setLiteralValue(e.target.value)}
            placeholder={def.literalLabel}
            className="input font-mono text-xs"
          />
        </div>
      )}

      {/* Expected result + Advanced section — hidden for IF (no docstring,
          no timeout/retry — those belong to leaf actions). */}
      {!isIf && (
        <>
          <div>
            <button
              type="button"
              onClick={() => setExpectedOpen(!expectedOpen)}
              className="inline-flex items-center gap-1.5 text-[11px] text-ink-muted hover:text-ink-primary"
            >
              {expectedOpen ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
              Expected result
              {expectedResult.trim() && !expectedOpen && (
                <span className="ml-1 text-ink-primary italic truncate max-w-[260px]">
                  · {expectedResult}
                </span>
              )}
            </button>
            {expectedOpen && (
              <div className="mt-1.5 pl-4 border-l border-surface-border">
                <textarea
                  value={expectedResult}
                  onChange={(e) => setExpectedResult(e.target.value)}
                  rows={2}
                  placeholder="What should happen after this step? (e.g. User lands on the dashboard)"
                  className="input resize-y text-xs"
                />
                <div className="text-[10px] text-ink-muted mt-1">
                  Documentation only — surfaced in run reports + pseudocode.
                </div>
              </div>
            )}
          </div>

          <button
            onClick={() => setAdvancedOpen(!advancedOpen)}
            className="inline-flex items-center gap-1 text-[11px] text-ink-muted hover:text-ink-primary"
          >
            {advancedOpen ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
            Advanced
          </button>

          {advancedOpen && (
            <div className="grid grid-cols-2 gap-3 pl-4 border-l border-surface-border">
              {def.hasTimeout && (
                <label className="block">
                  <span className="label block mb-1">Timeout (ms)</span>
                  <input
                    type="number" min={100} step={500}
                    value={timeoutMs}
                    onChange={(e) => setTimeoutMs(parseInt(e.target.value || "0", 10) || 0)}
                    className="input text-xs"
                  />
                </label>
              )}
              <label className="block">
                <span className="label block mb-1">Retries</span>
                <input
                  type="number" min={0} max={5}
                  value={retryCount}
                  onChange={(e) => setRetryCount(parseInt(e.target.value || "0", 10) || 0)}
                  className="input text-xs"
                />
              </label>
              <label className="col-span-2 flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox" checked={screenshotAfter}
                  onChange={(e) => setScreenshotAfter(e.target.checked)}
                  className="accent-brand-500"
                />
                <span className="text-xs">Capture screenshot after this step</span>
              </label>
            </div>
          )}
        </>
      )}

      <div className="flex justify-end gap-2 pt-2 border-t border-surface-border">
        <Button variant="secondary" size="sm" onClick={onCancel}>Cancel</Button>
        <Button variant="primary" size="sm" loading={busy} onClick={submit}
                disabled={isIf && condition == null}>{submitLabel}</Button>
      </div>
    </div>
  );
}

/* ─────────────────────────  Condition builder  ──────────────────────── */

function ConditionBuilder({
  value, onChange, elements, testData,
}: {
  value: StepCondition | null;
  onChange: (c: StepCondition | null) => void;
  elements: ElementView[];
  testData: TestDataView[];
}) {
  const type = value?.type ?? "element_state";
  function setType(next: "element_state" | "test_data_compare") {
    if (next === "element_state") {
      onChange({ type: "element_state", subjectId: elements[0]?.id ?? 0, operator: "is_visible", value: null });
    } else {
      onChange({ type: "test_data_compare", subjectId: testData[0]?.id ?? 0, operator: "equals", value: "" });
    }
  }

  return (
    <div className="space-y-2 rounded-md border border-warning-500/30 bg-warning-500/5 p-3">
      <div className="flex items-center justify-between">
        <span className="label">Condition</span>
        <div className="flex border border-surface-border rounded-md overflow-hidden">
          <button type="button" onClick={() => setType("element_state")}
                  className={cn("px-2 h-5 text-[9px] uppercase tracking-wider transition-colors",
                    type === "element_state" ? "bg-warning-500/20 text-warning-500" : "text-ink-muted hover:text-ink-primary")}>
            Element state
          </button>
          <button type="button" onClick={() => setType("test_data_compare")}
                  className={cn("px-2 h-5 text-[9px] uppercase tracking-wider transition-colors border-l border-surface-border",
                    type === "test_data_compare" ? "bg-warning-500/20 text-warning-500" : "text-ink-muted hover:text-ink-primary")}>
            Test data
          </button>
        </div>
      </div>

      {type === "element_state" ? (
        <ElementStateRow value={value as any} onChange={onChange} elements={elements} />
      ) : (
        <TestDataCompareRow value={value as any} onChange={onChange} testData={testData} />
      )}
    </div>
  );
}

function ElementStateRow({
  value, onChange, elements,
}: {
  value: Extract<StepCondition, { type: "element_state" }> | null;
  onChange: (c: StepCondition | null) => void;
  elements: ElementView[];
}) {
  const subjectId = value?.subjectId ?? elements[0]?.id ?? 0;
  const operator  = value?.operator ?? "is_visible";
  const literal   = value?.value ?? "";
  const needsValue = operator === "text_contains" || operator === "text_equals";

  function emit(next: Partial<Extract<StepCondition, { type: "element_state" }>>) {
    onChange({ type: "element_state", subjectId, operator, value: literal, ...next });
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-[1fr_140px_1fr] gap-2 items-start">
      <select value={subjectId} onChange={(e) => emit({ subjectId: Number(e.target.value) })}
              className="input text-xs">
        <option value="">{elements.length === 0 ? "No elements" : "Pick an element…"}</option>
        {elements.map((el) => (
          <option key={el.id} value={el.id}>{el.name} · {el.primaryStrategy}</option>
        ))}
      </select>
      <select value={operator} onChange={(e) => emit({ operator: e.target.value as any })}
              className="input text-xs">
        <option value="is_visible">is visible</option>
        <option value="is_hidden">is hidden</option>
        <option value="exists">exists</option>
        <option value="text_contains">text contains</option>
        <option value="text_equals">text equals</option>
      </select>
      {needsValue ? (
        <input type="text" value={literal ?? ""} onChange={(e) => emit({ value: e.target.value })}
               placeholder="expected text" className="input text-xs font-mono" />
      ) : (
        <span className="text-[10px] text-ink-muted italic self-center pl-1">no value needed</span>
      )}
    </div>
  );
}

function TestDataCompareRow({
  value, onChange, testData,
}: {
  value: Extract<StepCondition, { type: "test_data_compare" }> | null;
  onChange: (c: StepCondition | null) => void;
  testData: TestDataView[];
}) {
  const subjectId = value?.subjectId ?? testData[0]?.id ?? 0;
  const operator  = value?.operator ?? "equals";
  const literal   = value?.value ?? "";

  function emit(next: Partial<Extract<StepCondition, { type: "test_data_compare" }>>) {
    onChange({ type: "test_data_compare", subjectId, operator, value: literal, ...next });
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-[1fr_140px_1fr] gap-2 items-start">
      <select value={subjectId} onChange={(e) => emit({ subjectId: Number(e.target.value) })}
              className="input text-xs">
        <option value="">{testData.length === 0 ? "No test data" : "Pick test data…"}</option>
        {testData.map((td) => (
          <option key={td.id} value={td.id}>{td.name} · {td.environment}{td.sensitive ? " 🔒" : ""}</option>
        ))}
      </select>
      <select value={operator} onChange={(e) => emit({ operator: e.target.value as any })}
              className="input text-xs">
        <option value="equals">equals</option>
        <option value="not_equals">not equals</option>
        <option value="contains">contains</option>
        <option value="greater_than">greater than</option>
        <option value="less_than">less than</option>
      </select>
      <input type="text" value={literal} onChange={(e) => emit({ value: e.target.value })}
             placeholder="expected value" className="input text-xs font-mono" />
    </div>
  );
}

function shortStrategy(s: string): string {
  if (s === "RESOURCE_ID") return "id";
  if (s === "ACCESSIBILITY_ID") return "a11y";
  return s.toLowerCase();
}

/** "touch" → "Touch", "assert" → "Verify". Matches the older category
 *  headings so users don't see the raw enum names. */
function humaniseCategory(key: string): string {
  switch (key) {
    case "touch":  return "Touch";
    case "input":  return "Input";
    case "wait":   return "Wait";
    case "assert": return "Verify";
    case "util":   return "Util";
    default:       return key.charAt(0).toUpperCase() + key.slice(1);
  }
}
