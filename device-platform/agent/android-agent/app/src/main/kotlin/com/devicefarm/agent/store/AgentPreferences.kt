package com.devicefarm.agent.store

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "agent_prefs")

class AgentPreferences(private val context: Context) {

    private object Keys {
        val BACKEND_URL  = stringPreferencesKey("backend_url")
        val WS_URL       = stringPreferencesKey("ws_url")
        val AGENT_TOKEN  = stringPreferencesKey("agent_token")
        val DEVICE_ID    = longPreferencesKey("device_id")
        val PRODUCT_ID   = longPreferencesKey("product_id")
    }

    val state: Flow<AgentState> = context.dataStore.data.map(::toState)

    suspend fun current(): AgentState = toState(context.dataStore.data.first())

    suspend fun saveEnrollment(backendUrl: String, wsUrl: String, agentToken: String,
                               deviceId: Long, productId: Long) {
        context.dataStore.edit { p ->
            p[Keys.BACKEND_URL] = backendUrl
            p[Keys.WS_URL]      = wsUrl
            p[Keys.AGENT_TOKEN] = agentToken
            p[Keys.DEVICE_ID]   = deviceId
            p[Keys.PRODUCT_ID]  = productId
        }
    }

    suspend fun saveBackendUrl(backendUrl: String) {
        context.dataStore.edit { it[Keys.BACKEND_URL] = backendUrl }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private fun toState(p: Preferences) = AgentState(
        backendUrl = p[Keys.BACKEND_URL] ?: "",
        wsUrl      = p[Keys.WS_URL] ?: "",
        agentToken = p[Keys.AGENT_TOKEN],
        deviceId   = p[Keys.DEVICE_ID],
        productId  = p[Keys.PRODUCT_ID],
    )
}

data class AgentState(
    val backendUrl: String,
    val wsUrl: String,
    val agentToken: String?,
    val deviceId: Long?,
    val productId: Long?,
) {
    val enrolled: Boolean get() = !agentToken.isNullOrBlank() && deviceId != null
}
