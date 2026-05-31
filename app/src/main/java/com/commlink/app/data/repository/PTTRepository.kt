package com.commlink.app.data.repository

import com.commlink.app.data.audio.PTTAudioManager
import com.commlink.app.data.local.dao.MessageDao
import com.commlink.app.data.local.entity.toDomain
import com.commlink.app.data.local.entity.toEntity
import com.commlink.app.data.websocket.PTTWebSocketService
import com.commlink.app.domain.model.MessageType
import com.commlink.app.domain.model.NetworkState
import com.commlink.app.domain.model.PTTMessage
import com.commlink.app.domain.model.PTTSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class PTTRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val webSocketService: PTTWebSocketService,
    private val audioManager: PTTAudioManager
) {
    private var currentChannelId = ""

    protected val _sessionState = MutableStateFlow<PTTSessionState>(PTTSessionState.Idle)
    open val sessionState: StateFlow<PTTSessionState> = _sessionState

    open val networkState: StateFlow<NetworkState> = webSocketService.networkState
    open val incomingMessages: SharedFlow<PTTMessage> = webSocketService.incomingMessages
    open val incomingAudio: SharedFlow<ByteArray> = webSocketService.incomingAudio

    open fun getMessages(channelId: String): Flow<List<PTTMessage>> =
        messageDao.getMessages(channelId).map { entities -> entities.map { it.toDomain() } }

    open suspend fun joinChannel(channelId: String) {
        currentChannelId = channelId
        _sessionState.value = PTTSessionState.Inviting(channelId)
        withContext(Dispatchers.IO) {
            try {
                webSocketService.connect(channelId)
                val inviteMessage = PTTMessage(
                    channelId = channelId,
                    senderId = OFFICER_ID,
                    senderName = OFFICER_NAME,
                    content = "INVITE",
                    type = MessageType.INVITE
                )
                webSocketService.sendTextMessage(inviteMessage)
                _sessionState.value = PTTSessionState.Active(
                    channelId = channelId,
                    participants = emptyList()
                )
            } catch (e: Exception) {
                _sessionState.value = PTTSessionState.Terminated
                throw e
            }
        }
    }

    // Saves to Room first, then sends via WebSocket — message persisted even if send fails
    open suspend fun sendTextMessage(message: PTTMessage) {
        withContext(Dispatchers.IO) {
            messageDao.insertMessage(message.toEntity())
            webSocketService.sendTextMessage(message)
        }
    }

    // Not suspend — fire and forget, no waiting for ACK
    open fun sendAudioChunk(audioBytes: ByteArray) {
        webSocketService.sendAudioChunk(audioBytes)
    }

    open suspend fun leaveChannel() {
        _sessionState.value = PTTSessionState.Terminating
        withContext(Dispatchers.IO) {
            val byeMessage = PTTMessage(
                channelId = currentChannelId,
                senderId = OFFICER_ID,
                senderName = OFFICER_NAME,
                content = "BYE",
                type = MessageType.BYE
            )
            webSocketService.sendTextMessage(byeMessage)
            webSocketService.disconnect()
            _sessionState.value = PTTSessionState.Terminated
        }
    }

    open fun startPTT() {
        audioManager.startRecording { audioChunk ->
            webSocketService.sendAudioChunk(audioChunk)
        }
    }

    open fun stopPTT() {
        audioManager.stopRecording()
    }

    open fun playReceivedAudio(audioBytes: ByteArray) {
        audioManager.playAudioChunk(audioBytes)
    }

    open fun releaseAudio() {
        audioManager.release()
    }

    companion object {
        const val OFFICER_ID = "officer_001"
        const val OFFICER_NAME = "Officer Smith"
    }
}
