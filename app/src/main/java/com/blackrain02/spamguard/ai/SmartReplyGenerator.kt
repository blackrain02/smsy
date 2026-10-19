package com.blackrain02.spamguard.ai

import com.blackrain02.spamguard.ui.util.OtpHelper

/**
 * سیستم پیشنهاد پاسخ هوشمند مبتنی بر زمینه گفتگو (On-Device Contextual Smart Reply)
 */
object SmartReplyGenerator {

    fun generateReplies(messageText: String?, isFa: Boolean = true): List<String> {
        if (messageText.isNullOrBlank()) return emptyList()

        // پیامک‌های OTP یا صرفاً لینک نیازی به پیشنهاد پاسخ ندارند
        if (OtpHelper.isOtpMessage(messageText)) return emptyList()

        val text = messageText.trim()
        val lower = text.lowercase()
        val hasPersianLetters = text.any { it in '\u0600'..'\u06FF' }
        val isStrictlyEnglish = text.any { it in 'a'..'z' || it in 'A'..'Z' } && !hasPersianLetters
        val usePersian = hasPersianLetters || isFa || !isStrictlyEnglish

        // ۱. سلام و احوال‌پرسی
        if (lower.contains("سلام") || lower.contains("درود") || lower.contains("صبح بخیر") ||
            lower.contains("عصر بخیر") || lower.contains("شب بخیر") || lower.contains("چطوری") || lower.contains("خوبی") ||
            lower.contains("سلام وقت بخیر") || lower.contains("ارادت")
        ) {
            return if (usePersian) {
                listOf("سلام، وقت بخیر", "سلام، ممنون شما چطورید؟", "درود، ارادتمندم")
            } else {
                listOf("Hello, doing well!", "Hi there!", "Good day!")
            }
        }
        if (lower.contains("hello") || lower.contains("hi ") || lower.startsWith("hi") || lower.contains("how are you")) {
            return if (usePersian) {
                listOf("سلام، ممنون، شما خوبی؟", "درود، وقت بخیر")
            } else {
                listOf("Hello, doing well!", "Hi! All good, thanks", "Hey there!")
            }
        }

        // ۲. موقعیت مکانی و رسیدن
        if (lower.contains("کجایی") || lower.contains("رسیدی") || lower.contains("رسیدید") ||
            lower.contains("کجا هستی") || lower.contains("کجایید")
        ) {
            return if (usePersian) {
                listOf("تو راهم، به‌زودی می‌رسم", "بله، رسیدم", "نزدیکم، ۵ دقیقه دیگه")
            } else {
                listOf("On my way!", "Yes, just arrived", "Almost there")
            }
        }
        if (lower.contains("where are you") || lower.contains("did you arrive") || lower.contains("arrived")) {
            return listOf("On my way!", "Yes, just arrived", "Almost there")
        }

        // ۳. تایید انجام کار یا استعلام وضعیت
        if (lower.contains("تایید شد") || lower.contains("انجام شد") || lower.contains("اوکی شد") ||
            lower.contains("حل شد") || lower.contains("فرستادی") || lower.contains("ارسال شد")
        ) {
            return if (usePersian) {
                listOf("بله، انجام شد", "خیر، هنوز نه", "در حال پیگیری هستم")
            } else {
                listOf("Yes, all done", "Not yet, on it", "Will update you soon")
            }
        }
        if (lower.contains("done") || lower.contains("finished") || lower.contains("is it ready") || lower.contains("sent")) {
            return listOf("Yes, all done", "Not yet, working on it", "Will check shortly")
        }

        // ۴. تشکر و قدردانی
        if (lower.contains("ممنون") || lower.contains("تشکر") || lower.contains("سپاس") ||
            lower.contains("دستت درد نکنه") || lower.contains("لطف کردی")
        ) {
            return if (usePersian) {
                listOf("خواهش می‌کنم، وظیفه بود", "قربانت، سلامت باشی", "ارادتمندم")
            } else {
                listOf("You're welcome!", "Anytime!", "Glad to help")
            }
        }
        if (lower.contains("thanks") || lower.contains("thank you")) {
            return listOf("You're welcome!", "Glad to help!", "Anytime!")
        }

        // ۵. جلسه و هماهنگی زمان
        if (lower.contains("کی بیام") || lower.contains("وقت داری") || lower.contains("جلسه") ||
            lower.contains("کی ببینمت") || lower.contains("هماهنگ کنیم")
        ) {
            return if (usePersian) {
                listOf("فردا مناسبه", "تماس می‌گیرم هماهنگ کنیم", "ساعت چند مد نظرتونه؟")
            } else {
                listOf("Tomorrow works for me", "I'll call to coordinate", "What time works best?")
            }
        }

        // ۶. سوالات عمومی
        if (text.contains("؟") || text.contains("?") || lower.contains("آیا") || lower.contains("چطور")) {
            return if (usePersian) {
                listOf("بررسی می‌کنم خبر میدم", "بله، موافقم", "تماس می‌گیرم")
            } else {
                listOf("Let me check and get back to you", "Yes, agreed", "I'll call you")
            }
        }

        // ۷. پیش‌فرض مکالمات کوتاه
        if (text.length in 3..60 && !text.contains("http")) {
            return if (usePersian) {
                listOf("باشه، حتماً", "ممنون از اطلاع‌رسانی", "تماس می‌گیرم")
            } else {
                listOf("Got it, thanks", "Sounds good", "Will follow up")
            }
        }

        return emptyList()
    }
}
