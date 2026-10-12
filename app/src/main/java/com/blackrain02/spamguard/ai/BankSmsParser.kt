package com.blackrain02.spamguard.ai

import com.blackrain02.spamguard.data.local.BankTransactionEntity
import com.blackrain02.spamguard.ui.util.OtpHelper

/**
 * سیستم پردازش و استخراج هوشمند پیامک‌های بانکی (On-Device Bank SMS Parser)
 * ۱۰۰٪ محلی و آفلاین جهت حفظ امنیت و حریم خصوصی اطلاعات مالی
 */
object BankSmsParser {

    private val BANK_KEYWORDS = listOf(
        "بانک", "واریز", "برداشت", "مانده", "موجودی", "حساب", "کارت",
        "پایا", "ساتنا", "شبا", "شاپرک", "بلوبانک", "بانکینو", "رمز پویا"
    )

    private val KNOWN_BANKS = listOf(
        "بلوبانک" to listOf("بلوبانک", "blubank", "بلو بانک"),
        "بانک ملت" to listOf("بانک ملت", "mellat", "ملت"),
        "بانک ملی" to listOf("بانک ملی", "melli", "ملی ایران"),
        "بانک صادرات" to listOf("بانک صادرات", "saderat", "صادرات"),
        "بانک تجارت" to listOf("بانک تجارت", "tejarat", "تجارت"),
        "بانک سامان" to listOf("بانک سامان", "saman", "سامان"),
        "بانک پارسیان" to listOf("بانک پارسیان", "parsian", "پارسیان"),
        "بانک پاسارگاد" to listOf("بانک پاسارگاد", "pasargad", "پاسارگاد"),
        "بانک سپه" to listOf("بانک سپه", "sepah", "سپه", "انصار", "قوامین"),
        "بانک مسکن" to listOf("بانک مسکن", "maskan", "مسکن"),
        "بانک کشاورزی" to listOf("بانک کشاورزی", "keshavarzi", "کشاورزی"),
        "بانک رفاه" to listOf("بانک رفاه", "refah", "رفاه کارگران"),
        "بانک رسالت" to listOf("بانک رسالت", "resalat", "رسالت"),
        "بانک مهر ایران" to listOf("مهر ایران", "qmb", "قرض الحسنه مهر"),
        "بانک شهر" to listOf("بانک شهر", "shahr", "شهر"),
        "بانک آینده" to listOf("بانک آینده", "ayandeh", "آینده"),
        "بانک دی" to listOf("بانک دی", "day", "دی")
    )

    /**
     * بررسی آیا پیامک به یک تراکنش مالی بانکی تعلق دارد؟
     */
    fun isBankMessage(sender: String?, body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val s = (sender ?: "").lowercase()
        val b = body.lowercase()

        // استثنا: پیامک‌های حاوی صرفاً رمز پویا تراکنش نیستند
        if ((b.contains("رمز") || b.contains("کد")) && !b.contains("واریز") && !b.contains("برداشت") && !b.contains("مانده")) {
            return false
        }

        val hasSenderMatch = KNOWN_BANKS.any { (_, aliases) -> aliases.any { s.contains(it) } }
        val hasBankKeyword = (b.contains("واریز") || b.contains("برداشت") || b.contains("خرید") || b.contains("کسر")) &&
                (b.contains("مبلغ") || b.contains("ریال") || b.contains("تومان") || b.contains("مانده") || b.contains("موجودی"))

        return hasSenderMatch || hasBankKeyword
    }

    /**
     * استخراج اطلاعات تراکنش از متن پیامک بانکی
     */
    fun parse(sender: String?, body: String?, timestamp: Long): BankTransactionEntity? {
        if (!isBankMessage(sender, body) || body == null) return null

        val normalized = OtpHelper.toLatinDigits(body)
        val bankName = detectBankName(sender, normalized)
        val transactionType = detectTransactionType(normalized)
        val (amount, unit) = extractAmount(normalized)
        if (amount <= 0) return null

        val balance = extractBalance(normalized)
        val cardNumber = extractCardOrAccount(normalized)
        val category = categorize(normalized, transactionType)

        return BankTransactionEntity(
            bankName = bankName,
            amount = amount,
            amountUnit = unit,
            type = transactionType,
            balance = balance,
            cardNumber = cardNumber,
            category = category,
            rawBody = body,
            timestamp = timestamp
        )
    }

    private fun detectBankName(sender: String?, body: String): String {
        val s = (sender ?: "").lowercase()
        for ((name, aliases) in KNOWN_BANKS) {
            if (aliases.any { s.contains(it) || body.contains(it, ignoreCase = true) }) {
                return name
            }
        }
        return "بانک"
    }

    private fun detectTransactionType(body: String): String {
        return when {
            body.contains("واریز") || body.contains("افزایش موجودی") || body.contains("انتقال به حساب") -> "DEPOSIT"
            body.contains("برداشت") || body.contains("خرید") || body.contains("کسر") || body.contains("انتقال از") || body.contains("انتقال وجه") -> "WITHDRAWAL"
            else -> "OTHER"
        }
    }

    private fun extractAmount(body: String): Pair<Long, String> {
        val isRial = body.contains("ریال")
        val clean = body.replace(",", "").replace("،", "")

        val patterns = listOf(
            Regex("""(?i)(?:مبلغ|برداشت|واریز|خرید|کسر|انتقال)[\s:]*([0-9]{3,12})\b"""),
            Regex("""([0-9]{3,12})\s*(?:ریال|تومان)"""),
            Regex("""(?i)(?:مبلغ|انتقال وجه)[\s:]*([0-9]{3,12})""")
        )

        for (pattern in patterns) {
            val match = pattern.find(clean)
            if (match != null) {
                val rawNumber = match.groupValues[1].toLongOrNull() ?: continue
                if (rawNumber > 0) {
                    return if (isRial) {
                        // تبدیل ریال به تومان جهت یکپارچگی تحلیل مالی
                        (rawNumber / 10L) to "تومان"
                    } else {
                        rawNumber to "تومان"
                    }
                }
            }
        }
        return 0L to "تومان"
    }

    private fun extractBalance(body: String): Long? {
        val clean = body.replace(",", "").replace("،", "")
        val patterns = listOf(
            Regex("""(?i)(?:مانده|موجودی|مانده حساب)[\s:]*([0-9]{3,12})\b"""),
            Regex("""(?i)(?:مانده|موجودی)\s*(?:فعلی)?[\s:]*([0-9]{3,12})""")
        )

        for (pattern in patterns) {
            val match = pattern.find(clean)
            if (match != null) {
                val raw = match.groupValues[1].toLongOrNull() ?: continue
                val isRial = body.contains("ریال")
                return if (isRial) raw / 10L else raw
            }
        }
        return null
    }

    private fun extractCardOrAccount(body: String): String? {
        val cardRegex = Regex("""(?i)(?:کارت|حساب|کارت:|حساب:)[\s:]*([0-9\*\-]{4,16})""")
        val match = cardRegex.find(body)
        return match?.groupValues?.get(1)?.trim()
    }

    private fun categorize(body: String, type: String): String {
        val b = body.lowercase()
        return when {
            b.contains("سوپرمارکت") || b.contains("هایپر") || b.contains("کوروش") || b.contains("دیجی") || b.contains("فروشگاه") || b.contains("pos") || b.contains("پوز") -> "خرید و فروشگاه"
            b.contains("رستوران") || b.contains("کافه") || b.contains("اسنپ فود") || b.contains("اسنپ‌فود") || b.contains("فست فود") || b.contains("غذا") -> "خوراک و رستوران"
            b.contains("قبض") || b.contains("شارژ") || b.contains("همراه اول") || b.contains("ایرانسل") || b.contains("برق") || b.contains("گاز") || b.contains("آب") -> "قبوض و خدمات"
            b.contains("کارت به کارت") || b.contains("پایا") || b.contains("ساتنا") || b.contains("انتقال") -> "انتقال وجه"
            b.contains("حقوق") || b.contains("پاداش") || b.contains("مساعده") -> "حقوق و درآمد"
            type == "DEPOSIT" -> "واریز متفرقه"
            type == "WITHDRAWAL" -> "هزینه متفرقه"
            else -> "سایر"
        }
    }
}
