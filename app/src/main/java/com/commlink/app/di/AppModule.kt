package com.commlink.app.di

import android.content.Context
import androidx.room.Room
import com.commlink.app.data.local.CommLinkDatabase
import com.commlink.app.data.local.dao.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CommLinkDatabase {
        return Room.databaseBuilder(
            context,
            CommLinkDatabase::class.java,
            "commlink_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: CommLinkDatabase): MessageDao {
        return database.messageDao()
    }
}
