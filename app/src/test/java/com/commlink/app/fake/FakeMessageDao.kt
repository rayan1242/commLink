package com.commlink.app.fake

import com.commlink.app.data.local.dao.MessageDao
import com.commlink.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMessageDao : MessageDao {

    val insertedMessages = mutableListOf<MessageEntity>()
    private val messages = MutableStateFlow<List<MessageEntity>>(emptyList())

    override fun getMessages(channelId: String): Flow<List<MessageEntity>> =
        messages.map { list -> list.filter { it.channelId == channelId } }

    override suspend fun insertMessage(message: MessageEntity) {
        insertedMessages.add(message)
        messages.value = messages.value + message
    }

    override suspend fun pruneOldMessages() {}
}
