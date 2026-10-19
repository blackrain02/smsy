package com.blackrain02.spamguard.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Conversation(
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val snippet: String,
    val date: Long,
    val unreadCount: Int,
    val isPinned: Boolean = false,
    val mergedAddresses: Set<String> = emptySet(),
    val aliasId: String? = null,
    val subId: Int? = null,
    val isBlocked: Boolean = false,
    val isLocked: Boolean = false,
    val photoUri: String? = null
)
