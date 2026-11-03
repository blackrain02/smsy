package com.blackrain02.spamguard.data.model

import androidx.compose.runtime.Immutable

fun isRtlSnippet(text: String): Boolean {
    val limit = minOf(text.length, 60)
    for (i in 0 until limit) {
        val c = text[i]
        if (c in '\u0600'..'\u06FF' || c in '\uFB50'..'\uFDFF' || c in '\uFE70'..'\uFEFF') return true
        if (c in 'A'..'Z' || c in 'a'..'z') return false
    }
    return false
}

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
    val photoUri: String? = null,
    val cleanSnippet: String = "",
    val isRtl: Boolean = false,
    val formattedDate: String = ""
)
