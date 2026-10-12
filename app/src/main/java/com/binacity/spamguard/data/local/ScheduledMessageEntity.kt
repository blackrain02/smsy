package com.binacity.spamguard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_messages")
data class ScheduledMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val address: String,
    val body: String,
    val scheduledTime: Long,
    val subId: Int? = null,
    val status: String = "PENDING" // PENDING, SENT, FAILED, CANCELLED
)
