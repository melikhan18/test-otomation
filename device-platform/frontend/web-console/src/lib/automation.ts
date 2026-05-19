import { api } from "./api";

/* ───────────────────────────── Types ──────────────────────────────── */

export type LocatorStrategy =
  | "RESOURCE_ID"
  | "ACCESSIBILITY_ID"
  | "TEXT"
  | "CLASS"
  | "XPATH";

export type Locator = { strategy: LocatorStrategy; value: string };

export type ElementView = {
  id: number;
  productId: number;
  name: string;
  description: string | null;
  primaryStrategy: LocatorStrategy;
  primaryValue: string;
  fallbackLocators: Locator[];
  screenshotData: string | null;     // data URL
  sampleBounds: string | null;       // "[l,t,r,b]"
  sampleClass: string | null;
  sampleText: string | null;
  sampleResourceId: string | null;
  createdByUserId: number;
  createdAt: string;
  updatedAt: string;
};

export type ElementCreate = {
  name: string;
  description?: string | null;
  primaryStrategy: LocatorStrategy;
  primaryValue: string;
  fallbackLocators?: Locator[];
  screenshotData?: string | null;
  sampleBounds?: string | null;
  sampleClass?: string | null;
  sampleText?: string | null;
  sampleResourceId?: string | null;
};

export type ElementUpdate = {
  name: string;
  description?: string | null;
  primaryStrategy: LocatorStrategy;
  primaryValue: string;
  fallbackLocators?: Locator[];
};

export type TestDataView = {
  id: number;
  productId: number;
  name: string;
  environment: string;
  value: string;
  description: string | null;
  sensitive: boolean;
  masked: boolean;
  createdByUserId: number;
  createdAt: string;
  updatedAt: string;
};

export type TestDataCreate = {
  name: string;
  environment: string;
  value: string;
  description?: string | null;
  sensitive: boolean;
};

export type TestDataUpdate = TestDataCreate;

/* ───────────────────────────── API ───────────────────────────────── */

export const elementApi = {
  list:   (q?: string) =>
    api.get<ElementView[]>("/api/automation/elements", { params: q ? { q } : {} }).then((r) => r.data),
  get:    (id: number) => api.get<ElementView>(`/api/automation/elements/${id}`).then((r) => r.data),
  create: (body: ElementCreate) => api.post<ElementView>("/api/automation/elements", body).then((r) => r.data),
  update: (id: number, body: ElementUpdate) =>
    api.put<ElementView>(`/api/automation/elements/${id}`, body).then((r) => r.data),
  delete: (id: number) => api.delete<void>(`/api/automation/elements/${id}`).then((r) => r.data),
};

export const testDataApi = {
  list: (environment?: string, reveal = false) =>
    api
      .get<TestDataView[]>("/api/automation/test-data", {
        params: { ...(environment ? { environment } : {}), reveal },
      })
      .then((r) => r.data),
  environments: () => api.get<string[]>("/api/automation/test-data/environments").then((r) => r.data),
  get:    (id: number, reveal = false) =>
    api.get<TestDataView>(`/api/automation/test-data/${id}`, { params: { reveal } }).then((r) => r.data),
  create: (body: TestDataCreate) =>
    api.post<TestDataView>("/api/automation/test-data", body).then((r) => r.data),
  update: (id: number, body: TestDataUpdate) =>
    api.put<TestDataView>(`/api/automation/test-data/${id}`, body).then((r) => r.data),
  delete: (id: number) => api.delete<void>(`/api/automation/test-data/${id}`).then((r) => r.data),
};

/* ─────────────────  Locator generation from inspector  ───────────── */

/** Ordered list of candidate locators for a node — most reliable first. */
export function generateLocators(
  node: {
    className: string;
    resourceId?: string | null;
    text?: string | null;
    contentDescription?: string | null;
  },
  preferredXPath: string,
  absoluteXPath: string,
): { primary: Locator; fallbacks: Locator[] } {
  const candidates: Locator[] = [];
  if (node.resourceId)         candidates.push({ strategy: "RESOURCE_ID",      value: node.resourceId });
  if (node.contentDescription) candidates.push({ strategy: "ACCESSIBILITY_ID", value: node.contentDescription });
  if (node.text)               candidates.push({ strategy: "TEXT",             value: node.text });
  candidates.push({ strategy: "XPATH", value: preferredXPath });
  if (absoluteXPath !== preferredXPath) candidates.push({ strategy: "XPATH", value: absoluteXPath });
  return {
    primary: candidates[0] ?? { strategy: "XPATH", value: absoluteXPath },
    fallbacks: candidates.slice(1, 4),
  };
}

/** Suggest a kebab-case element name from a node's attributes. */
export function suggestElementName(node: {
  className: string;
  resourceId?: string | null;
  text?: string | null;
  contentDescription?: string | null;
}): string {
  const kebab = (s: string) =>
    s
      .toLowerCase()
      .normalize("NFD")
      .replace(/[̀-ͯ]/g, "")
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "")
      .slice(0, 60);

  if (node.resourceId) {
    const tail = node.resourceId.split("/").pop() ?? node.resourceId;
    return kebab(tail);
  }
  if (node.text) {
    const cls = lastSegment(node.className);
    return kebab(`${cls}-${node.text}`);
  }
  if (node.contentDescription) {
    return kebab(node.contentDescription);
  }
  return kebab(lastSegment(node.className)) + "-" + Math.random().toString(36).slice(2, 6);
}

function lastSegment(s: string): string {
  const i = s.lastIndexOf(".");
  return i < 0 ? s : s.slice(i + 1);
}

/** Crop the snapshot data URL to a node's bounds. Returns a smaller PNG data URL. */
export async function cropSnapshotForElement(
  snapshotDataUrl: string,
  bounds: [number, number, number, number],
  realWidth: number,
  realHeight: number,
  maxDim = 200,
): Promise<string | null> {
  try {
    const img = await loadImage(snapshotDataUrl);
    const [l, t, r, b] = bounds;
    const sx = (l / realWidth)  * img.naturalWidth;
    const sy = (t / realHeight) * img.naturalHeight;
    const sw = ((r - l) / realWidth)  * img.naturalWidth;
    const sh = ((b - t) / realHeight) * img.naturalHeight;
    if (sw <= 0 || sh <= 0) return null;
    const scale = Math.min(1, maxDim / Math.max(sw, sh));
    const canvas = document.createElement("canvas");
    canvas.width  = Math.max(1, Math.round(sw * scale));
    canvas.height = Math.max(1, Math.round(sh * scale));
    const ctx = canvas.getContext("2d");
    if (!ctx) return null;
    ctx.drawImage(img, sx, sy, sw, sh, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL("image/png");
  } catch {
    return null;
  }
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((res, rej) => {
    const img = new Image();
    img.onload = () => res(img);
    img.onerror = rej;
    img.src = src;
  });
}
