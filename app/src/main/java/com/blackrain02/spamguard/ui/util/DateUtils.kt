package com.blackrain02.spamguard.ui.util

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
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getBoolean(KEY_USE_SOLAR, true) // پیش‌فرض: تقویم شمسی
        } catch (e: Throwable) {
            true
        }
    }

    fun setSolarCalendar(context: Context, useSolar: Boolean) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_USE_SOLAR, useSolar).apply()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    data class JalaliDate(val year: Int, val month: Int, val day: Int)

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        return try {
            val safeGm = gm.coerceIn(1, 12)
            val safeGd = gd.coerceIn(1, 31)
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
            val gy2 = if (safeGm > 2) gYear + 1 else gYear
            var days = 365 * gYear + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 - 80 + safeGd + gDaysInMonth[safeGm - 1]
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
            JalaliDate(jy, jm.coerceIn(1, 12), jd.coerceIn(1, 31))
        } catch (e: Throwable) {
            JalaliDate(gy, gm.coerceIn(1, 12), gd.coerceIn(1, 31))
        }
    }

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        return try {
            val safeJm = jm.coerceIn(1, 12)
            val safeJd = jd.coerceIn(1, 31)
            val jY = jy - 979
            val jM = safeJm - 1
            val jD = safeJd - 1

            val jDayNo = 365 * jY + (jY / 33) * 8 + ((jY % 33 + 3) / 4) + (if (jM < 6) jM * 31 else (jM - 6) * 30 + 186) + jD
            var gDayNo = jDayNo + 79

            var gY = 1600 + 400 * (gDayNo / 146097)
            gDayNo %= 146097

            var leap = true
            if (gDayNo >= 36525) {
                gDayNo--
                gY += 100 * (gDayNo / 36524)
                gDayNo %= 36524

                if (gDayNo >= 365) {
                    gDayNo++
                } else {
                    leap = false
                }
            }

            gY += 4 * (gDayNo / 1461)
            gDayNo %= 1461

            if (gDayNo >= 366) {
                leap = false
                gDayNo--
                gY += gDayNo / 365
                gDayNo %= 365
            }

            val gDaysInMonth = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
            var gM = 1
            while (gM <= 12 && gDayNo >= gDaysInMonth[gM]) {
                gDayNo -= gDaysInMonth[gM]
                gM++
            }
            val gD = gDayNo + 1
            Triple(gY, gM, gD)
        } catch (e: Throwable) {
            Triple(jy, jm, jd)
        }
    }

    @Synchronized
    fun formatConversationDate(timestamp: Long, isSolar: Boolean): String {
        if (timestamp <= 0L) return ""
        return try {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            val isToday = AndroidDateUtils.isToday(timestamp)
            val isYesterday = !isToday && diff < 2 * 86_400_000L && AndroidDateUtils.isToday(timestamp + 86_400_000L)

            val cal = Calendar.getInstance(Locale.ENGLISH).apply { timeInMillis = timestamp }
            val timeStr = String.format(Locale.ENGLISH, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))

            if (isToday) return timeStr
            if (isYesterday) return "Yesterday"

            if (isSolar) {
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
        } catch (e: Throwable) {
            try {
                gregYearFormat.format(Date(timestamp))
            } catch (_: Throwable) {
                ""
            }
        }
    }

    @Synchronized
    fun formatMessageDetailTime(timestamp: Long, isSolar: Boolean): String {
        if (timestamp <= 0L) return ""
        return try {
            val cal = Calendar.getInstance(Locale.ENGLISH).apply { timeInMillis = timestamp }
            val timeStr = String.format(Locale.ENGLISH, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            val isToday = AndroidDateUtils.isToday(timestamp)
            if (isToday) return timeStr

            if (isSolar) {
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
        } catch (e: Throwable) {
            try {
                val cal = Calendar.getInstance(Locale.ENGLISH).apply { timeInMillis = timestamp }
                val timeStr = String.format(Locale.ENGLISH, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                "${gregYearFormat.format(Date(timestamp))} $timeStr"
            } catch (_: Throwable) {
                ""
            }
        }
    }

    @Synchronized
    fun formatMessageTime(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return try {
            val cal = Calendar.getInstance(Locale.ENGLISH).apply { timeInMillis = timestamp }
            String.format(Locale.ENGLISH, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        } catch (e: Throwable) {
            ""
        }
    }
}
