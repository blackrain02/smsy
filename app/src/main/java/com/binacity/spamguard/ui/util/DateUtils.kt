package com.binacity.spamguard.ui.util

import android.text.format.DateUtils as AndroidDateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val monthDayFormat = SimpleDateFormat("MMM d", Locale.ENGLISH)
    private val yearFormat = SimpleDateFormat("MM/dd/yy", Locale.ENGLISH)

    @Synchronized
    fun formatConversationDate(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val isToday = AndroidDateUtils.isToday(timestamp)
        val isYesterday = !isToday && diff < 2 * 86_400_000L && AndroidDateUtils.isToday(timestamp + 86_400_000L)

        val dateObj = Date(timestamp)
        return when {
            isToday -> timeFormat.format(dateObj)
            isYesterday -> "Yesterday"
            diff < 31536000000L -> monthDayFormat.format(dateObj)
            else -> yearFormat.format(dateObj)
        }
    }

    @Synchronized
    fun formatMessageTime(timestamp: Long): String {
        if (timestamp <= 0) return ""
        return timeFormat.format(Date(timestamp))
    }
}
