package com.devicefarm.agent.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HeartbeatClient(private val http: OkHttpClient = EnrollmentClient.defaultClient) {

    suspend fun ping(backendUrl: String, deviceId: Long, agentToken: String, agentVersion: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = """{"agentVersion":"$agentVersion"}"""
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("${backendUrl.trimEnd('/')}/api/devices/$deviceId/heartbeat")
                    .header("Authorization", "Bearer $agentToken")
                    .post(body)
                    .build()
                http.newCall(request).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }
}
