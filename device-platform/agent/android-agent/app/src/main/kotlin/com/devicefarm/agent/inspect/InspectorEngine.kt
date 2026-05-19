package com.devicefarm.agent.inspect

import android.content.res.Resources
import android.graphics.Rect
import android.os.Build
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

    private fun serialize(node: AccessibilityNodeInfo, depth: Int = 0, siblingIndex: Int = 0): JSONObject {
        val obj = JSONObject()
        obj.put("className", node.className?.toString() ?: "")
        obj.put("packageName", node.packageName?.toString() ?: "")
        obj.put("resourceId", node.viewIdResourceName)
        obj.put("text", node.text?.toString())
        obj.put("contentDescription", node.contentDescription?.toString())
        // Hint text (placeholder on EditText, e.g. "Phone number") — distinct locator dimension.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.toString()?.let { obj.put("hint", it) }
        }
        // EditText: input type (number/password/email/...) helps test code pick the right value.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val inp = node.inputType
            if (inp != 0) obj.put("inputType", inputTypeLabel(inp))
        }
        // Position in parent — useful for sibling-relative locators when no other selector is unique.
        obj.put("index", siblingIndex)

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
                try { children.put(serialize(child, depth + 1, i)) }
                finally { /* AccessibilityNodeInfo recycle is deprecated on modern Android — leave to system */ }
            }
        }
        obj.put("children", children)
        return obj
    }

    /** Human-readable label for {@link android.text.InputType} flags. */
    private fun inputTypeLabel(type: Int): String {
        // Lower 8 bits = class; upper bits = variations.
        val cls = type and 0x0F
        val variation = type and 0x0FF0
        val base = when (cls) {
            1 -> "text"           // TYPE_CLASS_TEXT
            2 -> "number"         // TYPE_CLASS_NUMBER
            3 -> "phone"          // TYPE_CLASS_PHONE
            4 -> "datetime"       // TYPE_CLASS_DATETIME
            else -> "other"
        }
        val suffix = when (variation) {
            0x0080 -> "-password"
            0x00E0 -> "-visible-password"
            0x0020 -> "-email"
            0x0030 -> "-email-subject"
            0x00C0 -> "-uri"
            else -> ""
        }
        return base + suffix
    }

    private const val MAX_DEPTH = 32
}
