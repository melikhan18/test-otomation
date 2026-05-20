package com.devicefarm.agent.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import com.devicefarm.agent.control.ControlAccessibilityService
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * One-shot PNG screenshot via {@link AccessibilityService#takeScreenshot}. We piggy-back
 * on the already-required accessibility service so we don't need yet another MediaProjection
 * consent dance just for a still image.
 *
 * Response payload layout (sent as the body of a SCREENSHOT_RESPONSE frame):
 * <pre>
 *   [4 bytes BE: metaLen][metaLen bytes: UTF-8 JSON metadata][PNG bytes]
 * </pre>
 * Metadata on success: {@code {"requestId":"...","width":W,"height":H}}<br>
 * Metadata on failure: {@code {"requestId":"...","error":"reason"}} (no PNG bytes follow)
 */
object ScreenshotEngine {

    private const val TAG = "ScreenshotEngine"

    /** Result is the fully-built SCREENSHOT_RESPONSE payload. Caller wraps it in a Frame and sends. */
    fun capture(requestId: String?, onResult: (ByteArray) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(errorPayload(requestId, "screenshot-requires-android-11"))
            return
        }
        val svc = ControlAccessibilityService.instance
        if (svc == null) {
            onResult(errorPayload(requestId, "accessibility-service-not-enabled"))
            return
        }
        try {
            svc.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                svc.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        try { onResult(encodeSuccess(requestId, result)) }
                        catch (e: Exception) {
                            Log.e(TAG, "encode failed", e)
                            onResult(errorPayload(requestId, "encode-failed: ${e.message}"))
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "takeScreenshot failed code=$errorCode")
                        onResult(errorPayload(requestId, "take-screenshot-failed-$errorCode"))
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "takeScreenshot threw", e)
            onResult(errorPayload(requestId, "take-screenshot-threw: ${e.message}"))
        }
    }

    private fun encodeSuccess(requestId: String?, result: AccessibilityService.ScreenshotResult): ByteArray {
        // Hardware buffer is GPU-backed; we copy into a software Bitmap so PNG.compress works.
        val hw = result.hardwareBuffer
        val gpuBmp = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
        if (gpuBmp == null) { hw.close(); throw IllegalStateException("wrapHardwareBuffer returned null") }

        val swBmp: Bitmap
        try {
            swBmp = gpuBmp.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw IllegalStateException("bitmap copy returned null")
        } finally {
            gpuBmp.recycle()
            hw.close()
        }

        val width = swBmp.width
        val height = swBmp.height
        val baos = ByteArrayOutputStream(256 * 1024)
        try {
            swBmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        } finally { swBmp.recycle() }
        val pngBytes = baos.toByteArray()

        val meta = JSONObject().apply {
            if (requestId != null) put("requestId", requestId)
            put("width", width)
            put("height", height)
            put("format", "png")
            put("byteSize", pngBytes.size)
        }
        return packPayload(meta, pngBytes)
    }

    private fun errorPayload(requestId: String?, reason: String): ByteArray {
        val meta = JSONObject().apply {
            if (requestId != null) put("requestId", requestId)
            put("error", reason)
        }
        return packPayload(meta, ByteArray(0))
    }

    /** [4-byte BE metaLen][meta UTF-8][png bytes] */
    private fun packPayload(meta: JSONObject, pngBytes: ByteArray): ByteArray {
        val metaBytes = meta.toString().toByteArray(Charsets.UTF_8)
        val out = ByteArray(4 + metaBytes.size + pngBytes.size)
        out[0] = ((metaBytes.size ushr 24) and 0xFF).toByte()
        out[1] = ((metaBytes.size ushr 16) and 0xFF).toByte()
        out[2] = ((metaBytes.size ushr 8)  and 0xFF).toByte()
        out[3] = (metaBytes.size           and 0xFF).toByte()
        System.arraycopy(metaBytes, 0, out, 4, metaBytes.size)
        if (pngBytes.isNotEmpty()) {
            System.arraycopy(pngBytes, 0, out, 4 + metaBytes.size, pngBytes.size)
        }
        return out
    }
}
