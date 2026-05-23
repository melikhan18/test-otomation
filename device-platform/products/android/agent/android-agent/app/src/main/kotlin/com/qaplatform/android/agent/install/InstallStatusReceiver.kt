package com.qaplatform.android.agent.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Receives {@code PackageInstaller} commit-status broadcasts and routes each to the
 * {@link CompletableDeferred} the {@link ApkInstaller} suspended on. Concurrent installs
 * are demultiplexed by {@code sessionId}.
 *
 * <h2>Lifecycle (intentional — not a leak)</h2>
 * <p>Registered against {@code context.applicationContext} via {@link #ensureRegistered}
 * the first time anyone needs install broadcasts, and <strong>never unregistered</strong>.
 * This is deliberate:</p>
 * <ul>
 *   <li>Application context lives for the whole process. Receiver weight is a single
 *       BroadcastReceiver instance + a {@code ConcurrentHashMap} — bytes, not megabytes.</li>
 *   <li>{@code ensureRegistered} is idempotent (volatile flag + synchronized) so calling
 *       it multiple times across the process is safe; no duplicate registrations.</li>
 *   <li>The agent's process is long-lived (foreground service). If Android tears the
 *       process down for memory pressure, our receiver dies with it — no leak.</li>
 *   <li>Manifest declaration would also work, but a runtime receiver lets us use
 *       {@code RECEIVER_NOT_EXPORTED} cleanly across API levels without an extra
 *       manifest attribute.</li>
 * </ul>
 * <p>Static analyzers may flag this as "receiver not unregistered"; ignore the warning,
 * the lifecycle is correct.</p>
 */
object InstallStatusReceiver : BroadcastReceiver() {

    private const val TAG = "InstallStatusReceiver"
    const val ACTION = "com.qaplatform.android.agent.PACKAGE_INSTALL_STATUS"

    private val pending = ConcurrentHashMap<Int, CompletableDeferred<InstallResult>>()

    @Volatile private var registered = false

    fun ensureRegistered(context: Context) {
        if (registered) return
        synchronized(this) {
            if (registered) return
            val filter = IntentFilter(ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(this, filter)
            }
            registered = true
            Log.i(TAG, "registered (api=${Build.VERSION.SDK_INT})")
        }
    }

    /** Park {@code deferred} until the broadcast for {@code sessionId} arrives. */
    fun watch(sessionId: Int, deferred: CompletableDeferred<InstallResult>) {
        pending[sessionId] = deferred
    }

    /** Drop a watcher when the install is aborted before commit (e.g. write threw). */
    fun cancel(sessionId: Int) {
        pending.remove(sessionId)?.cancel()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.d(TAG, "broadcast session=$sessionId status=$status msg=$message")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // System install dialog needed — launch it. The AccessibilityService-side
                // auto-tap (kicked off by ApkInstaller) will press the "Install" / "Kur"
                // button to push past this without manual interaction on test farm devices.
                val confirmIntent = @Suppress("DEPRECATION")
                        intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirmIntent)
                    } catch (e: Exception) {
                        Log.w(TAG, "failed to start confirm intent", e)
                        pending.remove(sessionId)?.complete(
                                InstallResult(false, errorCode = "CONFIRM_INTENT_FAILED",
                                              errorMessage = e.message))
                    }
                } else {
                    pending.remove(sessionId)?.complete(
                            InstallResult(false, errorCode = "NO_CONFIRM_INTENT",
                                          errorMessage = "STATUS_PENDING_USER_ACTION without EXTRA_INTENT"))
                }
                // Note: deferred NOT completed here. We wait for the follow-up SUCCESS /
                // FAILURE broadcast that fires after the user (or the auto-tap helper)
                // confirms or cancels.
            }
            PackageInstaller.STATUS_SUCCESS -> {
                pending.remove(sessionId)?.complete(InstallResult(true))
            }
            else -> {
                pending.remove(sessionId)?.complete(
                        InstallResult(false, errorCode = statusName(status), errorMessage = message))
            }
        }
    }

    private fun statusName(code: Int): String = when (code) {
        PackageInstaller.STATUS_FAILURE              -> "FAILURE"
        PackageInstaller.STATUS_FAILURE_ABORTED      -> "FAILURE_ABORTED"
        PackageInstaller.STATUS_FAILURE_BLOCKED      -> "FAILURE_BLOCKED"
        PackageInstaller.STATUS_FAILURE_CONFLICT     -> "FAILURE_CONFLICT"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "FAILURE_INCOMPATIBLE"
        PackageInstaller.STATUS_FAILURE_INVALID      -> "FAILURE_INVALID"
        PackageInstaller.STATUS_FAILURE_STORAGE      -> "FAILURE_STORAGE"
        else                                          -> "UNKNOWN($code)"
    }
}

data class InstallResult(
        val success: Boolean,
        val installedVersionCode: Long? = null,
        val errorCode: String? = null,
        val errorMessage: String? = null,
)
