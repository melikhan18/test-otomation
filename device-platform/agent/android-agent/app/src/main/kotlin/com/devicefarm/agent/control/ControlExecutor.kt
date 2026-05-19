package com.devicefarm.agent.control

import android.accessibilityservice.AccessibilityService
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Translates a JSON control command into an AccessibilityService call.
 *
 * Coordinates arrive in **physical screen pixel space** — the web client uses the
 * {@code realWidth}/{@code realHeight} from {@code STREAM_METADATA} to compute exact
 * device coordinates from the user's click, so the agent doesn't scale anything.
 *
 * Expected JSON shapes:
 *   {"type":"tap","x":540,"y":1200,"durationMs":80}
 *   {"type":"swipe","startX":100,"startY":1500,"endX":100,"endY":500,"durationMs":300}
 *   {"type":"key","keyCode":4}             // BACK
 *   {"type":"text","value":"hello"}
 */
object ControlExecutor {

    private val json = Json { ignoreUnknownKeys = true }

    fun execute(payload: String): Boolean {
        val svc = ControlAccessibilityService.instance ?: run {
            Log.w(TAG, "accessibility service not enabled — skipping $payload")
            return false
        }
        return try {
            val obj = json.parseToJsonElement(payload).jsonObject
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "tap"   -> doTap(svc, obj)
                "swipe" -> doSwipe(svc, obj)
                "key"   -> doKey(svc, obj)
                "text"  -> doText(svc, obj)
                else    -> { Log.w(TAG, "unknown command: $payload"); false }
            }
        } catch (e: Exception) {
            Log.w(TAG, "bad control payload: $payload", e); false
        }
    }

    private fun doTap(svc: ControlAccessibilityService, o: JsonObject): Boolean {
        val x = o["x"]?.jsonPrimitive?.floatOrNull ?: return false
        val y = o["y"]?.jsonPrimitive?.floatOrNull ?: return false
        val dur = o["durationMs"]?.jsonPrimitive?.longOrNull ?: 80L
        svc.tap(x, y, dur); return true
    }

    private fun doSwipe(svc: ControlAccessibilityService, o: JsonObject): Boolean {
        val sx = o["startX"]?.jsonPrimitive?.floatOrNull ?: return false
        val sy = o["startY"]?.jsonPrimitive?.floatOrNull ?: return false
        val ex = o["endX"]?.jsonPrimitive?.floatOrNull ?: return false
        val ey = o["endY"]?.jsonPrimitive?.floatOrNull ?: return false
        val dur = o["durationMs"]?.jsonPrimitive?.longOrNull ?: 300L
        svc.swipe(sx, sy, ex, ey, dur); return true
    }

    private fun doKey(svc: ControlAccessibilityService, o: JsonObject): Boolean {
        val code = o["keyCode"]?.jsonPrimitive?.intOrNull ?: return false
        val action = when (code) {
            4   -> AccessibilityService.GLOBAL_ACTION_BACK
            3   -> AccessibilityService.GLOBAL_ACTION_HOME
            187 -> AccessibilityService.GLOBAL_ACTION_RECENTS
            else -> return false
        }
        return svc.globalAction(action)
    }

    private fun doText(svc: ControlAccessibilityService, o: JsonObject): Boolean {
        val v = o["value"]?.jsonPrimitive?.contentOrNull ?: return false
        return svc.typeText(v)
    }

    private const val TAG = "ControlExecutor"
}
