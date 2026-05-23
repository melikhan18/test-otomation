package com.qaplatform.android.agent.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.qaplatform.android.agent.store.AgentPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires after the device finishes booting. If the agent was enrolled before
 * shutdown, we kick the foreground service back up so it can reach the
 * backend over WebSocket and emit heartbeats — the device shows as online in
 * the web console even though the user hasn't opened the app yet.
 *
 * Note: MediaProjection consent is process-bound and cannot be obtained from
 * a BroadcastReceiver, so live screen mirroring stays paused until the user
 * launches the app and approves capture. Heartbeat-only mode is enough to
 * make the device discoverable.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = AgentPreferences(app)
            val s = prefs.current()
            if (s.enrolled) {
                // Heartbeat-only mode: pass RESULT_CANCELED + null data so the
                // service starts in DATA_SYNC type without trying to use a
                // (stale) MediaProjection consent token. Capture resumes when
                // the user next opens the app.
                AgentForegroundService.start(app, Activity.RESULT_CANCELED, Intent())
                Log.i(TAG, "boot complete — agent service restarted (heartbeat-only)")
            } else {
                Log.i(TAG, "boot complete — agent not enrolled, nothing to do")
            }
        }
    }

    companion object { private const val TAG = "AgentBoot" }
}
