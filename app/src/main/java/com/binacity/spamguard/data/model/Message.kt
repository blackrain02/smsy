package com.binacity.spamguard.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Message(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val isSent: Boolean,
    val isRead: Boolean,
    val subId: Int? = null,
    val deliveryStatus: Int = 0 // 0 = standard/received, 1 = sent, 2 = delivered, 3 = failed
)
