package com.binacity.spamguard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserWhitelistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: UserWhitelistEntity)

    @Query("SELECT COUNT(*) FROM user_whitelist WHERE LOWER(sender) = LOWER(:sender)")
    suspend fun isWhitelisted(sender: String): Int

    @Query("SELECT sender FROM user_whitelist")
    suspend fun getAllWhitelistedSenders(): List<String>
}
