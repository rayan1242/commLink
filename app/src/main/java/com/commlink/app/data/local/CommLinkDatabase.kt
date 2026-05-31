package com.commlink.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.commlink.app.data.local.dao.MessageDao
import com.commlink.app.data.local.entity.MessageEntity

@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class CommLinkDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
