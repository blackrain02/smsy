package com.binacity.spamguard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpamMessageDao {
    @Query("SELECT * FROM spam_messages ORDER BY timestamp DESC")
    fun getAllSpam(): Flow<List<SpamMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: SpamMessageEntity): Long

    @Query("DELETE FROM spam_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM spam_messages")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM spam_messages WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>
}
