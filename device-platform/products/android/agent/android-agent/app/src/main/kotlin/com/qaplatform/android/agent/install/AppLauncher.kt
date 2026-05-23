package com.qaplatform.android.agent.install

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Brings the target app to the foreground via its launcher activity.
 *
 * <p>Uses {@code getLaunchIntentForPackage()} which respects whatever {@code MAIN +
 * LAUNCHER} entry the manifest declares — same activity the user would tap from the
 * launcher. Apps without a launcher activity (services-only, instant apps) return
 * a null intent and we report failure.</p>
 */
object AppLauncher {

    private const val TAG = "AppLauncher"

    data class Outcome(val success: Boolean, val errorMessage: String? = null)

    fun launch(context: Context, packageName: String): Outcome {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return Outcome(false, "no launcher activity for $packageName")
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        return try {
            context.startActivity(intent)
            Log.i(TAG, "launched $packageName")
            Outcome(true)
        } catch (e: Exception) {
            Log.w(TAG, "launch($packageName) failed", e)
            Outcome(false, e.message ?: e.javaClass.simpleName)
        }
    }
}
