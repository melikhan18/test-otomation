package com.devicefarm.agent.inspect

import android.content.res.Resources
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.devicefarm.agent.control.ControlAccessibilityService
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a JSON snapshot of the active window's accessibility node tree.
 * The format is consumed by the frontend Inspector panel and is the source of
 * the locator (resource-id / text / xpath) shown to the user.
 *
 * Requires the {@link ControlAccessibilityService} to be enabled in Settings.
 */
object InspectorEngine {

    fun dump(requestId: String?): String {
        val out = JSONObject()
        out.put("type", "inspectResult")
        if (requestId != null) out.put("requestId", requestId)
        out.put("timestamp", System.currentTimeMillis())

        val dm = Resources.getSystem().displayMetrics
        out.put("screenWidth", dm.widthPixels)
        out.put("screenHeight", dm.heightPixels)

        val svc = ControlAccessibilityService.instance
        if (svc == null) {
            out.put("error", "accessibility-service-not-enabled")
            return out.toString()
        }
        val root = svc.rootInActiveWindow
        if (root == null) {
            out.put("error", "no-active-window")
            return out.toString()
        }
        try {
            out.put("root", serialize(root))
        } catch (t: Throwable) {
            out.put("error", "serialize-failed: ${t.javaClass.simpleName}: ${t.message}")
        }
        return out.toString()
    }

    private fun serialize(node: AccessibilityNodeInfo, depth: Int = 0): JSONObject {
        val obj = JSONObject()
        obj.put("className", node.className?.toString() ?: "")
        obj.put("packageName", node.packageName?.toString() ?: "")
        obj.put("resourceId", node.viewIdResourceName)
        obj.put("text", node.text?.toString())
        obj.put("contentDescription", node.contentDescription?.toString())
        obj.put("clickable", node.isClickable)
        obj.put("longClickable", node.isLongClickable)
        obj.put("focusable", node.isFocusable)
        obj.put("focused", node.isFocused)
        obj.put("scrollable", node.isScrollable)
        obj.put("enabled", node.isEnabled)
        obj.put("checkable", node.isCheckable)
        obj.put("checked", node.isChecked)
        obj.put("selected", node.isSelected)
        obj.put("password", node.isPassword)

        val r = Rect()
        node.getBoundsInScreen(r)
        obj.put("bounds", JSONArray().apply { put(r.left); put(r.top); put(r.right); put(r.bottom) })

        val children = JSONArray()
        if (depth < MAX_DEPTH) {
            val n = node.childCount
            for (i in 0 until n) {
                val child = node.getChild(i) ?: continue
                try { children.put(serialize(child, depth + 1)) }
                finally { /* AccessibilityNodeInfo recycle is deprecated on modern Android — leave to system */ }
            }
        }
        obj.put("children", children)
        return obj
    }

    private const val MAX_DEPTH = 32
}
