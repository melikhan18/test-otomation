package com.devicefarm.automation.service.run;

import com.devicefarm.automation.domain.ElementEntity;
import com.devicefarm.automation.locator.Locator;
import com.devicefarm.automation.locator.LocatorStrategy;
import com.devicefarm.automation.service.LocatorJson;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks the AccessibilityNodeInfo tree (received from the bridge's inspect endpoint) and
 * resolves an {@link ElementEntity} to a concrete node — i.e. its bounds + the locator
 * that actually matched (self-healing trace).
 *
 * Locators are tried in order: primary, then each fallback. The first hit wins and is
 * returned via {@link Hit#resolvedBy()} so the run report can show e.g. "primary
 * RESOURCE_ID missed, fell back to XPATH" — letting the user know it's time to refresh
 * the element repository.
 */
public class LocatorResolver {

    private static final Logger log = LoggerFactory.getLogger(LocatorResolver.class);

    public static Hit resolve(JsonNode inspect, ElementEntity element) {
        if (inspect == null || !inspect.has("root")) return null;

        List<Locator> candidates = new ArrayList<>();
        candidates.add(new Locator(element.getPrimaryStrategy(), element.getPrimaryValue()));
        candidates.addAll(LocatorJson.read(element.getFallbackLocatorsJson()));

        JsonNode root = inspect.get("root");
        int idx = 0;
        for (Locator c : candidates) {
            // XPath is path-walked from root (not a free tree-search) — otherwise the
            // matcher would self-heal too aggressively and click random TextViews on
            // unrelated screens just because the leaf class name happened to match.
            NodeMatch m = (c.strategy() == LocatorStrategy.XPATH)
                    ? findByXPath(root, c.value())
                    : find(root, c, new ArrayDeque<String>());
            if (m != null) {
                if (idx > 0) {
                    log.info("element {} primary missed, fell back to {} ({})",
                            element.getName(), c.strategy(), c.value());
                }
                return new Hit(m.node(), m.bounds(), c, idx, m.xpath());
            }
            idx++;
        }
        return null;
    }

    /* ────────────────────  Tree walk per strategy  ──────────────────── */

    private static NodeMatch find(JsonNode node, Locator c, java.util.ArrayDeque<String> classChain) {
        if (matches(node, c)) {
            return new NodeMatch(node, boundsOf(node), buildXPath(classChain, node));
        }
        JsonNode children = node.get("children");
        if (children != null && children.isArray()) {
            classChain.push(safeText(node, "className"));
            try {
                for (JsonNode child : children) {
                    NodeMatch found = find(child, c, classChain);
                    if (found != null) return found;
                }
            } finally {
                classChain.pop();
            }
        }
        return null;
    }

    private static boolean matches(JsonNode node, Locator c) {
        switch (c.strategy()) {
            case RESOURCE_ID:      return eq(node, "resourceId", c.value());
            case ACCESSIBILITY_ID: return eq(node, "contentDescription", c.value());
            case TEXT:             return eq(node, "text", c.value());
            case CLASS:            return eq(node, "className", c.value());
            case XPATH:            return false; // XPath is handled by findByXPath at the top level
        }
        return false;
    }

    /* ──────────────────────  Strict XPath walker  ──────────────────────
     * Frontend produces absolute paths in Appium / UI-Automator style:
     *
     *   /android.widget.FrameLayout
     *   /android.widget.LinearLayout[1]
     *   /android.widget.FrameLayout[3]
     *   /android.widget.TextView[2]
     *
     * Each segment is the FULL class name + 1-based index among siblings of the same
     * class (the index is omitted on the root). We walk from root and require every
     * segment to match — no free tree-search, no leaf-only fallback. This stops
     * "self-heal" from matching some other TextView on an unrelated screen.
     */

    private record XPathSeg(String cls, int index) {}

    private static NodeMatch findByXPath(JsonNode root, String xpath) {
        if (root == null || xpath == null || xpath.isBlank()) return null;

        // Strip leading '/' so split() doesn't yield an empty first element.
        String body = xpath.startsWith("/") ? xpath.substring(1) : xpath;
        String[] raw = body.split("/");
        if (raw.length == 0) return null;

        XPathSeg[] segs = new XPathSeg[raw.length];
        for (int i = 0; i < raw.length; i++) {
            XPathSeg s = parseSeg(raw[i]);
            if (s == null || s.cls().isEmpty()) return null;
            segs[i] = s;
        }

        // First segment must match the tree root by class name (index is ignored on root).
        if (!segs[0].cls().equals(safeText(root, "className"))) return null;

        JsonNode current = root;
        for (int i = 1; i < segs.length; i++) {
            current = findChildBySeg(current, segs[i]);
            if (current == null) return null;
        }
        return new NodeMatch(current, boundsOf(current), xpath);
    }

    private static XPathSeg parseSeg(String seg) {
        if (seg == null || seg.isEmpty()) return null;
        int br = seg.indexOf('[');
        if (br < 0) return new XPathSeg(seg, 1);
        int end = seg.indexOf(']', br);
        String cls = seg.substring(0, br);
        if (end <= br) return new XPathSeg(cls, 1);
        try { return new XPathSeg(cls, Integer.parseInt(seg.substring(br + 1, end))); }
        catch (NumberFormatException e) { return new XPathSeg(cls, 1); }
    }

    /** Pick the Nth child whose className equals seg.cls() (N is 1-based, same-class only). */
    private static JsonNode findChildBySeg(JsonNode parent, XPathSeg seg) {
        JsonNode children = parent.get("children");
        if (children == null || !children.isArray()) return null;
        int matchCount = 0;
        for (JsonNode child : children) {
            if (seg.cls().equals(safeText(child, "className"))) {
                if (++matchCount == seg.index()) return child;
            }
        }
        return null;
    }

    private static boolean eq(JsonNode node, String field, String expected) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return false;
        return expected != null && expected.equals(v.asText());
    }

    /* ────────────────────────  helpers  ──────────────────────────── */

    private static int[] boundsOf(JsonNode node) {
        JsonNode b = node.get("bounds");
        if (b == null || !b.isArray() || b.size() < 4) return new int[]{0, 0, 0, 0};
        return new int[]{ b.get(0).asInt(), b.get(1).asInt(), b.get(2).asInt(), b.get(3).asInt() };
    }

    private static String safeText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText();
    }

    private static String buildXPath(java.util.ArrayDeque<String> classChain, JsonNode leaf) {
        var b = new StringBuilder();
        for (var it = classChain.descendingIterator(); it.hasNext(); ) {
            b.append("/").append(lastSegment(it.next()));
        }
        b.append("/").append(lastSegment(safeText(leaf, "className")));
        return b.toString();
    }

    private static String lastSegment(String s) {
        int i = s.lastIndexOf('.');
        return i < 0 ? s : s.substring(i + 1);
    }

    /* ─────────────────────────  results  ─────────────────────────── */

    public record Hit(JsonNode node, int[] bounds, Locator resolvedBy, int fallbackIndex, String xpath) {
        /** Center-x of the matched node — used for tap dispatch. */
        public float centerX() { return (bounds[0] + bounds[2]) / 2f; }
        public float centerY() { return (bounds[1] + bounds[3]) / 2f; }
    }

    private record NodeMatch(JsonNode node, int[] bounds, String xpath) {}
}
