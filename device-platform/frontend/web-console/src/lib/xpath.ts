/** UI-Automator-style locators. Mirrors what Appium produces so test code carries over. */

export type UiNode = {
  className: string;
  packageName?: string;
  resourceId?: string | null;
  text?: string | null;
  contentDescription?: string | null;
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
