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
        "پایا", "ساتنا", "شبا", "شاپرک", "بلوبانک", "بانکینو", "ویپاد", "رمز پویا"
    )

    private val KNOWN_BANKS = listOf(
        "بلوبانک" to listOf("بلوبانک", "blubank", "بلو بانک", "بلو\n", "بلو "),
        "بانک ملت" to listOf("بانک ملت", "mellat", "ملت", "bankmellat"),
        "بانک ملی" to listOf("بانک ملی", "melli", "ملی ایران", "bmi"),
        "بانک صادرات" to listOf("بانک صادرات", "saderat", "صادرات", "bsi"),
        "بانک تجارت" to listOf("بانک تجارت", "tejarat", "تجارت"),
        "بانک سامان" to listOf("بانک سامان", "saman", "سامان"),
        "بانک پارسیان" to listOf("بانک پارسیان", "parsian", "پارسیان"),
        "بانک پاسارگاد" to listOf("بانک پاسارگاد", "pasargad", "پاسارگاد", "bpi"),
        "بانک سپه" to listOf("بانک سپه", "sepah", "سپه", "انصار", "قوامین", "حکمت", "کوثر", "مهر اقتصاد"),
        "بانک مسکن" to listOf("بانک مسکن", "maskan", "مسکن"),
        "بانک کشاورزی" to listOf("بانک کشاورزی", "keshavarzi", "کشاورزی", "bki"),
        "بانک رفاه" to listOf("بانک رفاه", "refah", "رفاه کارگران", "رفاه"),
        "بانک رسالت" to listOf("بانک رسالت", "resalat", "رسالت"),
        "بانک مهر ایران" to listOf("مهر ایران", "qmb", "قرض الحسنه مهر", "مهرایران"),
        "بانک شهر" to listOf("بانک شهر", "shahr", "بانک‌شهر"),
        "بانک آینده" to listOf("بانک آینده", "ayandeh", "آینده"),
        "بانک دی" to listOf("بانک دی", "day bank", "بانک‌دی"),
        "ویپاد" to listOf("ویپاد", "wepod"),
        "بانک خاورمیانه" to listOf("خاورمیانه", "middleeast"),
        "بانک سینا" to listOf("بانک سینا", "sina", "سینا"),
        "پست بانک" to listOf("پست بانک", "postbank", "پست‌بانک")
    )

    /**
     * بررسی آیا پیامک به یک تراکنش مالی بانکی تعلق دارد؟
     */
    fun isBankMessage(sender: String?, body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val s = (sender ?: "").lowercase()
        val b = body.lowercase()

        // استثنا: پیامک‌های حاوی صرفاً رمز پویا تراکنش نیستند
        if ((b.contains("رمز") || b.contains("کد")) &&
            !b.contains("واریز") && !b.contains("برداشت") && !b.contains("مانده") &&
            !b.contains("نشست") && !b.contains("پرید") && !b.contains("موجودی")
        ) {
            return false
        }

        val hasSenderMatch = KNOWN_BANKS.any { (_, aliases) -> aliases.any { s.contains(it) } }
        val hasBankAction = b.contains("واریز") || b.contains("برداشت") || b.contains("خرید") ||
                b.contains("کسر") || b.contains("نشست") || b.contains("پرید") ||
                b.contains("بستانکار") || b.contains("بدهکار") || b.contains("پرداخت") ||
                b.contains("انتقال وجه") || b.contains("افزایش موجودی") || b.contains("کاهش موجودی")
        val hasFinancialContext = b.contains("مبلغ") || b.contains("ریال") || b.contains("تومان") ||
                b.contains("مانده") || b.contains("موجودی") || b.contains("حساب") || b.contains("کارت")

        return hasSenderMatch || (hasBankAction && hasFinancialContext)
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
        val b = body.lowercase()
        for ((name, aliases) in KNOWN_BANKS) {
            if (aliases.any { s.contains(it) || b.contains(it) }) {
                return name
            }
        }
        if (b.startsWith("بلو\n") || b.startsWith("بلو ") || b.startsWith("بلو:")) {
            return "بلوبانک"
        }
        return "بانک"
    }

    fun detectTransactionType(body: String): String {
        val b = body.replace(",", "").replace("،", "").lowercase()

        val isDeposit = b.contains("واریز") ||
                b.contains("نشست") || // بلوبانک: به حساب شما نشست
                b.contains("بستانکار") ||
                b.contains("افزایش موجودی") ||
                b.contains("انتقال به حساب") ||
                b.contains("دریافت وجه") ||
                Regex("""\+\s*[0-9]{3,}""").containsMatchIn(b)

        val isWithdrawal = b.contains("برداشت") ||
                b.contains("پرید") || // بلوبانک: از حساب شما پرید
                b.contains("بدهکار") ||
                b.contains("کاهش موجودی") ||
                b.contains("خرید") ||
                b.contains("کسر") ||
                b.contains("پرداخت") || // پرداخت قبض، پرداخت صورتحساب
                b.contains("کارمزد") ||
                b.contains("انتقال از") ||
                Regex("""-\s*[0-9]{3,}""").containsMatchIn(b)

        return when {
            isDeposit && !isWithdrawal -> "DEPOSIT"
            isWithdrawal && !isDeposit -> "WITHDRAWAL"
            // در صورت وجود هر دو (مثل "انتقال وجه از ... به حساب شما نشست"):
            b.contains("به حساب شما نشست") || b.contains("نشست.") || b.contains("واریز پول") -> "DEPOSIT"
            b.contains("از حساب شما پرید") || b.contains("پرید.") || b.contains("برداشت پول") -> "WITHDRAWAL"
            isDeposit -> "DEPOSIT"
            isWithdrawal -> "WITHDRAWAL"
            b.contains("انتقال وجه") || b.contains("انتقال") -> "WITHDRAWAL"
            else -> "OTHER"
        }
    }

    private fun extractAmount(body: String): Pair<Long, String> {
        val isRial = body.contains("ریال")
        val clean = body.replace(",", "").replace("،", "")

        val patterns = listOf(
            Regex("""(?i)(?:مبلغ|برداشت|واریز|خرید|کسر|پرداخت|بستانکار|بدهکار)[\s:]*([0-9]{3,12})\b"""),
            Regex("""([0-9]{3,12})\s*(?:ریال|تومان)\s*(?:به حساب شما نشست|از حساب شما پرید|بابت)"""),
            Regex("""([0-9]{3,12})\s*(?:ریال|تومان)"""),
            Regex("""(?i)(?:[+-])\s*([0-9]{3,12})\b""")
        )

        val nonBalanceLines = clean.lines().filter { !it.contains("مانده") && !it.contains("موجودی") }.joinToString(" ")

        for (pattern in patterns) {
            val match = pattern.find(nonBalanceLines) ?: pattern.find(clean)
            if (match != null) {
                val rawNumber = match.groupValues[1].toLongOrNull() ?: continue
                if (rawNumber > 0) {
                    return if (isRial) {
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
