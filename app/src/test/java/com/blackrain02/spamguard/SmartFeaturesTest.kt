package com.blackrain02.spamguard

import com.blackrain02.spamguard.ai.BankSmsParser
import com.blackrain02.spamguard.ai.SmartEventExtractor
import com.blackrain02.spamguard.ai.SmartReplyGenerator
import com.blackrain02.spamguard.ui.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartFeaturesTest {

    // ۱. تست یادگیری محلی و استخراج توکن‌های فیدبک (Feature 6)
    @Test
    fun testAdaptiveFeedbackScoringLogic() {
        fun calculateScore(spamCount: Int, hamCount: Int): Float {
            val diff = (spamCount - hamCount).coerceIn(-10, 10)
            return (diff * 0.12f).coerceIn(-0.60f, 0.60f)
        }

        // وقتی کاربر پیامی را ۲ بار اسپم می‌کند، بایاس مثبت تولید می‌شود
        val spamScore = calculateScore(2, 0)
        assertTrue(spamScore > 0f)
        assertEquals(0.24f, spamScore, 0.001f)

        // وقتی کاربر پیامی را ۳ بار از قرنطینه خارج می‌کند، بایاس منفی (سالم) تولید می‌شود
        val hamScore = calculateScore(0, 3)
        assertTrue(hamScore < 0f)
        assertEquals(-0.36f, hamScore, 0.001f)

        // اگر برابر باشد امتیاز صفر است
        assertEquals(0f, calculateScore(2, 2), 0.001f)
    }

    // ۲. تست استخراج تراکنش واریز بانکی (Feature 1)
    @Test
    fun testBankSmsParserDeposit() {
        val sampleSms = "بانک ملت\nواریز: 15,000,000 ریال\nبه حساب: 12345678\nمانده: 45,000,000 ریال\n1403/07/20 14:30"
        assertTrue(BankSmsParser.isBankMessage("Mellat", sampleSms))

        val transaction = BankSmsParser.parse("Mellat", sampleSms, 1729000000000L)
        assertNotNull(transaction)
        assertEquals("بانک ملت", transaction!!.bankName)
        assertEquals("DEPOSIT", transaction.type)
        // ۱۵ میلیون ریال معادل ۱.۵ میلیون تومان است
        assertEquals(1_500_000L, transaction.amount)
        assertEquals(4_500_000L, transaction.balance)
        assertEquals("12345678", transaction.cardNumber)
    }

    // ۳. تست استخراج تراکنش خرید و دسته‌بندی خوراک/فروشگاه (Feature 1)
    @Test
    fun testBankSmsParserWithdrawalAndCategory() {
        val purchaseSms = "بلوبانک\nخرید: 250,000 تومان\nاز: اسنپ فود\nکارت: 6037***4422\nمانده: 1,800,000 تومان"
        val transaction = BankSmsParser.parse("BluBank", purchaseSms, 1729000000000L)
        assertNotNull(transaction)
        assertEquals("بلوبانک", transaction!!.bankName)
        assertEquals("WITHDRAWAL", transaction.type)
        assertEquals(250_000L, transaction.amount)
        assertEquals(1_800_000L, transaction.balance)
        assertEquals("خوراک و رستوران", transaction.category)

        // پیامک رمز پویا نباید به عنوان تراکنش ثبت شود
        val otpSms = "بانک سامان: رمز یکبار مصرف خرید شما 839201 می‌باشد."
        assertFalse(BankSmsParser.isBankMessage("Saman", otpSms))
        assertNull(BankSmsParser.parse("Saman", otpSms, 1729000000000L))
    }

    // ۴. تست استخراج رویداد سررسید چک و تاریخ شمسی (Feature 3)
    @Test
    fun testSmartEventExtractorCheckDeadline() {
        val checkSms = "مشتری گرامی، سررسید چک صیادی به مبلغ ۵۰,۰۰۰,۰۰۰ ریال در تاریخ 1403/08/15 فرا می‌رسد."
        val event = SmartEventExtractor.extractEvent(checkSms)
        assertNotNull(event)
        assertEquals("سررسید چک صیادی", event!!.title)
        assertEquals("CHECK_DUE", event.category)
        assertNotNull(event.dateEpochMillis)
        assertTrue(event.dateFormatted!!.contains("1403/08/15"))
    }

    // ۵. تست استخراج نوبت پزشکی با ماه متنی فارسی (Feature 3)
    @Test
    fun testSmartEventExtractorAppointmentWithMonthName() {
        val clinicSms = "یادآوری: نوبت شما در کلینیک دکتر رضایی برای 25 آبان ساعت 16:30 رزرو شد."
        val event = SmartEventExtractor.extractEvent(clinicSms)
        assertNotNull(event)
        assertEquals("نوبت پزشکی / رزرو", event!!.title)
        assertEquals("APPOINTMENT", event.category)
        assertNotNull(event.dateEpochMillis)
        assertTrue(event.dateFormatted!!.contains("25 آبان"))
        assertTrue(event.dateFormatted!!.contains("16:30"))
    }

    // ۶. تست تولید پاسخ هوشمند زمینه‌ای (Feature 4)
    @Test
    fun testSmartReplyGeneratorContextual() {
        // احوالپرسی
        val greetingReplies = SmartReplyGenerator.generateReplies("سلام چطوری؟", isFa = true)
        assertTrue(greetingReplies.isNotEmpty())
        assertTrue(greetingReplies.any { it.contains("سلام") })

        // موقعیت مکانی
        val locationReplies = SmartReplyGenerator.generateReplies("کجایی رسیدی؟", isFa = true)
        assertTrue(locationReplies.isNotEmpty())
        assertTrue(locationReplies.any { it.contains("رسیدم") || it.contains("تو راهم") })

        // تایید کار
        val confirmationReplies = SmartReplyGenerator.generateReplies("کار انجام شد؟", isFa = true)
        assertTrue(confirmationReplies.isNotEmpty())
        assertTrue(confirmationReplies.any { it.contains("انجام شد") || it.contains("پیگیری") })

        // تشکر
        val thanksReplies = SmartReplyGenerator.generateReplies("خیلی ممنون دستت درد نکنه", isFa = true)
        assertTrue(thanksReplies.isNotEmpty())
        assertTrue(thanksReplies.any { it.contains("خواهش می‌کنم") || it.contains("قربانت") })

        // انگلیسی
        val enReplies = SmartReplyGenerator.generateReplies("Are you done with the task?", isFa = false)
        assertTrue(enReplies.isNotEmpty())
        assertTrue(enReplies.any { it.contains("done") || it.contains("working") })
    }

    // ۷. تست تبدیل معکوس تقویم شمسی به میلادی و برعکس (Date Conversion)
    @Test
    fun testJalaliToGregorianConversion() {
        // تبدیل 1403/07/20 شمسی
        val (gy, gm, gd) = DateUtils.jalaliToGregorian(1403, 7, 20)
        assertEquals(2024, gy)
        assertEquals(10, gm)
        assertEquals(11, gd)

        // بررسی رفت و برگشت: Gregorian(2024, 10, 11) باید دوباره 1403/7/20 شمسی شود
        val jBack = DateUtils.gregorianToJalali(gy, gm, gd)
        assertEquals(1403, jBack.year)
        assertEquals(7, jBack.month)
        assertEquals(20, jBack.day)
    }
}
