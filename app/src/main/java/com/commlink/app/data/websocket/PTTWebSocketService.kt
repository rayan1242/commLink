package com.commlink.app.data.websocket

import com.commlink.app.data.local.dao.MessageDao
import com.commlink.app.data.local.entity.toEntity
import com.commlink.app.domain.model.NetworkState
import com.commlink.app.domain.model.PTTMessage
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class PTTWebSocketService @Inject constructor(
    private val messageDao: MessageDao
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // no timeout — WebSocket stays open indefinitely
        .build()

    private var webSocket: WebSocket? = null

    // protected so FakePTTWebSocketService can emit state changes
    protected val _networkState = MutableStateFlow<NetworkState>(NetworkState.Disconnected)
    val networkState: StateFlow<NetworkState> = _networkState

    // SharedFlow — one-time events, not replayed to new collectors
    private val _incomingMessages = MutableSharedFlow<PTTMessage>()
    val incomingMessages: SharedFlow<PTTMessage> = _incomingMessages

    private val _incomingAudio = MutableSharedFlow<ByteArray>()
    val incomingAudio: SharedFlow<ByteArray> = _incomingAudio

    open fun connect(channelId: String) {
        val request = Request.Builder()
            .url("$WS_BASE_URL/$channelId")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _networkState.value = NetworkState.Connected
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = try {
                    Gson().fromJson(text, PTTMessage::class.java)
                } catch (e: Exception) {
                    null
                }
                if (message == null) return  // non-JSON frame (e.g. server echo/ack)
                CoroutineScope(Dispatchers.IO).launch {
                    messageDao.insertMessage(message.toEntity())
                    _incomingMessages.emit(message)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                CoroutineScope(Dispatchers.IO).launch {
                    _incomingAudio.emit(bytes.toByteArray())
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _networkState.value = NetworkState.Reconnecting(attempt = 1)
                reconnect(channelId)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _networkState.value = NetworkState.Disconnected
            }
        })
    }

    open fun sendTextMessage(message: PTTMessage) {
        val json = Gson().toJson(message)
        webSocket?.send(json)
    }

    open fun sendAudioChunk(audioBytes: ByteArray) {
        webSocket?.send(ByteString.of(*audioBytes))
    }

    open fun disconnect() {
        webSocket?.close(1000, "Session ended")
        webSocket = null
        _networkState.value = NetworkState.Disconnected
    }

    private fun reconnect(channelId: String, attempt: Int = 1) {
        if (attempt > MAX_RETRIES) {
            _networkState.value = NetworkState.Disconnected
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            delay(RECONNECT_DELAY_MS * attempt) // exponential backoff
            _networkState.value = NetworkState.Reconnecting(attempt)
            connect(channelId)
        }
    }

    companion object {
        // 192.168.0.54 = host LAN IP; use 10.0.2.2 for emulator
        const val WS_BASE_URL = "ws://192.168.0.54:8765"
        const val MAX_RETRIES = 5
        const val RECONNECT_DELAY_MS = 1000L
    }
}
