package com.blackrain02.spamguard.ui.util

object OtpHelper {

    private val KEYWORDS = listOf(
        "رمز", "کد", "تایید", "پویا", "ورود", "فعالسازی", "اعتبارسنجی",
        "otp", "code", "pin", "verify", "verification", "password", "passcode"
    )

    /**
     * تبدیل ارقام فارسی و عربی به ارقام استاندارد لاتین
     */
    fun convertDigitsToEnglish(input: String): String {
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        var result = input
        for (i in 0..9) {
            result = result.replace(persianDigits[i], ('0' + i))
            result = result.replace(arabicDigits[i], ('0' + i))
        }
        return result
    }

    fun toLatinDigits(input: String): String = convertDigitsToEnglish(input)

    /**
     * آیا این پیامک حاوی کد تایید / رمز یک‌بارمصرف است؟
     */
    fun isOtpMessage(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        val hasKeyword = KEYWORDS.any { lower.contains(it) }
        if (!hasKeyword) return false

        val normalized = convertDigitsToEnglish(text)
        // بررسی وجود کد عددی ۴ تا ۸ رقمی
        val digitMatch = Regex("""\b(?<!\d)(\d{4,8})(?!\d)\b""").find(normalized)
        return digitMatch != null
    }

    /**
     * استخراج کد تایید / رمز پویا از متن پیامک
     * اولویت با اعدادی است که بلافاصله بعد از کلمات کلیدی کد/رمز آمده‌اند
     */
    fun extractOtp(text: String): String? {
        if (text.isBlank()) return false.let { null }
        val lower = text.lowercase()
        val hasKeyword = KEYWORDS.any { lower.contains(it) }
        if (!hasKeyword) return null

        val normalized = convertDigitsToEnglish(text)

        // الگوی با اولویت بالا: کلمه کلیدی همراه با دو نقطه یا فاصله و سپس عدد ۴ تا ۸ رقمی
        val explicitPattern = Regex("""(?i)(?:کد(?:\s*تایید|\s*ورود|\s*فعالسازی)?|رمز(?:\s*پویا|\s*یکبار\s*مصرف|\s*ورود)?|code|otp|pin)\s*[:=\-]?\s*([0-9]{4,8})\b""")
        val explicitMatch = explicitPattern.find(normalized)
        if (explicitMatch != null) {
            val code = explicitMatch.groupValues[1]
            if (isValidOtpCandidate(code, normalized)) {
                return code
            }
        }

        // الگوی عمومی: یافتن تمام اعداد ۴ تا ۸ رقمی مستقل
        val matches = Regex("""\b(?<!\d)(\d{4,8})(?!\d)\b""").findAll(normalized).toList()
        for (m in matches) {
            val candidate = m.value
            if (isValidOtpCandidate(candidate, normalized)) {
                return candidate
            }
        }

        return null
    }

    private fun isValidOtpCandidate(candidate: String, fullText: String): Boolean {
        // حذف سال‌های شمسی رایج مانند 1402, 1403, 1404, 1405
        if (candidate in listOf("1400", "1401", "1402", "1403", "1404", "1405", "1406", "2024", "2025", "2026")) {
            // اگر در متن کلمه سال یا تاریخ قبل/بعد آن آمده باشد فیلتر شود
            if (fullText.contains("سال $candidate") || fullText.contains("/$candidate") || fullText.contains("$candidate/")) {
                return false
            }
        }
        // کاندیدا نباید بخشی از شماره کارت ۱۶ رقمی یا حساب باشد
        return candidate.length in 4..8
    }
}
