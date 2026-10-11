package com.binacity.spamguard.data.model

data class Conversation(
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val snippet: String,
    val date: Long,
    val unreadCount: Int,
    val isPinned: Boolean = false,
    val mergedAddresses: Set<String> = emptySet(),
    val aliasId: String? = null
)
