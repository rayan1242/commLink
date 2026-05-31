package com.commlink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.commlink.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    // Flow — reactive, UI updates automatically when new messages arrive
    @Query("SELECT * FROM messages WHERE channel_id = :channelId ORDER BY timestamp ASC")
    fun getMessages(channelId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    // Keep only the latest 100 messages per database
    @Query("DELETE FROM messages WHERE id NOT IN (SELECT id FROM messages ORDER BY timestamp DESC LIMIT 100)")
    suspend fun pruneOldMessages()
}
