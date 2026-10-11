package com.binacity.spamguard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SpamMessageEntity::class, UserWhitelistEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spamMessageDao(): SpamMessageDao
    abstract fun userWhitelistDao(): UserWhitelistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spamguard_quarantine.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
