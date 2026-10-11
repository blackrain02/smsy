package com.binacity.spamguard.ui.util

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    fun formatConversationDate(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val now = Calendar.getInstance()
        val msgTime = Calendar.getInstance().apply { timeInMillis = timestamp }

        return when {
            now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR) -> {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
            }
            now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) - msgTime.get(Calendar.DAY_OF_YEAR) == 1 -> {
                "Yesterday"
            }
            now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) -> {
                SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date(timestamp))
            }
            else -> {
                SimpleDateFormat("MM/dd/yy", Locale.ENGLISH).format(Date(timestamp))
            }
        }
    }

    fun formatMessageTime(timestamp: Long): String {
        if (timestamp <= 0) return ""
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
