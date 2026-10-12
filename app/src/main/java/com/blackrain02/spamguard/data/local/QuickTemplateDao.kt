package com.blackrain02.spamguard.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickTemplateDao {
    @Query("SELECT * FROM quick_templates ORDER BY usageCount DESC, id ASC")
    fun getAllTemplates(): Flow<List<QuickTemplateEntity>>

    @Query("SELECT * FROM quick_templates ORDER BY usageCount DESC, id ASC")
    suspend fun getAllTemplatesList(): List<QuickTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: QuickTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<QuickTemplateEntity>)

    @Query("UPDATE quick_templates SET usageCount = usageCount + 1 WHERE id = :id")
    suspend fun incrementUsage(id: Long)

    @Query("DELETE FROM quick_templates WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM quick_templates")
    suspend fun count(): Int
}
