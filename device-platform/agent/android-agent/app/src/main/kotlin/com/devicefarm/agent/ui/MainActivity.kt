package com.devicefarm.agent.ui

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.devicefarm.agent.BuildConfig
import com.devicefarm.agent.net.EnrollmentClient
import com.devicefarm.agent.service.AgentForegroundService
import com.devicefarm.agent.service.CaptureBridge
import com.devicefarm.agent.store.AgentPreferences
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AgentApp()
                }
            }
        }
    }
}

/**
 * Converts the user-entered backend URL into the bridge WebSocket URL.
 *   http://10.0.2.2:8080            → ws://10.0.2.2:8080/ws/agent      (emulator)
 *   http://192.168.1.50:8080        → ws://192.168.1.50:8080/ws/agent  (LAN/real device)
 *   https://farm.example.com        → wss://farm.example.com/ws/agent  (prod)
 */
private fun deriveWsUrl(backendUrl: String): String =
    backendUrl.trim().trimEnd('/').replaceFirst(Regex("^http"), "ws") + "/ws/agent"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentApp() {
    val ctx = LocalContext.current
    val prefs = remember { AgentPreferences(ctx) }
    val scope = rememberCoroutineScope()
    val state by prefs.state.collectAsState(initial = null)

    var backendUrl by remember { mutableStateOf("") }
    var enrollmentToken by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {

            AgentForegroundService.start(
                context = ctx,
                resultCode = result.resultCode,
                data = data
            )

            status = "screen capture granted"
        } else {
            status = "screen capture denied"
        }
    }

    LaunchedEffect(state) {
        state?.backendUrl?.takeIf { it.isNotBlank() }?.let { backendUrl = it }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Device Farm Agent") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Status", style = MaterialTheme.typography.titleMedium)
                    Text(when {
                        state == null -> "loading…"
                        state!!.enrolled -> "enrolled · device #${state!!.deviceId} (product ${state!!.productId})"
                        else -> "not enrolled"
                    })
                    Text("agent v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedTextField(
                value = backendUrl, onValueChange = { backendUrl = it },
                label = { Text("Backend URL (e.g. http://10.0.2.2:8080)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = enrollmentToken, onValueChange = { enrollmentToken = it },
                label = { Text("Enrollment Token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        scope.launch {
                            busy = true; status = "enrolling…"
                            val req = EnrollmentClient.EnrollRequest(
                                enrollmentToken = enrollmentToken.trim(),
                                serial = "${Build.MANUFACTURER}-${Build.MODEL}-${Build.ID}",
                                manufacturer = Build.MANUFACTURER ?: "unknown",
                                model = Build.MODEL ?: "unknown",
                                androidVersion = Build.VERSION.RELEASE ?: "unknown",
                                screenWidth = ctx.resources.displayMetrics.widthPixels,
                                screenHeight = ctx.resources.displayMetrics.heightPixels,
                                agentVersion = BuildConfig.VERSION_NAME,
                            )
                            val result = EnrollmentClient().enroll(backendUrl.trim(), req)
                            result.fold(
                                onSuccess = { resp ->
                                    prefs.saveEnrollment(backendUrl.trim(), deriveWsUrl(backendUrl), resp.agentToken, resp.deviceId, resp.productId)
                                    status = "enrolled — starting service"
                                    val pm =
                                        ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                                                as MediaProjectionManager

                                    projectionLauncher.launch(
                                        pm.createScreenCaptureIntent()
                                    )
                                },
                                onFailure = { e -> status = "error: ${e.message}" },
                            )
                            busy = false
                        }
                    },
                    enabled = !busy && backendUrl.isNotBlank() && enrollmentToken.isNotBlank(),
                ) { Text(if (state?.enrolled == true) "Re-enroll" else "Enroll & Start") }

                Button(
                    onClick = {
                        val pm =
                            ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                                    as MediaProjectionManager

                        projectionLauncher.launch(
                            pm.createScreenCaptureIntent()
                        )
                    },
                    enabled = state?.enrolled == true,
                ) { Text("Grant Screen Capture") }
            }

            TextButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }) { Text("Open Accessibility Settings (needed for control)") }

            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
