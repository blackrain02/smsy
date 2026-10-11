package com.binacity.spamguard.data.model

data class Message(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val isSent: Boolean,
    val isRead: Boolean
)
