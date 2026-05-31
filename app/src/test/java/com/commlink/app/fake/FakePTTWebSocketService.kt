package com.commlink.app.fake

import com.commlink.app.data.websocket.PTTWebSocketService
import com.commlink.app.domain.model.NetworkState
import com.commlink.app.domain.model.PTTMessage

class FakePTTWebSocketService(
    fakeDao: FakeMessageDao = FakeMessageDao()
) : PTTWebSocketService(fakeDao) {

    val sentMessages = mutableListOf<PTTMessage>()
    val sentAudioChunks = mutableListOf<ByteArray>()

    override fun connect(channelId: String) {
        _networkState.value = NetworkState.Connected
    }

    override fun sendTextMessage(message: PTTMessage) {
        sentMessages.add(message)
    }

    override fun sendAudioChunk(audioBytes: ByteArray) {
        sentAudioChunks.add(audioBytes)
    }

    override fun disconnect() {
        _networkState.value = NetworkState.Disconnected
    }
}
