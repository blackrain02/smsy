package com.blackrain02.spamguard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SpamFeedbackDao {
    @Query("SELECT * FROM spam_feedback_bias WHERE token = :token")
    suspend fun getByToken(token: String): SpamFeedbackEntity?

    @Query("SELECT * FROM spam_feedback_bias WHERE token IN (:tokens)")
    suspend fun getByTokens(tokens: List<String>): List<SpamFeedbackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SpamFeedbackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SpamFeedbackEntity>)

    @Query("SELECT * FROM spam_feedback_bias")
    suspend fun getAll(): List<SpamFeedbackEntity>

    @Query("DELETE FROM spam_feedback_bias")
    suspend fun clearAll()
}
