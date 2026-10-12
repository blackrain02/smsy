package com.blackrain02.spamguard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SpamMessageEntity::class,
        UserWhitelistEntity::class,
        BlocklistEntity::class,
        ScheduledMessageEntity::class,
        QuickTemplateEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spamMessageDao(): SpamMessageDao
    abstract fun userWhitelistDao(): UserWhitelistDao
    abstract fun blocklistDao(): BlocklistDao
    abstract fun scheduledMessageDao(): ScheduledMessageDao
    abstract fun quickTemplateDao(): QuickTemplateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spamguard_quarantine.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
