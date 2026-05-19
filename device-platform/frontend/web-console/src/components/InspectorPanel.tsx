import { Fragment, useEffect, useMemo, useRef, useState } from "react";
import {
  Check, ChevronRight, ChevronsDownUp, ChevronsUpDown, Copy, RefreshCcw, Search, X,
} from "lucide-react";
import {
  absoluteXPath, appiumLocators, espressoMatchers,
  lastSegment, preferredXPath, uiAutomatorSelector, webdriverIOSelectors,
  type LocatorSnippet, type UiNode,
} from "@/lib/xpath";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { cn } from "@/lib/cn";

type Props = {
  tree: UiNode | null;
  error: string | null;
  busy: boolean;
  onRefresh: () => void;
  selectedPath: UiNode[] | null;
  onSelect: (path: UiNode[] | null) => void;
  /** Optional: enables the "Save as element" action on the selection details. */
  onSaveAsElement?: (path: UiNode[]) => void;
};

type FlatRow = {
  node: UiNode;
  depth: number;
  path: UiNode[];
  hasChildren: boolean;
};

type Scope = "all" | "id" | "class" | "text" | "a11y" | "hint" | "pkg" | "xpath";
const SCOPES: { key: Scope; label: string; hint: string }[] = [
  { key: "all",   label: "All",   hint: "All attributes" },
  { key: "id",    label: "id",    hint: "resource-id (Appium id)" },
  { key: "a11y",  label: "a11y",  hint: "content-desc / accessibility-id" },
  { key: "text",  label: "text",  hint: "visible text" },
  { key: "hint",  label: "hint",  hint: "EditText placeholder" },
  { key: "class", label: "class", hint: "android view class" },
  { key: "pkg",   label: "pkg",   hint: "package name" },
  { key: "xpath", label: "xpath", hint: "preferred or absolute xpath" },
];

type MatchInfo = {
  /** Node sets keyed by attribute that matched — used for scope filtering. */
  byType: Record<Scope, Set<UiNode>>;
  /** Per-attribute match counts shown in the chip badges. */
  counts: Record<Scope, number>;
  /** Per-node match types — used for inline badges on rows. */
  perNode: Map<UiNode, Set<Scope>>;
};

const INDENT_PX = 14;

export default function InspectorPanel({
  tree, error, busy, onRefresh, selectedPath, onSelect, onSaveAsElement,
}: Props) {
  const [search, setSearch] = useState("");
  const [scope, setScope] = useState<Scope>("all");
  const [expanded, setExpanded] = useState<Set<UiNode>>(new Set());

  // Build per-attribute match sets + per-scope counts. Computed once per (tree, search) tick.
  const matchInfo = useMemo<MatchInfo | null>(() => {
    if (!tree || !search) return null;
    const q = search.toLowerCase();
    const byType: Record<Scope, Set<UiNode>> = {
      all:   new Set(),
      id:    new Set(),
      class: new Set(),
      text:  new Set(),
      a11y:  new Set(),
      hint:  new Set(),
      pkg:   new Set(),
      xpath: new Set(),
    };
    const counts: Record<Scope, number> = {
      all: 0, id: 0, class: 0, text: 0, a11y: 0, hint: 0, pkg: 0, xpath: 0,
    };
    const perNode = new Map<UiNode, Set<Scope>>();

    const stack: UiNode[] = [];
    function walk(node: UiNode) {
      stack.push(node);
      const types = new Set<Scope>();
      if (node.resourceId && node.resourceId.toLowerCase().includes(q))                 types.add("id");
      if (node.className.toLowerCase().includes(q))                                     types.add("class");
      if (node.text && node.text.toLowerCase().includes(q))                             types.add("text");
      if (node.contentDescription && node.contentDescription.toLowerCase().includes(q)) types.add("a11y");
      if (node.hint && node.hint.toLowerCase().includes(q))                             types.add("hint");
      if (node.packageName && node.packageName.toLowerCase().includes(q))               types.add("pkg");
      // xpath: compute for this node's path; both preferred and absolute searched.
      const path = stack.slice();
      const xp1 = preferredXPath(path).toLowerCase();
      const xp2 = absoluteXPath(path).toLowerCase();
      if (xp1.includes(q) || xp2.includes(q))                                   types.add("xpath");
      if (types.size > 0) {
        perNode.set(node, types);
        byType.all.add(node);
        counts.all++;
        types.forEach((t) => { byType[t].add(node); counts[t]++; });
      }
      for (const c of node.children) walk(c);
      stack.pop();
    }
    walk(tree);
    return { byType, counts, perNode };
  }, [tree, search]);

  // Visible set = scope's matches + their ancestors so the tree path is reachable.
  const showSet = useMemo<Set<UiNode> | null>(() => {
    if (!matchInfo || !tree) return null;
    const matches = matchInfo.byType[scope];
    const out = new Set<UiNode>(matches);
    const stack: UiNode[] = [];
    function walk(node: UiNode) {
      stack.push(node);
      if (matches.has(node)) for (const a of stack) out.add(a);
      for (const c of node.children) walk(c);
      stack.pop();
    }
    walk(tree);
    return out;
  }, [matchInfo, scope, tree]);

  useEffect(() => {
    if (!tree) { setExpanded(new Set()); return; }
    const initial = new Set<UiNode>();
    function exp(n: UiNode, d: number) {
      if (d < 2) {
        initial.add(n);
        n.children.forEach((c) => exp(c, d + 1));
      }
    }
    exp(tree, 0);
    setExpanded(initial);
  }, [tree]);

  // When the search text disappears, reset scope to "all" so a stale "id" filter doesn't surprise.
  useEffect(() => { if (!search) setScope("all"); }, [search]);

  useEffect(() => {
    if (!selectedPath || selectedPath.length === 0) return;
    setExpanded((prev) => {
      const next = new Set(prev);
      for (const n of selectedPath) next.add(n);
      return next;
    });
  }, [selectedPath]);

  const rows = useMemo<FlatRow[]>(() => {
    if (!tree) return [];
    const out: FlatRow[] = [];
    const stack: UiNode[] = [];
    function visit(node: UiNode, depth: number) {
      if (showSet && !showSet.has(node)) return;
      stack.push(node);
      out.push({ node, depth, path: stack.slice(), hasChildren: node.children.length > 0 });
      if (showSet || expanded.has(node)) {
        for (const c of node.children) visit(c, depth + 1);
      }
      stack.pop();
    }
    visit(tree, 0);
    return out;
  }, [tree, expanded, showSet]);

  function toggle(node: UiNode) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(node)) next.delete(node); else next.add(node);
      return next;
    });
  }

  function expandAll() {
    if (!tree) return;
    const all = new Set<UiNode>();
    (function add(n: UiNode) { all.add(n); n.children.forEach(add); })(tree);
    setExpanded(all);
  }

  function collapseAll() {
    if (!tree) return;
    setExpanded(new Set([tree]));
  }

  const allOpen = !!tree && rows.every((r) => !r.hasChildren || expanded.has(r.node));

  return (
    <div className="flex flex-col gap-3 h-full min-h-0">
      <header className="flex items-center justify-between">
        <div>
          <div className="text-xs font-semibold text-ink-primary">UI hierarchy</div>
          <div className="text-[11px] text-ink-muted">
            {tree ? `${rows.length} visible / ${totalNodes(tree)} total` : "AccessibilityNodeInfo snapshot"}
          </div>
        </div>
        <Button
          size="sm"
          variant={tree ? "secondary" : "primary"}
          leftIcon={<RefreshCcw size={12} className={busy ? "animate-spin" : undefined} />}
          onClick={onRefresh}
          disabled={busy}
        >
          {busy ? "Dumping" : tree ? "Refresh" : "Inspect"}
        </Button>
      </header>

      {error && (
        <div className="rounded-md border border-danger-500/30 bg-danger-500/10 text-danger-500 px-3 py-2 text-xs">
          {error}
        </div>
      )}

      {!tree && !error && (
        <div className="flex-1 rounded-md border border-dashed border-surface-border">
          <EmptyState
            icon={<Search size={18} />}
            title="No snapshot yet"
            description="Click Inspect to capture the active window's UI tree from the device."
          />
        </div>
      )}

      {tree && (
        <>
          <div className="flex items-center gap-1.5">
            <div className="relative flex-1">
              <Search size={12} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-ink-muted" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search id, class, text or xpath"
                className="input pl-7 pr-7 text-xs h-8"
              />
              {search && (
                <button
                  onClick={() => setSearch("")}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-ink-muted hover:text-ink-primary"
                  title="Clear search"
                >
                  <X size={12} />
                </button>
              )}
            </div>
            <button
              onClick={allOpen ? collapseAll : expandAll}
              className="h-8 w-8 rounded-md border border-surface-border bg-surface hover:bg-surface-muted text-ink-secondary hover:text-ink-primary flex items-center justify-center shrink-0"
              title={allOpen ? "Collapse all" : "Expand all"}
            >
              {allOpen ? <ChevronsDownUp size={13} /> : <ChevronsUpDown size={13} />}
            </button>
          </div>

          {/* Scope chips — appear when a search is active. Each chip shows the count of
              matches by attribute; clicking one filters the tree to just those matches. */}
          {matchInfo && (
            <div className="flex items-center gap-1 flex-wrap">
              {SCOPES.map((s) => {
                const n = matchInfo.counts[s.key];
                const disabled = s.key !== "all" && n === 0;
                const active = scope === s.key;
                return (
                  <button
                    key={s.key}
                    onClick={() => !disabled && setScope(s.key)}
                    disabled={disabled}
                    title={s.hint}
                    className={cn(
                      "inline-flex items-center gap-1.5 h-6 px-2 rounded-md text-[10px] uppercase tracking-wider font-semibold border transition-colors",
                      active
                        ? "bg-brand-500/15 border-brand-500/40 text-brand-300"
                        : disabled
                          ? "border-surface-border text-ink-muted/50 cursor-not-allowed"
                          : "border-surface-border text-ink-secondary hover:text-ink-primary hover:border-surface-border",
                    )}
                  >
                    <span>{s.label}</span>
                    <span className={cn(
                      "min-w-[1.25rem] h-4 px-1 rounded text-[10px] font-mono text-center leading-4",
                      active
                        ? "bg-brand-500/30 text-brand-200"
                        : n > 0 ? "bg-surface-muted text-ink-primary" : "bg-surface text-ink-muted",
                    )}>{n}</span>
                  </button>
                );
              })}
            </div>
          )}

          {selectedPath && selectedPath.length > 1 && (
            <Breadcrumb path={selectedPath} onSelect={onSelect} />
          )}

          <div className="flex-1 overflow-auto border border-surface-border rounded-md bg-surface min-h-[220px]">
            {rows.length === 0 ? (
              <div className="px-3 py-6 text-center text-xs text-ink-muted">
                {search ? `No nodes match “${search}” in this scope.` : "No nodes match the filter."}
              </div>
            ) : rows.map((row) => (
              <Row
                key={refKey(row.node)}
                row={row}
                isSelected={selectedPath ? selectedPath[selectedPath.length - 1] === row.node : false}
                expanded={expanded.has(row.node)}
                matchTypes={matchInfo?.perNode.get(row.node)}
                query={search}
                onSelect={() => onSelect(row.path)}
                onToggle={() => toggle(row.node)}
              />
            ))}
          </div>

          {selectedPath && selectedPath.length > 0 && (
            <SelectionDetails
              path={selectedPath}
              query={search}
              onSaveAsElement={onSaveAsElement ? () => onSaveAsElement(selectedPath) : undefined}
            />
          )}
        </>
      )}
    </div>
  );
}

/* ─────────────────────────────  Row  ────────────────────────────── */

function Row({
  row, isSelected, expanded, matchTypes, query, onSelect, onToggle,
}: {
  row: FlatRow;
  isSelected: boolean;
  expanded: boolean;
  matchTypes: Set<Scope> | undefined;
  query: string;
  onSelect: () => void;
  onToggle: () => void;
}) {
  const { node, depth, hasChildren } = row;
  const cls = lastSegment(node.className);
  const label = node.text || node.contentDescription || (node.hint ? `(${node.hint})` : undefined);
  const rowRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (isSelected) rowRef.current?.scrollIntoView({ block: "nearest" });
  }, [isSelected]);

  return (
    <div
      ref={rowRef}
      onClick={onSelect}
      className={cn(
        "relative flex items-stretch h-7 cursor-pointer text-[11px] transition-colors",
        isSelected
          ? "bg-brand-500/15 text-ink-primary before:absolute before:left-0 before:top-0 before:bottom-0 before:w-[2px] before:bg-brand-500"
          : "hover:bg-surface-muted/60 text-ink-secondary",
      )}
    >
      {Array.from({ length: depth }).map((_, i) => (
        <span
          key={i}
          aria-hidden
          className="border-r border-surface-border/40 shrink-0"
          style={{ width: INDENT_PX }}
        />
      ))}

      <div className="flex items-center gap-1.5 flex-1 min-w-0 px-2 overflow-hidden">
        {hasChildren ? (
          <button
            onClick={(e) => { e.stopPropagation(); onToggle(); }}
            className="w-3 h-3 flex items-center justify-center text-ink-muted hover:text-ink-primary shrink-0"
            tabIndex={-1}
          >
            <ChevronRight size={11} className={cn("transition-transform", expanded && "rotate-90")} />
          </button>
        ) : (
          <span className="w-3 h-3 flex items-center justify-center shrink-0">
            <span className="w-1 h-1 rounded-full bg-ink-muted/50" />
          </span>
        )}

        <span className="font-mono text-brand-300 truncate">{highlight(cls, query)}</span>

        {node.resourceId && (
          <span className="font-mono text-success-500 truncate shrink-0">#{highlight(tail(node.resourceId), query)}</span>
        )}
        {label && (
          <span className="text-ink-muted truncate" title={label}>“{highlight(label, query)}”</span>
        )}

        {/* Match-type badges + behaviour pips on the right */}
        <span className="ml-auto flex items-center gap-1 shrink-0">
          {matchTypes && [...matchTypes].filter((t) => t !== "all").map((t) => (
            <span
              key={t}
              title={`matched: ${t}`}
              className="text-[9px] font-mono uppercase rounded px-1 py-0 border border-warning-500/30 bg-warning-500/10 text-warning-500"
            >
              {t}
            </span>
          ))}
          <span className="flex items-center gap-1 opacity-70">
            {node.clickable  && <Pip title="clickable"  className="bg-brand-400" />}
            {node.scrollable && <Pip title="scrollable" className="bg-warning-500" />}
            {node.focused    && <Pip title="focused"    className="bg-success-500" />}
          </span>
        </span>
      </div>
    </div>
  );
}

function Pip({ title, className }: { title: string; className: string }) {
  return <span title={title} className={cn("w-1.5 h-1.5 rounded-full", className)} />;
}

/* Highlights occurrences of {@code q} inside {@code text} with a subtle warning-coloured mark. */
function highlight(text: string, q: string) {
  if (!q || !text) return text;
  const lower = text.toLowerCase();
  const query = q.toLowerCase();
  const parts: React.ReactNode[] = [];
  let i = 0;
  while (i < text.length) {
    const idx = lower.indexOf(query, i);
    if (idx < 0) { parts.push(text.slice(i)); break; }
    if (idx > i) parts.push(text.slice(i, idx));
    parts.push(
      <mark
        key={idx}
        className="bg-warning-500/30 text-warning-500 rounded-[2px] px-0.5"
      >
        {text.slice(idx, idx + q.length)}
      </mark>,
    );
    i = idx + q.length;
  }
  return <>{parts}</>;
}

/* ─────────────────────────  Breadcrumb  ─────────────────────────── */

function Breadcrumb({
  path, onSelect,
}: { path: UiNode[]; onSelect: (p: UiNode[]) => void }) {
  const MAX_VISIBLE = 5;
  const truncated = path.length > MAX_VISIBLE;
  const visible = truncated ? path.slice(-MAX_VISIBLE) : path;
  const startIdx = path.length - visible.length;

  return (
    <nav
      aria-label="Selected node path"
      className="rounded-md border border-surface-border bg-surface-raised/40 px-2 py-1.5 flex items-center gap-1 text-[11px] overflow-hidden"
    >
      {truncated && (
        <>
          <button
            className="text-ink-muted hover:text-ink-primary shrink-0 px-1"
            onClick={() => onSelect(path.slice(0, 1))}
            title="Root"
          >
            …
          </button>
          <ChevronRight size={10} className="text-ink-muted shrink-0" />
        </>
      )}
      {visible.map((node, i) => {
        const idx = startIdx + i;
        const isLast = idx === path.length - 1;
        return (
          <Fragment key={idx}>
            {i > 0 && <ChevronRight size={10} className="text-ink-muted shrink-0" />}
            <button
              onClick={() => onSelect(path.slice(0, idx + 1))}
              className={cn(
                "truncate max-w-[140px] text-left font-mono",
                isLast
                  ? "text-brand-300 font-semibold"
                  : "text-ink-secondary hover:text-ink-primary",
              )}
              title={fullLabel(node)}
            >
              {lastSegment(node.className)}
              {node.resourceId && <span className="text-success-500/80">#{tail(node.resourceId)}</span>}
            </button>
          </Fragment>
        );
      })}
    </nav>
  );
}

function fullLabel(n: UiNode): string {
  let s = n.className;
  if (n.resourceId) s += " #" + tail(n.resourceId);
  if (n.text) s += ` "${n.text}"`;
  return s;
}

/* ─────────────────────  Selection details  ──────────────────────── */

function SelectionDetails({
  path, query, onSaveAsElement,
}: { path: UiNode[]; query: string; onSaveAsElement?: () => void }) {
  const node = path[path.length - 1];
  const xpath = preferredXPath(path);
  const absolute = absoluteXPath(path);

  return (
    <div className="rounded-md border border-surface-border bg-surface text-xs shrink-0">
      <div className="px-3 py-2 border-b border-surface-border flex items-center justify-between gap-2">
        <span className="font-semibold text-ink-primary font-mono truncate">{lastSegment(node.className)}</span>
        <div className="flex items-center gap-2 shrink-0">
          <span className="text-ink-muted font-mono text-[10px]">[{node.bounds.join(", ")}]</span>
          {onSaveAsElement && (
            <button
              onClick={onSaveAsElement}
              className="inline-flex items-center gap-1 text-[10px] uppercase tracking-wider font-semibold px-2 py-0.5 rounded border border-brand-500/40 bg-brand-500/10 text-brand-300 hover:bg-brand-500/20"
              title="Save this node into the element repository"
            >
              + Save element
            </button>
          )}
        </div>
      </div>
      <div className="p-3 space-y-2 max-h-[420px] overflow-auto">
        <Field label="resource-id"  value={node.resourceId ?? ""}             mono query={query} />
        <Field label="accessibility id" value={node.contentDescription ?? ""}      query={query} />
        <Field label="text"         value={node.text ?? ""}                        query={query} />
        <Field label="hint"         value={node.hint ?? ""}                        query={query} />
        <Field label="input-type"   value={node.inputType ?? ""}              mono query={query} />
        <Field label="class"        value={node.className}                    mono query={query} />
        <Field label="package"      value={node.packageName ?? ""}            mono query={query} />
        <Field label="index"        value={node.index != null ? String(node.index) : ""} mono query={query} />
        <Field label="xpath"        value={xpath}                             mono query={query} />
        <Field label="abs xpath"    value={absolute}                          mono query={query} />

        <div className="flex gap-1.5 flex-wrap pt-2">
          <Flag label="clickable"  on={node.clickable} />
          <Flag label="focusable"  on={!!node.focusable} />
          <Flag label="enabled"    on={node.enabled !== false} />
          <Flag label="checkable"  on={!!node.checkable} />
          <Flag label="scrollable" on={!!node.scrollable} />
          {node.password && <Flag label="password" on />}
        </div>

        <LocatorsBlock node={node} path={path} />
      </div>
    </div>
  );
}

/* ─────────────────  Generated locators (copy-to-clipboard)  ──────────────── */

function LocatorsBlock({ node, path }: { node: UiNode; path: UiNode[] }) {
  const groups: { title: string; items: LocatorSnippet[] }[] = [
    { title: "Appium",       items: appiumLocators(node, path) },
    { title: "UiAutomator",  items: uiAutomatorSelector(node).slice(0, 1) },
    { title: "Espresso",     items: espressoMatchers(node) },
    { title: "WebDriverIO",  items: webdriverIOSelectors(node, path) },
  ].filter((g) => g.items.length > 0);

  if (groups.length === 0) return null;

  return (
    <div className="pt-3 mt-1 border-t border-surface-border space-y-3">
      <div className="text-[10px] uppercase tracking-wider text-ink-muted font-semibold">
        Test-code locators
      </div>
      {groups.map((g) => (
        <div key={g.title} className="space-y-1">
          <div className="text-[10px] uppercase tracking-wider text-brand-300 font-semibold">{g.title}</div>
          <div className="space-y-1">
            {g.items.map((it, i) => <LocatorRow key={i} snippet={it} />)}
          </div>
        </div>
      ))}
    </div>
  );
}

function LocatorRow({ snippet }: { snippet: LocatorSnippet }) {
  const [copied, setCopied] = useState(false);
  return (
    <div className="flex items-start gap-2 group rounded-md bg-surface-raised/40 border border-surface-border px-2 py-1.5">
      <div className="flex-1 min-w-0">
        <div className="text-[10px] uppercase tracking-wider text-ink-muted">{snippet.label}</div>
        <code className="block text-[11px] font-mono text-ink-primary break-all">{snippet.code}</code>
      </div>
      <button
        onClick={(e) => {
          e.stopPropagation();
          navigator.clipboard.writeText(snippet.code);
          setCopied(true);
          setTimeout(() => setCopied(false), 1200);
        }}
        className="text-ink-muted hover:text-ink-primary p-1 rounded hover:bg-surface-muted shrink-0"
        title="Copy"
      >
        {copied ? <Check size={12} className="text-success-500" /> : <Copy size={12} />}
      </button>
    </div>
  );
}

function Field({
  label, value, mono, query,
}: { label: string; value: string; mono?: boolean; query: string }) {
  const [copied, setCopied] = useState(false);
  if (!value) return null;
  return (
    <div className="flex items-start gap-2 group">
      <div className="w-24 shrink-0 text-ink-muted text-[10px] uppercase tracking-wide pt-0.5">{label}</div>
      <div className={cn("flex-1 break-all text-ink-primary", mono && "font-mono text-[11px]")}>
        {highlight(value, query)}
      </div>
      <button
        onClick={(e) => {
          e.stopPropagation();
          navigator.clipboard.writeText(value);
          setCopied(true);
          setTimeout(() => setCopied(false), 1200);
        }}
        className="text-ink-muted hover:text-ink-primary p-1 rounded hover:bg-surface-muted opacity-0 group-hover:opacity-100 transition-opacity"
        title="Copy"
      >
        {copied ? <Check size={12} className="text-success-500" /> : <Copy size={12} />}
      </button>
    </div>
  );
}

function Flag({ label, on }: { label: string; on: boolean }) {
  return (
    <span className={cn(
      "text-[10px] uppercase tracking-wider rounded px-1.5 py-0.5 border",
      on
        ? "border-success-500/30 bg-success-500/10 text-success-500"
        : "border-surface-border text-ink-muted",
    )}>
      {label}
    </span>
  );
}

/* ───────────────────────────  helpers  ──────────────────────────── */

function tail(rid: string): string {
  const i = rid.lastIndexOf("/");
  return i < 0 ? rid : rid.substring(i + 1);
}

function totalNodes(root: UiNode): number {
  let n = 0;
  (function visit(x: UiNode) { n++; x.children.forEach(visit); })(root);
  return n;
}

const refMap = new WeakMap<UiNode, number>();
let refCounter = 0;
function refKey(n: UiNode): number {
  let k = refMap.get(n);
  if (k === undefined) { k = ++refCounter; refMap.set(n, k); }
  return k;
}
