package com.commlink.app.fake

import com.commlink.app.data.repository.PTTRepository
import com.commlink.app.domain.model.NetworkState
import com.commlink.app.domain.model.PTTMessage
import com.commlink.app.domain.model.PTTSessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.io.IOException

class FakePTTRepository : PTTRepository(
    messageDao = FakeMessageDao(),
    webSocketService = FakePTTWebSocketService(),
    audioManager = FakeAudioManager()
) {
    var shouldThrowError = false
    val sentMessages = mutableListOf<PTTMessage>()

    private val _fakeSessionState = MutableStateFlow<PTTSessionState>(PTTSessionState.Idle)
    override val sessionState: StateFlow<PTTSessionState> = _fakeSessionState

    private val _fakeNetworkState = MutableStateFlow<NetworkState>(NetworkState.Connected)
    override val networkState: StateFlow<NetworkState> = _fakeNetworkState

    private val _fakeIncomingAudio = MutableSharedFlow<ByteArray>()
    override val incomingAudio: SharedFlow<ByteArray> = _fakeIncomingAudio

    override fun getMessages(channelId: String): Flow<List<PTTMessage>> = emptyFlow()

    override suspend fun joinChannel(channelId: String) {
        if (shouldThrowError) throw IOException("Network failed")
        _fakeSessionState.value = PTTSessionState.Active(
            channelId = channelId,
            participants = listOf("officer_001", "officer_002")
        )
    }

    override suspend fun sendTextMessage(message: PTTMessage) {
        if (shouldThrowError) throw IOException("Network failed")
        sentMessages.add(message)
    }

    override suspend fun leaveChannel() {
        _fakeSessionState.value = PTTSessionState.Terminated
    }

    override fun startPTT() {}
    override fun stopPTT() {}
    override fun playReceivedAudio(audioBytes: ByteArray) {}
    override fun releaseAudio() {}
}
