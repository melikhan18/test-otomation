package com.devicefarm.agent.install

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.devicefarm.agent.control.ControlAccessibilityService

/**
 * Returns the device to a clean state between runs:
 *   1. Presses HOME via the AccessibilityService.
 *   2. Optionally force-stops the target package (only effective on Device Owner /
 *      managed devices — for unmanaged 3rd-party apps in the foreground,
 *      {@link ActivityManager#killBackgroundProcesses} is a no-op).
 *
 * <p>Designed to be called from the run-orchestrator's {@code finally} block: it must
 * never throw, returning an {@link Outcome} with the failure detail instead. A failed
 * reset records a warning on the run row but does not retroactively fail the run.</p>
 */
object DeviceResetService {

    private const val TAG = "DeviceReset"

    data class Outcome(val success: Boolean, val errorMessage: String? = null)

    fun reset(context: Context, packageName: String?, killProcess: Boolean): Outcome {
        val svc = ControlAccessibilityService.instance
                ?: return Outcome(false, "accessibility service not enabled")

        val homeOk = svc.globalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        if (killProcess && !packageName.isNullOrBlank()) {
            tryKill(context, packageName)
        }
        return if (homeOk) Outcome(true)
        else Outcome(false, "home gesture rejected by accessibility service")
    }

    /**
     * Best-effort cleanup. Standard cihazda {@code killBackgroundProcesses} only affects
     * cached/background processes; the active foreground app survives. On Device Owner
     * cihazda we could use {@code DevicePolicyManager.setApplicationHidden(true)} for a
     * harder stop — left as a future hardening step.
     */
    private fun tryKill(context: Context, packageName: String) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            am.killBackgroundProcesses(packageName)
            Log.d(TAG, "killBackgroundProcesses($packageName) issued (no-op for foreground apps without Device Owner)")
        } catch (e: Exception) {
            Log.w(TAG, "killBackgroundProcesses($packageName) failed", e)
        }
    }
}
