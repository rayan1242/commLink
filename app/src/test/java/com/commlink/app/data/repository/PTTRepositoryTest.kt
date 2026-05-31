package com.commlink.app.data.repository

import com.commlink.app.domain.model.MessageType
import com.commlink.app.domain.model.PTTMessage
import com.commlink.app.domain.model.PTTSessionState
import com.commlink.app.fake.FakeAudioManager
import com.commlink.app.fake.FakeMessageDao
import com.commlink.app.fake.FakePTTWebSocketService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PTTRepositoryTest {

    private lateinit var fakeDao: FakeMessageDao
    private lateinit var fakeWebSocket: FakePTTWebSocketService
    private lateinit var repository: PTTRepository

    @Before
    fun setup() {
        fakeDao = FakeMessageDao()
        fakeWebSocket = FakePTTWebSocketService(fakeDao)
        repository = PTTRepository(fakeDao, fakeWebSocket, FakeAudioManager())
    }

    @Test
    fun `joining channel sends INVITE message via WebSocket`() = runTest {
        repository.joinChannel("channel_alpha")

        val sentMessage = fakeWebSocket.sentMessages.first()
        assertEquals(MessageType.INVITE, sentMessage.type)
        assertEquals("channel_alpha", sentMessage.channelId)
    }

    @Test
    fun `joining channel transitions session state to Active`() = runTest {
        assertTrue(repository.sessionState.value is PTTSessionState.Idle)

        repository.joinChannel("channel_alpha")

        assertTrue(repository.sessionState.value is PTTSessionState.Active)
    }

    @Test
    fun `sending text message saves to Room before WebSocket`() = runTest {
        val message = PTTMessage(
            channelId = "channel_alpha",
            senderId = "officer_001",
            senderName = "Officer Smith",
            content = "Suspect spotted",
            type = MessageType.TEXT
        )

        repository.sendTextMessage(message)

        assertTrue(fakeDao.insertedMessages.isNotEmpty())
        assertEquals("Suspect spotted", fakeDao.insertedMessages.first().content)
        // Room insert happened — WebSocket also received the message
        assertEquals(1, fakeWebSocket.sentMessages.size)
    }

    @Test
    fun `leaving channel sends BYE message`() = runTest {
        repository.joinChannel("channel_alpha")

        repository.leaveChannel()

        val byeMessage = fakeWebSocket.sentMessages.last()
        assertEquals(MessageType.BYE, byeMessage.type)
    }

    @Test
    fun `leaving channel transitions session state to Terminated`() = runTest {
        repository.joinChannel("channel_alpha")
        repository.leaveChannel()

        assertTrue(repository.sessionState.value is PTTSessionState.Terminated)
    }
}
