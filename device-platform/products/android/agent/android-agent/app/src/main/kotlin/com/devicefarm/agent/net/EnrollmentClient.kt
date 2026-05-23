package com.devicefarm.agent.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class EnrollmentClient(private val http: OkHttpClient = defaultClient) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class EnrollRequest(
        val enrollmentToken: String,
        val serial: String,
        val manufacturer: String,
        val model: String,
        val androidVersion: String,
        val screenWidth: Int,
        val screenHeight: Int,
        val agentVersion: String,
    )

    @Serializable
    data class EnrollResponse(
        val deviceId: Long,
        val productId: Long,
        val agentToken: String,
        val wsUrl: String,
    )

    suspend fun enroll(backendUrl: String, req: EnrollRequest): Result<EnrollResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(EnrollRequest.serializer(), req)
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${backendUrl.trimEnd('/')}/api/agent/enroll")
                .post(body)
                .build()
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("enroll failed (${resp.code}): $text")
                json.decodeFromString(EnrollResponse.serializer(), text)
            }
        }
    }

    companion object {
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
