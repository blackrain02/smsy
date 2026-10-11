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
    val isRead: Boolean
)
