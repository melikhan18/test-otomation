package com.qaplatform.android.agent.install

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Reads {@code PackageManager.getPackageInfo()} synchronously to report whether a target
 * package is installed and, if so, at what versionCode/versionName.
 *
 * <p>Cheap enough to call inline from the inbound frame dispatcher — no coroutine needed.</p>
 */
object AppInfoProbe {

    private const val TAG = "AppInfoProbe"

    data class Info(
            val installed: Boolean,
            val versionCode: Long? = null,
            val versionName: String? = null,
    )

    fun probe(context: Context, packageName: String): Info {
        return try {
            val pi = context.packageManager.getPackageInfo(packageName, 0)
            // longVersionCode is API 28+; minSdk is 28 so always safe.
            Info(installed = true, versionCode = pi.longVersionCode, versionName = pi.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(TAG, "$packageName not installed")
            Info(installed = false)
        } catch (e: Exception) {
            Log.w(TAG, "probe($packageName) failed", e)
            Info(installed = false)
        }
    }
}
