package com.devicefarm.agent.net

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Long-lived WebSocket to the device-bridge-service. Reconnects with exponential backoff.
 * Inbound frames are emitted via {@link incoming}; outbound goes through {@link send}.
 */
class AgentSocket(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {

    enum class State { DISCONNECTED, CONNECTING, CONNECTED }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // long-lived
        .build()

    private val socketRef = AtomicReference<WebSocket?>(null)
    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<Frame>(extraBufferCapacity = 64)
    val incoming: SharedFlow<Frame> = _incoming.asSharedFlow()

    private var supervisor: Job? = null
    private var configuredUrl: String? = null

    fun connect(wsUrl: String, agentToken: String) {
        val withToken = if (wsUrl.contains("?")) "$wsUrl&token=$agentToken" else "$wsUrl?token=$agentToken"
        configuredUrl = withToken
        supervisor?.cancel()
        supervisor = scope.launch {
            var backoff = 1_000L
            while (isJobActive()) {
                _state.value = State.CONNECTING
                val ws = openOnce(withToken)
                if (ws != null) {
                    backoff = 1_000L
                    awaitClose(ws)
                }
                _state.value = State.DISCONNECTED
                if (!isJobActive()) break
                Log.i(TAG, "reconnecting in ${backoff}ms")
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    fun disconnect() {
        supervisor?.cancel()
        supervisor = null
        socketRef.getAndSet(null)?.close(1000, "agent shutdown")
        _state.value = State.DISCONNECTED
    }

    fun send(frame: Frame): Boolean {
        val ws = socketRef.get() ?: return false
        return ws.send(frame.encode())
    }

    private fun isJobActive(): Boolean =
        supervisor?.let { !it.isCompleted && !it.isCancelled } ?: false

    private suspend fun openOnce(url: String): WebSocket? {
        val req = Request.Builder().url(url).build()
        val gate = kotlinx.coroutines.CompletableDeferred<WebSocket?>()
        val ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "ws open")
                socketRef.set(webSocket)
                _state.value = State.CONNECTED
                gate.complete(webSocket)
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try { _incoming.tryEmit(Frame.decode(bytes)) }
                catch (e: Exception) { Log.w(TAG, "decode err", e) }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "ws closing $code $reason")
                webSocket.close(code, reason)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socketRef.compareAndSet(webSocket, null)
                _state.value = State.DISCONNECTED
                if (!gate.isCompleted) gate.complete(null)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "ws failure ${t.message}")
                socketRef.compareAndSet(webSocket, null)
                _state.value = State.DISCONNECTED
                if (!gate.isCompleted) gate.complete(null)
            }
        })
        return gate.await().also { if (it == null) ws.cancel() }
    }

    private suspend fun awaitClose(ws: WebSocket) {
        state.first { it == State.DISCONNECTED }
    }

    companion object { private const val TAG = "AgentSocket" }
}
