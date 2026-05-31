package com.commlink.app.domain.model

import java.util.UUID

data class PTTMessage(
    val id: String = UUID.randomUUID().toString(),
    val channelId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageType {
    TEXT,   // plain text message
    AUDIO,  // push-to-talk voice chunk
    INVITE, // SIP-style session start
    BYE     // SIP-style session end
}
