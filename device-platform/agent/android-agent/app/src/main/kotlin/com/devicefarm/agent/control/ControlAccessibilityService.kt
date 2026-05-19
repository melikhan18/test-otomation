package com.devicefarm.agent.control

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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not used yet */ }
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
