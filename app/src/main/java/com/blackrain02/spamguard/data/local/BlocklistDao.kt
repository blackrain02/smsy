package com.blackrain02.spamguard.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BlocklistDao {
    @Query("SELECT * FROM blocklist ORDER BY blockedAt DESC")
    fun getAllBlocked(): Flow<List<BlocklistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocklist WHERE sender = :sender)")
    suspend fun isBlocked(sender: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockSender(item: BlocklistEntity)

    @Query("DELETE FROM blocklist WHERE sender = :sender")
    suspend fun unblockSender(sender: String)

    @Query("SELECT * FROM blocklist")
    suspend fun getAllBlockedList(): List<BlocklistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BlocklistEntity>)
}
