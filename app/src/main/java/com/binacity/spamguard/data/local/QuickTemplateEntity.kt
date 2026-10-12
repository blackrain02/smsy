package com.binacity.spamguard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_templates")
data class QuickTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val isDefault: Boolean = false,
    val usageCount: Int = 0
)
