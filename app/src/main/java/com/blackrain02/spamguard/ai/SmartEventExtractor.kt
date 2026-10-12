package com.blackrain02.spamguard.ai

import com.blackrain02.spamguard.ui.util.DateUtils
import com.blackrain02.spamguard.ui.util.OtpHelper
import java.util.Calendar
import java.util.Locale

data class SmartEvent(
    val title: String,
    val dateEpochMillis: Long?,
    val dateFormatted: String?,
    val description: String,
    val category: String
)

/**
 * سیستم استخراج خودکار رویدادها، سررسیدها و یادآوری‌ها از پیامک (On-Device Event Extractor)
 */
object SmartEventExtractor {

    private val PERSIAN_MONTHS = mapOf(
        "فروردین" to 1, "اردیبهشت" to 2, "خرداد" to 3,
        "تیر" to 4, "مرداد" to 5, "شهریور" to 6,
        "مهر" to 7, "آبان" to 8, "آذر" to 9,
        "دی" to 10, "بهمن" to 11, "اسفند" to 12
    )

    fun extractEvent(body: String?): SmartEvent? {
        if (body.isNullOrBlank()) return null
        val latinText = OtpHelper.toLatinDigits(body)
        val lower = latinText.lowercase()

        val category: String
        val title: String

        when {
            lower.contains("سررسید چک") || lower.contains("چک صیادی") || lower.contains("چک به شماره") -> {
                category = "CHECK_DUE"
                title = "سررسید چک صیادی"
            }
            lower.contains("مهلت پرداخت") || lower.contains("شناسه قبض") || lower.contains("قبض برق") ||
                    lower.contains("قبض گاز") || lower.contains("قبض آب") || lower.contains("قبض تلفن") -> {
                category = "BILL_DEADLINE"
                title = "مهلت پرداخت قبض"
            }
            lower.contains("نوبت") || lower.contains("وقت دکتر") || lower.contains("کلینیک") ||
                    lower.contains("آزمایشگاه") || lower.contains("پزشک") || lower.contains("رزرو نوبت") -> {
                category = "APPOINTMENT"
                title = "نوبت پزشکی / رزرو"
            }
            lower.contains("مرسوله") || lower.contains("پست پیشتاز") || lower.contains("کد رهگیری") ||
                    lower.contains("تیپاکس") || lower.contains("تحویل بسته") -> {
                category = "DELIVERY"
                title = "تحویل مرسوله پستی"
            }
            lower.contains("جلسه") || lower.contains("جلسه کاری") || lower.contains("کلاس") || lower.contains("وبینار") -> {
                category = "MEETING"
                title = "جلسه و رویداد"
            }
            lower.contains("پرواز") || lower.contains("بلیط") || lower.contains("قطار") -> {
                category = "TRAVEL"
                title = "زمان حرکت / بلیط"
            }
            else -> return null
        }

        val (epochMillis, dateStr) = parseDateTime(latinText)
        if (epochMillis == null && dateStr == null) {
            // اگر حتی تاریخ دقیق نباشد ولی کلمات صریح رویداد باشد با زمان پیش‌فرض ۲۴ ساعت آینده
            return SmartEvent(
                title = title,
                dateEpochMillis = System.currentTimeMillis() + 86_400_000L,
                dateFormatted = "به‌زودی",
                description = body.trim(),
                category = category
            )
        }

        return SmartEvent(
            title = title,
            dateEpochMillis = epochMillis,
            dateFormatted = dateStr,
            description = body.trim(),
            category = category
        )
    }

    private fun parseDateTime(text: String): Pair<Long?, String?> {
        val (hour, minute) = parseTime(text)

        // ۱. فرمت شمسی عددی مثل 1403/07/28 یا 1403-08-15
        val jalaliNumericRegex = Regex("""\b(140[0-9])[/-](0?[1-9]|1[0-2])[/-](0?[1-9]|[12][0-9]|3[01])\b""")
        val jalaliMatch = jalaliNumericRegex.find(text)
        if (jalaliMatch != null) {
            val y = jalaliMatch.groupValues[1].toInt()
            val m = jalaliMatch.groupValues[2].toInt()
            val d = jalaliMatch.groupValues[3].toInt()

            val (gy, gm, gd) = DateUtils.jalaliToGregorian(y, m, d)
            val cal = Calendar.getInstance(Locale.ENGLISH).apply {
                set(Calendar.YEAR, gy)
                set(Calendar.MONTH, gm - 1)
                set(Calendar.DAY_OF_MONTH, gd)
                set(Calendar.HOUR_OF_DAY, hour ?: 9)
                set(Calendar.MINUTE, minute ?: 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val formatted = String.format(Locale.ENGLISH, "%04d/%02d/%02d", y, m, d) +
                    if (hour != null) String.format(Locale.ENGLISH, " ساعت %02d:%02d", hour, minute ?: 0) else ""
            return cal.timeInMillis to formatted
        }

        // ۲. فرمت شمسی با حروف ماه مثل: 25 مهر یا 10 آبان
        for ((monthName, monthNum) in PERSIAN_MONTHS) {
            val namedMonthRegex = Regex("""\b(0?[1-9]|[12][0-9]|3[01])\s+""" + monthName)
            val namedMatch = namedMonthRegex.find(text)
            if (namedMatch != null) {
                val d = namedMatch.groupValues[1].toInt()
                val currentJalali = DateUtils.gregorianToJalali(
                    Calendar.getInstance().get(Calendar.YEAR),
                    Calendar.getInstance().get(Calendar.MONTH) + 1,
                    Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                )
                val (gy, gm, gd) = DateUtils.jalaliToGregorian(currentJalali.year, monthNum, d)
                val cal = Calendar.getInstance(Locale.ENGLISH).apply {
                    set(Calendar.YEAR, gy)
                    set(Calendar.MONTH, gm - 1)
                    set(Calendar.DAY_OF_MONTH, gd)
                    set(Calendar.HOUR_OF_DAY, hour ?: 9)
                    set(Calendar.MINUTE, minute ?: 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val formatted = "$d $monthName" +
                        if (hour != null) String.format(Locale.ENGLISH, " ساعت %02d:%02d", hour, minute ?: 0) else ""
                return cal.timeInMillis to formatted
            }
        }

        // ۳. فرمت میلادی عددی مثل 2026/10/15
        val gregNumericRegex = Regex("""\b(202[0-9])[/-](0?[1-9]|1[0-2])[/-](0?[1-9]|[12][0-9]|3[01])\b""")
        val gregMatch = gregNumericRegex.find(text)
        if (gregMatch != null) {
            val y = gregMatch.groupValues[1].toInt()
            val m = gregMatch.groupValues[2].toInt()
            val d = gregMatch.groupValues[3].toInt()

            val cal = Calendar.getInstance(Locale.ENGLISH).apply {
                set(Calendar.YEAR, y)
                set(Calendar.MONTH, m - 1)
                set(Calendar.DAY_OF_MONTH, d)
                set(Calendar.HOUR_OF_DAY, hour ?: 9)
                set(Calendar.MINUTE, minute ?: 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val formatted = String.format(Locale.ENGLISH, "%04d/%02d/%02d", y, m, d) +
                    if (hour != null) String.format(Locale.ENGLISH, " %02d:%02d", hour, minute ?: 0) else ""
            return cal.timeInMillis to formatted
        }

        // ۴. واژگان نسبی مثل "فردا"
        if (text.contains("فردا")) {
            val cal = Calendar.getInstance(Locale.ENGLISH).apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, hour ?: 10)
                set(Calendar.MINUTE, minute ?: 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val formatted = "فردا" + if (hour != null) String.format(Locale.ENGLISH, " ساعت %02d:%02d", hour, minute ?: 0) else ""
            return cal.timeInMillis to formatted
        }

        return null to null
    }

    private fun parseTime(text: String): Pair<Int?, Int?> {
        val timeRegex = Regex("""(?i)(?:ساعت|زمان|time|at)[\s:]*([01]?[0-9]|2[0-3])(?::([0-5][0-9]))?""")
        val match = timeRegex.find(text)
        if (match != null) {
            val h = match.groupValues[1].toIntOrNull()
            val m = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            return h to m
        }
        return null to null
    }
}
