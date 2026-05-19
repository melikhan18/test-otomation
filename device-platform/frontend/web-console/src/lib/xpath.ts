/** UI-Automator-style locators. Mirrors what Appium produces so test code carries over. */

export type UiNode = {
  className: string;
  packageName?: string;
  resourceId?: string | null;
  text?: string | null;
  contentDescription?: string | null;
  /** EditText placeholder (`android:hint`) — API 26+. */
  hint?: string | null;
  /** Human label for android.text.InputType (e.g. "text-password", "number", "phone"). */
  inputType?: string | null;
  /** Zero-based position among the parent's children — useful for sibling-relative locators. */
  index?: number;
  bounds: [number, number, number, number];
  clickable: boolean;
  longClickable?: boolean;
  focusable?: boolean;
  focused?: boolean;
  scrollable?: boolean;
  enabled?: boolean;
  checkable?: boolean;
  checked?: boolean;
  selected?: boolean;
  password?: boolean;
  children: UiNode[];
};

export function lastSegment(className: string): string {
  const i = className.lastIndexOf(".");
  return i < 0 ? className : className.substring(i + 1);
}

/** Absolute xpath using positions among siblings of the same class — UI-Automator style. */
export function absoluteXPath(path: UiNode[]): string {
  let out = "";
  for (let i = 0; i < path.length; i++) {
    const node = path[i];
    const parent = i === 0 ? null : path[i - 1];
    if (!parent) { out += "/" + (node.className || "*"); continue; }
    const same = parent.children.filter((c) => c.className === node.className);
    const idx = same.indexOf(node) + 1;
    out += "/" + (node.className || "*") + "[" + idx + "]";
  }
  return out;
}

/** Prefer a resource-id locator when available — robust under layout changes. */
export function preferredXPath(path: UiNode[]): string {
  const last = path[path.length - 1];
  if (last?.resourceId) return `//${last.className || "*"}[@resource-id="${last.resourceId}"]`;
  return absoluteXPath(path);
}

/* ─────────────────────  Generated test-code locators  ─────────────────────
 * Each generator returns 0..N {label, code} pairs ordered most-reliable first.
 * UI shows them in a copy-to-clipboard list under SelectionDetails.
 */

export type LocatorSnippet = { label: string; code: string };

const esc = (s: string) => s.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
const idTail = (rid: string) => { const i = rid.lastIndexOf("/"); return i < 0 ? rid : rid.slice(i + 1); };

/** Appium WebDriverIO selector strings. */
export function appiumLocators(node: UiNode, path: UiNode[]): LocatorSnippet[] {
  const out: LocatorSnippet[] = [];
  if (node.resourceId)         out.push({ label: "Appium · id",                 code: `id=${node.resourceId}` });
  if (node.contentDescription) out.push({ label: "Appium · accessibility id",   code: `~${node.contentDescription}` });
  if (node.text)               out.push({ label: "Appium · xpath (by text)",    code: `//${node.className}[@text="${esc(node.text)}"]` });
  out.push({ label: "Appium · xpath", code: preferredXPath(path) });
  return out;
}

/** Android UiAutomator selector chain — Appium's "-android uiautomator" strategy. */
export function uiAutomatorSelector(node: UiNode): LocatorSnippet[] {
  const parts: string[] = [];
  if (node.resourceId)         parts.push(`.resourceId("${esc(node.resourceId)}")`);
  if (node.contentDescription) parts.push(`.description("${esc(node.contentDescription)}")`);
  if (node.text)               parts.push(`.text("${esc(node.text)}")`);
  if (parts.length === 0)      parts.push(`.className("${esc(node.className)}")`);
  return [{ label: "UiSelector",            code: `new UiSelector()${parts.join("")}` },
          { label: "UiAutomator (Appium)",  code: `new UiSelector()${parts.join("")}` }];
}

/** Espresso (Java/Kotlin) ViewMatcher one-liners. */
export function espressoMatchers(node: UiNode): LocatorSnippet[] {
  const out: LocatorSnippet[] = [];
  if (node.resourceId) out.push({ label: "Espresso · withId",
                                  code: `onView(withId(R.id.${idTail(node.resourceId)}))` });
  if (node.text)               out.push({ label: "Espresso · withText",
                                          code: `onView(withText("${esc(node.text)}"))` });
  if (node.contentDescription) out.push({ label: "Espresso · withContentDescription",
                                          code: `onView(withContentDescription("${esc(node.contentDescription)}"))` });
  if (node.hint)               out.push({ label: "Espresso · withHint",
                                          code: `onView(withHint("${esc(node.hint)}"))` });
  return out;
}

/** WebDriverIO short-form selectors. */
export function webdriverIOSelectors(node: UiNode, path: UiNode[]): LocatorSnippet[] {
  const out: LocatorSnippet[] = [];
  if (node.resourceId)         out.push({ label: "WebDriverIO · id",                  code: `$('id=${node.resourceId}')` });
  if (node.contentDescription) out.push({ label: "WebDriverIO · accessibility id",    code: `$('~${node.contentDescription}')` });
  out.push({ label: "WebDriverIO · xpath", code: `$('${preferredXPath(path).replace(/'/g, "\\'")}')` });
  return out;
}

/** Compute the index of this node among its siblings of the same class — Appium-style. */
export function classSiblingIndex(parent: UiNode | undefined, node: UiNode): number {
  if (!parent) return 1;
  return parent.children.filter((c) => c.className === node.className).indexOf(node) + 1;
}

/** Find the leaf node whose bounds enclose (x,y), picking the deepest match. */
export function nodeAt(root: UiNode, x: number, y: number): UiNode[] | null {
  const path: UiNode[] = [];
  function recur(n: UiNode): boolean {
    const [l, t, r, b] = n.bounds;
    if (x < l || x > r || y < t || y > b) return false;
    path.push(n);
    for (const c of n.children) if (recur(c)) return true;
    return true;
  }
  return recur(root) ? path : null;
}
