package com.devicefarm.agent.install

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.devicefarm.agent.control.ControlAccessibilityService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Installs an APK via {@link PackageInstaller} and reports the outcome.
 *
 * <h2>Two install paths</h2>
 * <ul>
 *   <li><b>Device Owner / Profile Owner</b> — the system PackageInstaller dialog is
 *       skipped automatically; commit completes in {@code STATUS_SUCCESS} or a
 *       {@code STATUS_FAILURE_*} without any UI.</li>
 *   <li><b>Standard cihaz</b> — first commit returns {@code STATUS_PENDING_USER_ACTION}
 *       and the system dialog opens. We use the AccessibilityService to auto-tap
 *       "Install" / "Update" / "Kur" / "Güncelle" so a test farm device can install
 *       unattended. The follow-up status broadcast resolves the deferred.</li>
 * </ul>
 *
 * <p>Whatever mode the device is in, callers always see a single deferred-style
 * {@link InstallResult} — the dual paths only matter for whether the dialog flashes
 * briefly on the screen during the install.</p>
 */
object ApkInstaller {

    private const val TAG = "ApkInstaller"

    /**
     * Labels Android's PackageInstaller dialog uses across versions / locales / OEMs.
     * Order matters slightly — primary install verbs first so a generic "OK" never wins
     * over a real "Install" button on the same screen.
     */
    private val CONFIRM_LABELS = listOf(
            // English (stock AOSP, GooglePI)
            "Install", "INSTALL", "Update", "UPDATE",
            // Turkish (TR locale)
            "Yükle", "YÜKLE", "Kur", "KUR", "Güncelle", "GÜNCELLE",
            // Generic fall-back verbs — some OEM dialogs use these for the primary action
            "Continue", "CONTINUE", "Devam", "DEVAM",
            "OK", "Tamam", "TAMAM",
            // Done-style buttons for the post-install "App installed" screen so we can
            // dismiss it cleanly when the test orchestrator wants to launch right after.
            "Done", "DONE", "Bitti", "BİTTİ"
    )

    /** Packages we recognise as install confirmation surfaces. Empty → click anywhere
     *  (used when no match found within the standard installers — a few OEMs use bespoke
     *  package names we haven't enumerated yet). */
    private val INSTALLER_PACKAGES = listOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
    )

    suspend fun install(
            context: Context,
            apkFile: File,
            packageName: String,
            expectedVersionCode: Long,
    ): InstallResult = withContext(Dispatchers.IO) {

        InstallStatusReceiver.ensureRegistered(context)
        val deviceOwner = isDeviceOwner(context)
        Log.i(TAG, "begin install pkg=$packageName expectedVersionCode=$expectedVersionCode " +
                "deviceOwner=$deviceOwner apk=${apkFile.length()} bytes")

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setInstallReason(PackageManager.INSTALL_REASON_USER)
            }
        }

        val sessionId: Int
        try {
            sessionId = installer.createSession(params)
        } catch (e: Exception) {
            return@withContext InstallResult(false, errorCode = "CREATE_SESSION_FAILED",
                                             errorMessage = e.message)
        }

        val deferred = CompletableDeferred<InstallResult>()
        InstallStatusReceiver.watch(sessionId, deferred)

        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apkFile.length()).use { out ->
                    apkFile.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val confirmIntent = Intent(InstallStatusReceiver.ACTION).apply {
                    setPackage(context.packageName)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, confirmIntent, flags)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            Log.w(TAG, "session write/commit failed for $packageName", e)
            InstallStatusReceiver.cancel(sessionId)
            return@withContext InstallResult(false, errorCode = "SESSION_WRITE_FAILED",
                                             errorMessage = e.message)
        }

        val result = coroutineScope {
            val svc = ControlAccessibilityService.instance
            if (svc == null) {
                Log.w(TAG, "accessibility service not connected — install dialog will need a manual tap")
            }

            // ── Reactive path: react to PackageInstaller window events the instant they
            //    fire, rather than waiting for the next 1.5 s polling tick. The dialog
            //    publishes TYPE_WINDOW_STATE_CHANGED / TYPE_WINDOW_CONTENT_CHANGED with
            //    its package name; we only act on installer packages so we never tap
            //    something in the foreground test app. Wrapped in a try/finally so the
            //    listener is always unhooked (multiple installs would stack otherwise).
            val tapAttempt = java.util.concurrent.atomic.AtomicInteger(0)
            val eventTapper: (AccessibilityEvent) -> Unit = { event ->
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                    val pkg = event.packageName?.toString()
                    if (pkg != null && INSTALLER_PACKAGES.any { it == pkg }) {
                        val s = ControlAccessibilityService.instance
                        if (s != null) {
                            val ok = s.clickByText(CONFIRM_LABELS, packageFilter = pkg)
                            if (ok) Log.i(TAG, "auto-tapped installer dialog reactively (pkg=$pkg attempt=${tapAttempt.incrementAndGet()})")
                        }
                    }
                }
            }
            svc?.eventListener = eventTapper

            // ── Polling fallback: covers the case where the dialog was already on
            //    screen before we installed the listener, or events get coalesced.
            //    ~30 s window (20 × 1.5 s); large APK staged-writes can delay the dialog.
            val a11yJob = launch {
                repeat(20) { _ ->
                    delay(1500)
                    val s = ControlAccessibilityService.instance ?: return@repeat
                    val clicked =
                            INSTALLER_PACKAGES.any { pkg -> s.clickByText(CONFIRM_LABELS, packageFilter = pkg) } ||
                            s.clickByText(CONFIRM_LABELS, packageFilter = null)
                    if (clicked) Log.d(TAG, "auto-tapped installer dialog via polling")
                }
            }

            try {
                deferred.await()
            } finally {
                a11yJob.cancel()
                // Detach the listener even if cancel happened — leaving it would let a
                // future foreground installer event re-trigger taps after the install
                // is long done. Compare-and-clear keeps a parallel install (unlikely)
                // from clobbering each other's listener.
                if (svc?.eventListener === eventTapper) svc.eventListener = null
            }
        }

        // On success, query the installed versionCode for the result envelope.
        val withVersion = if (result.success) {
            val info = AppInfoProbe.probe(context, packageName)
            result.copy(installedVersionCode = info.versionCode)
        } else result

        // Clean up the staged APK regardless of outcome.
        if (!apkFile.delete()) apkFile.deleteOnExit()

        Log.i(TAG, "install pkg=$packageName done success=${withVersion.success} " +
                "installedVersionCode=${withVersion.installedVersionCode} " +
                "errorCode=${withVersion.errorCode}")
        withVersion
    }

    /**
     * @return true if this app is the active Device Owner / Profile Owner. On such cihazda
     *         install is fully silent; otherwise we depend on the AccessibilityService
     *         to confirm the system dialog.
     */
    fun isDeviceOwner(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                    ?: return false
            dpm.isDeviceOwnerApp(context.packageName) ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && dpm.isProfileOwnerApp(context.packageName))
        } catch (e: Exception) {
            Log.w(TAG, "isDeviceOwner check failed", e)
            false
        }
    }
}
