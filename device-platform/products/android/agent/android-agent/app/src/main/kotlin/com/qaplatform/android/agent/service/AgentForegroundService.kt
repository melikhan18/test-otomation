package com.qaplatform.android.agent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.qaplatform.android.agent.BuildConfig
import com.qaplatform.android.agent.R
import com.qaplatform.android.agent.capture.ScreenCaptureEngine
import com.qaplatform.android.agent.capture.ScreenshotEngine
import com.qaplatform.android.agent.control.ControlExecutor
import com.qaplatform.android.agent.inspect.InspectorEngine
import com.qaplatform.android.agent.install.ApkDownloader
import com.qaplatform.android.agent.install.ApkInstaller
import com.qaplatform.android.agent.install.AppInfoProbe
import com.qaplatform.android.agent.install.AppLauncher
import com.qaplatform.android.agent.install.DeviceResetService
import com.qaplatform.android.agent.install.InstallStatusReceiver
import com.qaplatform.android.agent.net.AgentSocket
import com.qaplatform.android.agent.net.Frame
import com.qaplatform.android.agent.net.FrameType
import com.qaplatform.android.agent.net.HeartbeatClient
import com.qaplatform.android.agent.store.AgentPreferences
import com.qaplatform.android.agent.ui.MainActivity
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.app.Activity
import android.util.Log

class AgentForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val agentSocket = AgentSocket(scope)
    private val heartbeats = HeartbeatClient()
    private lateinit var captureEngine: ScreenCaptureEngine

    private var heartbeatJob: Job? = null
    private var dispatcherJob: Job? = null
    private var wsHeartbeatJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        // İlk başta sadece dataSync — heartbeat + WS için yeter, MediaProjection consent'i
        // henüz alınmadığı için mediaProjection tipini şu an kullanırsak Android 14
        // SecurityException atar.
        startInForeground(buildNotification("Starting…"), dataSyncOnly = true)
        captureEngine = ScreenCaptureEngine(applicationContext, agentSocket)
        // Wire the PackageInstaller result broadcast bridge so the first APK install
        // doesn't have to pay the registration latency in its own request path.
        InstallStatusReceiver.ensureRegistered(applicationContext)
        startInboundDispatcher()
        startConnectionAndHeartbeat()
        observeSocketState()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val resultCode =
            intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                ?: Activity.RESULT_CANCELED

        val resultData =
            intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        // Already capturing — skip. Prevents START_STICKY from re-applying a stale consent
        // intent that the OS may redeliver after a crash (consent token is invalidated when
        // the original process dies, and reusing it throws SecurityException).
        if (captureEngine.isRunning) return START_STICKY

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            // Consent freshly granted — promote service type to mediaProjection, then start
            // capture. Wrapped in try/catch so a stale consent (process restart redelivery)
            // doesn't kill the service; we just stay in dataSync mode until the user
            // re-grants from the activity.
            try {
                startInForeground(buildNotification("Capturing screen…"), dataSyncOnly = false)
                captureEngine.start(resultCode, resultData)
            } catch (e: SecurityException) {
                Log.w(TAG, "MediaProjection consent invalid (likely redelivery after crash) — awaiting fresh consent", e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start screen capture", e)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { captureEngine.stop() }
        agentSocket.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    private fun startConnectionAndHeartbeat() {
        scope.launch {
            val prefs = AgentPreferences(applicationContext)
            prefs.state.collectLatest { s ->
                if (!s.enrolled) {
                    updateNotification("Not enrolled")
                    agentSocket.disconnect()
                } else {
                    agentSocket.connect(s.wsUrl, s.agentToken!!)
                    startHeartbeatLoop(s.backendUrl, s.deviceId!!, s.agentToken)
                }
            }
        }
    }

    private fun startHeartbeatLoop(backendUrl: String, deviceId: Long, agentToken: String) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                runCatching {
                    heartbeats.ping(backendUrl, deviceId, agentToken, BuildConfig.VERSION_NAME)
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun startInboundDispatcher() {
        dispatcherJob = scope.launch {
            agentSocket.incoming.collectLatest { frame ->
                handleFrame(frame)
            }
        }
    }

    private fun handleFrame(frame: Frame) {
        when (frame.type) {
            FrameType.CONTROL_COMMAND -> ControlExecutor.execute(frame.payloadAsString())
            FrameType.INSPECT_REQUEST -> {
                val rid = runCatching { JSONObject(frame.payloadAsString()).optString("requestId", null) }.getOrNull()
                val payload = InspectorEngine.dump(rid)
                agentSocket.send(Frame.ofJson(FrameType.INSPECT_RESPONSE, payload))
            }
            FrameType.FORCE_KEYFRAME -> {
                // captureEngine null check — engine onCreate'te init ediliyor ama henüz
                // start(resultCode,data) çağrılmadıysa codec yok; no-op olur.
                if (::captureEngine.isInitialized) captureEngine.requestKeyframe()
            }
            FrameType.SCREENSHOT_REQUEST -> {
                val rid = runCatching { JSONObject(frame.payloadAsString()).optString("requestId", null) }.getOrNull()
                // Result fires on the accessibility service's main executor; we just forward.
                ScreenshotEngine.capture(rid) { payload ->
                    agentSocket.send(Frame(FrameType.SCREENSHOT_RESPONSE, payload))
                }
            }
            FrameType.HEARTBEAT -> {
                agentSocket.send(Frame.empty(FrameType.HEARTBEAT))
            }
            FrameType.APP_INFO_REQUEST       -> handleAppInfoRequest(frame)
            FrameType.INSTALL_APK_REQUEST    -> handleInstallApkRequest(frame)
            FrameType.LAUNCH_APP_REQUEST     -> handleLaunchAppRequest(frame)
            FrameType.RESET_HOME_REQUEST     -> handleResetHomeRequest(frame)
            else -> { /* ignore */ }
        }
    }

    /* ─────────────────── Faz 3: app/install/launch/reset handlers ──────────── */

    private fun handleAppInfoRequest(frame: Frame) {
        runCatching {
            val obj = JSONObject(frame.payloadAsString())
            val rid = obj.getString("requestId")
            val pkg = obj.getString("packageName")
            val info = AppInfoProbe.probe(applicationContext, pkg)
            val resp = JSONObject().apply {
                put("requestId", rid)
                put("installed", info.installed)
                info.versionCode?.let { put("versionCode", it) }
                info.versionName?.let { put("versionName", it) }
            }
            agentSocket.send(Frame.ofJson(FrameType.APP_INFO_RESPONSE, resp.toString()))
        }.onFailure { Log.w(TAG, "APP_INFO_REQUEST failed", it) }
    }

    private fun handleInstallApkRequest(frame: Frame) {
        // Install can take minutes (large APK + download + commit). Run on a launched
        // coroutine so we don't block the inbound frame collector — other frames
        // (heartbeat, screenshot, control) keep flowing during the install.
        scope.launch {
            val payloadStr = frame.payloadAsString()
            val rid = runCatching { JSONObject(payloadStr).optString("requestId", "") }.getOrNull().orEmpty()

            fun respond(status: String, versionCode: Long? = null, errorCode: String? = null, errorMessage: String? = null) {
                val resp = JSONObject().apply {
                    put("requestId", rid)
                    put("status", status)
                    versionCode?.let { put("installedVersionCode", it) }
                    errorCode?.let { put("errorCode", it) }
                    errorMessage?.let { put("errorMessage", it) }
                }
                agentSocket.send(Frame.ofJson(FrameType.INSTALL_APK_RESPONSE, resp.toString()))
            }

            try {
                val obj = JSONObject(payloadStr)
                val downloadUrl = obj.getString("downloadUrl")
                val sha256 = obj.getString("sha256")
                val expectedVersionCode = obj.getLong("expectedVersionCode")
                val packageName = obj.getString("packageName")

                Log.i(TAG, "install pkg=$packageName from $downloadUrl (expected vc=$expectedVersionCode)")

                val downloadResult = ApkDownloader.download(applicationContext, downloadUrl, sha256)
                val apkFile = downloadResult.getOrElse {
                    Log.w(TAG, "download failed for $packageName", it)
                    respond("failed", errorCode = "DOWNLOAD_FAILED", errorMessage = it.message)
                    return@launch
                }

                val result = ApkInstaller.install(applicationContext, apkFile, packageName, expectedVersionCode)
                if (result.success) {
                    respond("ok", versionCode = result.installedVersionCode)
                } else {
                    respond("failed", errorCode = result.errorCode, errorMessage = result.errorMessage)
                }
            } catch (e: Exception) {
                Log.w(TAG, "INSTALL_APK_REQUEST failed", e)
                respond("failed", errorCode = "EXCEPTION", errorMessage = e.message)
            }
        }
    }

    private fun handleLaunchAppRequest(frame: Frame) {
        runCatching {
            val obj = JSONObject(frame.payloadAsString())
            val rid = obj.getString("requestId")
            val pkg = obj.getString("packageName")
            val outcome = AppLauncher.launch(applicationContext, pkg)
            val resp = JSONObject().apply {
                put("requestId", rid)
                put("status", if (outcome.success) "ok" else "failed")
                outcome.errorMessage?.let { put("errorMessage", it) }
            }
            agentSocket.send(Frame.ofJson(FrameType.LAUNCH_APP_RESPONSE, resp.toString()))
        }.onFailure { Log.w(TAG, "LAUNCH_APP_REQUEST failed", it) }
    }

    private fun handleResetHomeRequest(frame: Frame) {
        runCatching {
            val obj = JSONObject(frame.payloadAsString())
            val rid = obj.getString("requestId")
            val pkg = obj.optString("packageName").takeIf { it.isNotEmpty() }
            val kill = obj.optBoolean("killProcess", false)
            val outcome = DeviceResetService.reset(applicationContext, pkg, kill)
            val resp = JSONObject().apply {
                put("requestId", rid)
                put("status", if (outcome.success) "ok" else "failed")
                outcome.errorMessage?.let { put("errorMessage", it) }
            }
            agentSocket.send(Frame.ofJson(FrameType.RESET_HOME_RESPONSE, resp.toString()))
        }.onFailure { Log.w(TAG, "RESET_HOME_REQUEST failed", it) }
    }

    private fun observeSocketState() {
        scope.launch {
            agentSocket.state.collectLatest { s ->
                updateNotification(when (s) {
                    AgentSocket.State.CONNECTED    -> "Online · streaming ready"
                    AgentSocket.State.CONNECTING   -> "Connecting…"
                    AgentSocket.State.DISCONNECTED -> "Reconnecting…"
                })
                // Bridge.receive() data-frame timeout'u (~25s) için, video akmasa bile aktif tut.
                // OkHttp'nin WS ping/pong'u control frame; Reactor Netty bunu "message" saymaz.
                if (s == AgentSocket.State.CONNECTED) {
                    startWsHeartbeat()
                    // Re-prime the (possibly fresh) bridge channel: re-announce STREAM_METADATA
                    // and force a keyframe. After a bridge restart its in-memory cache is empty,
                    // so without this the web decoder is never configured and the live view stays
                    // black — even though the encoder is still happily producing DELTAs.
                    if (::captureEngine.isInitialized) captureEngine.reprimeForBridge()
                } else {
                    stopWsHeartbeat()
                }
            }
        }
    }

    private fun startWsHeartbeat() {
        wsHeartbeatJob?.cancel()
        wsHeartbeatJob = scope.launch {
            while (true) {
                delay(WS_HEARTBEAT_INTERVAL_MS)
                agentSocket.send(Frame.empty(FrameType.HEARTBEAT))
            }
        }
    }

    private fun stopWsHeartbeat() {
        wsHeartbeatJob?.cancel()
        wsHeartbeatJob = null
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pi = android.app.PendingIntent.getActivity(this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    /**
     * Android Q+ (API 29+) bir foreground service başlatırken type bayrağı bekler.
     * - {@code dataSyncOnly = true}: sadece DATA_SYNC — consent yokken güvenli.
     * - {@code dataSyncOnly = false}: DATA_SYNC + MEDIA_PROJECTION — consent geldikten sonra.
     *
     * Aynı service üzerinde startForeground'ı tekrar çağırmak mevcut tipi günceller; bu sayede
     * tek bir uzun ömürlü service'le iki aşamalı geçiş yapabiliyoruz.
     */
    private fun startInForeground(notification: Notification, dataSyncOnly: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (dataSyncOnly) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {

        private const val TAG = "AgentFgs"
        private const val CHANNEL_ID = "agent_status"
        private const val NOTIFICATION_ID = 1
        private const val HEARTBEAT_INTERVAL_MS = 10_000L     // HTTP heartbeat → device-service
        private const val WS_HEARTBEAT_INTERVAL_MS = 8_000L   // WS HEARTBEAT frame → bridge

        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        fun start(
            context: Context,
            resultCode: Int,
            data: Intent
        ) {

            val intent = Intent(context, AgentForegroundService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentForegroundService::class.java))
        }
    }
}
