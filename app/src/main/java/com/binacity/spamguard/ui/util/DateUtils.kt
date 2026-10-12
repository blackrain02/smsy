package com.binacity.spamguard.ui.util

import android.content.Context
import android.text.format.DateUtils as AndroidDateUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private const val PREFS_NAME = "calendar_prefs"
    private const val KEY_USE_SOLAR = "use_solar_calendar"

    private val gregMonthDayFormat = SimpleDateFormat("MM/dd", Locale.ENGLISH)
    private val gregYearFormat = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)

    fun isSolarCalendar(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USE_SOLAR, true) // پیش‌فرض: تقویم شمسی
    }

    fun setSolarCalendar(context: Context, useSolar: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_USE_SOLAR, useSolar).apply()
    }

    data class JalaliDate(val year: Int, val month: Int, val day: Int)

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var gYear = gy
        var jy: Int
        if (gYear > 1600) {
            jy = 979
            gYear -= 1600
        } else {
            jy = 0
            gYear -= 621
        }
        val gy2 = if (gm > 2) gYear + 1 else gYear
        var days = 365 * gYear + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 - 80 + gd + gDaysInMonth[gm - 1]
        jy += 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + (days % 31)
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + ((days - 186) % 30)
        }
        return JalaliDate(jy, jm, jd)
    }

    @Synchronized
    fun formatConversationDate(timestamp: Long, isSolar: Boolean): String {
        if (timestamp <= 0) return ""
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val isToday = AndroidDateUtils.isToday(timestamp)
        val isYesterday = !isToday && diff < 2 * 86_400_000L && AndroidDateUtils.isToday(timestamp + 86_400_000L)

        val cal = Calendar.getInstance(Locale.ENGLISH).apply { timeInMillis = timestamp }
        val timeStr = String.format(Locale.ENGLISH, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))

        if (isToday) return timeStr
        if (isYesterday) return "Yesterday"

        return if (isSolar) {
            val jDate = gregorianToJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            val nowCal = Calendar.getInstance(Locale.ENGLISH).apply { timeInMillis = now }
            val nowJ = gregorianToJalali(
                nowCal.get(Calendar.YEAR),
                nowCal.get(Calendar.MONTH) + 1,
                nowCal.get(Calendar.DAY_OF_MONTH)
            )
            if (jDate.year == nowJ.year) {
                String.format(Locale.ENGLISH, "%02d/%02d", jDate.month, jDate.day)
            } else {
                String.format(Locale.ENGLISH, "%04d/%02d/%02d", jDate.year, jDate.month, jDate.day)
            }
        } else {
            val dateObj = Date(timestamp)
            if (diff < 31536000000L) gregMonthDayFormat.format(dateObj)
            else gregYearFormat.format(dateObj)
        }
    }

    @Synchronized
    fun formatMessageDetailTime(timestamp: Long, isSolar: Boolean): String {
        if (timestamp <= 0) return ""
        val cal = Calendar.getInstance(Locale.ENGLISH).apply { timeInMillis = timestamp }
        val timeStr = String.format(Locale.ENGLISH, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        val isToday = AndroidDateUtils.isToday(timestamp)
        if (isToday) return timeStr

        return if (isSolar) {
            val jDate = gregorianToJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            val nowCal = Calendar.getInstance(Locale.ENGLISH).apply { timeInMillis = System.currentTimeMillis() }
            val nowJ = gregorianToJalali(
                nowCal.get(Calendar.YEAR),
                nowCal.get(Calendar.MONTH) + 1,
                nowCal.get(Calendar.DAY_OF_MONTH)
            )
            if (jDate.year == nowJ.year) {
                String.format(Locale.ENGLISH, "%02d/%02d %s", jDate.month, jDate.day, timeStr)
            } else {
                String.format(Locale.ENGLISH, "%04d/%02d/%02d %s", jDate.year, jDate.month, jDate.day, timeStr)
            }
        } else {
            val isYesterday = AndroidDateUtils.isToday(timestamp + 86_400_000L)
            if (isYesterday) {
                "Yesterday $timeStr"
            } else {
                val dateObj = Date(timestamp)
                "${gregMonthDayFormat.format(dateObj)} $timeStr"
            }
        }
    }

    @Synchronized
    fun formatMessageTime(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val cal = Calendar.getInstance(Locale.ENGLISH).apply { timeInMillis = timestamp }
        return String.format(Locale.ENGLISH, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }
}
