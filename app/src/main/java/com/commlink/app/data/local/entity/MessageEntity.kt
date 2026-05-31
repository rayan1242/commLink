package com.commlink.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.commlink.app.domain.model.MessageType
import com.commlink.app.domain.model.PTTMessage

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "channel_id") val channelId: String,
    @ColumnInfo(name = "sender_id") val senderId: String,
    @ColumnInfo(name = "sender_name") val senderName: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long
)

fun MessageEntity.toDomain() = PTTMessage(
    id = id,
    channelId = channelId,
    senderId = senderId,
    senderName = senderName,
    content = content,
    type = MessageType.valueOf(type),
    timestamp = timestamp
)

fun PTTMessage.toEntity() = MessageEntity(
    id = id,
    channelId = channelId,
    senderId = senderId,
    senderName = senderName,
    content = content,
    type = type.name,
    timestamp = timestamp
)
