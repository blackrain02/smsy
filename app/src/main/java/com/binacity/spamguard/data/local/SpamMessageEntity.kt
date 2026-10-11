package com.binacity.spamguard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spam_messages")
data class SpamMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val spamProbability: Float,
    val isRead: Boolean = false
)
