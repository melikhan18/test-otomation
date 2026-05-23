package com.qaplatform.android.agent.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Executes remote tap / swipe / key / text commands using AccessibilityService APIs.
 *
 * Notes:
 *  - Must be manually enabled by the user in Settings → Accessibility → Device Farm Agent.
 *  - dispatchGesture / performGlobalAction require {@code canPerformGestures="true"} in the
 *    service config XML.
 *  - On some Samsung/Exynos ROMs a {@code Path} with only a {@code moveTo} is treated as a
 *    zero-length stroke and silently rejected; we add a tiny {@code lineTo} so the path is
 *    always non-degenerate.
 *  - We always pass a callback to {@link #dispatchGesture} so failures are visible in logcat
 *    (filter with {@code adb logcat -s ControlA11y:*}).
 */
class ControlAccessibilityService : AccessibilityService() {

    /**
     * Optional listener for raw {@link AccessibilityEvent}s — set by {@link ApkInstaller}
     * during an install so it can react the moment the PackageInstaller dialog appears,
     * instead of relying purely on a 1.5 s polling loop.
     */
    @Volatile
    var eventListener: ((AccessibilityEvent) -> Unit)? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        eventListener?.let {
            try { it(event) } catch (e: Exception) { Log.w(TAG, "eventListener threw", e) }
        }
    }
    override fun onInterrupt() { /* no-op */ }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "service connected — gestures + globalActions available")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        Log.i(TAG, "service destroyed")
        super.onDestroy()
    }

    fun tap(x: Float, y: Float, durationMs: Long = 80) {
        // Some ROMs reject zero-length paths; add a 1-px displacement so the stroke registers.
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 1f, y + 1f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
        dispatch("tap($x,$y,${durationMs}ms)", GestureDescription.Builder().addStroke(stroke).build())
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
        dispatch("swipe(($x1,$y1)→($x2,$y2),${durationMs}ms)", GestureDescription.Builder().addStroke(stroke).build())
    }

    fun globalAction(action: Int): Boolean {
        val ok = performGlobalAction(action)
        Log.d(TAG, "globalAction($action) → $ok")
        return ok
    }

    fun typeText(text: String): Boolean {
        val focus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focus == null) {
            Log.w(TAG, "typeText skipped — no focused input")
            return false
        }
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        val ok = focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.d(TAG, "typeText(${text.length} chars) → $ok")
        return ok
    }

    /**
     * Find the first node whose text matches one of {@code texts} (case-insensitive,
     * substring on {@code findAccessibilityNodeInfosByText}) and click it. Optionally
     * restricted to nodes whose owning package is {@code packageFilter} — used by the
     * APK installer to target system PackageInstaller dialogs without accidentally
     * tapping a "Install" button that happens to exist in the foreground app.
     *
     * <p>If the matched node itself is not clickable we walk up parents — Material
     * buttons often expose the click on the wrapper, not the inner TextView.</p>
     *
     * @return true if a tap fired, false if nothing matched or no clickable ancestor
     */
    fun clickByText(texts: List<String>, packageFilter: String? = null): Boolean {
        val root = rootInActiveWindow ?: run {
            Log.w(TAG, "clickByText: rootInActiveWindow null")
            return false
        }
        // First pass — Android's native findAccessibilityNodeInfosByText (fast, indexed).
        for (label in texts) {
            val matches = root.findAccessibilityNodeInfosByText(label) ?: continue
            for (node in matches) {
                if (packageFilter != null) {
                    val pkg = node.packageName?.toString()
                    if (pkg != null && pkg != packageFilter) continue
                }
                if (tryClick(node, "text='$label'")) return true
            }
        }
        // Second pass — full tree walk for content-description matches. ImageButtons in
        // the installer dialog often expose "Install" via contentDescription not via text,
        // and findAccessibilityNodeInfosByText doesn't see those.
        if (walkAndClick(root, texts, packageFilter)) return true
        Log.d(TAG, "clickByText: no match for $texts (pkgFilter=$packageFilter)")
        return false
    }

    /**
     * Recursive walker — checks every node's text + contentDescription against the label
     * list and clicks the first match (walking up to the nearest clickable ancestor).
     * Slower than {@code findAccessibilityNodeInfosByText} but catches non-text buttons.
     */
    private fun walkAndClick(
            node: AccessibilityNodeInfo?,
            texts: List<String>,
            packageFilter: String?,
            depth: Int = 0,
    ): Boolean {
        if (node == null || depth > 32) return false
        val pkg = node.packageName?.toString()
        val pkgOk = packageFilter == null || pkg == null || pkg == packageFilter
        if (pkgOk) {
            val nodeText = node.text?.toString()
            val nodeDesc = node.contentDescription?.toString()
            val match = texts.any { label ->
                (nodeText != null && nodeText.equals(label, ignoreCase = true)) ||
                (nodeDesc != null && nodeDesc.equals(label, ignoreCase = true))
            }
            if (match && tryClick(node, "walk text='$nodeText' desc='$nodeDesc'")) return true
        }
        for (i in 0 until node.childCount) {
            if (walkAndClick(node.getChild(i), texts, packageFilter, depth + 1)) return true
        }
        return false
    }

    /** Walk up from {@code node} to the closest clickable ancestor and click it. */
    private fun tryClick(start: AccessibilityNodeInfo, what: String): Boolean {
        var n: AccessibilityNodeInfo? = start
        while (n != null) {
            if (n.isClickable) {
                val ok = n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "click($what) on pkg=${n.packageName} → $ok")
                if (ok) return true
            }
            n = n.parent
        }
        return false
    }

    private fun dispatch(label: String, description: GestureDescription) {
        val accepted = dispatchGesture(description, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "$label completed")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "$label cancelled — gesture rejected (check coords / overlays / secure window)")
            }
        }, null)
        if (!accepted) {
            Log.w(TAG, "$label not accepted — dispatchGesture returned false")
        }
    }

    companion object {
        private const val TAG = "ControlA11y"

        @Volatile
        var instance: ControlAccessibilityService? = null
            private set
    }
}
