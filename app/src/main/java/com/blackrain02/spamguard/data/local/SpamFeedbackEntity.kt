package com.blackrain02.spamguard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spam_feedback_bias")
data class SpamFeedbackEntity(
    @PrimaryKey val token: String,
    val spamCount: Int = 0,
    val hamCount: Int = 0,
    val biasScore: Float = 0.0f
)
