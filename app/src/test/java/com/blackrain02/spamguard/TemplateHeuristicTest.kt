package com.blackrain02.spamguard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateHeuristicTest {

    private fun isEligibleTemplate(body: String): Boolean {
        val clean = body.replace(Regex("\\s+"), " ").trim()
        return clean.length in 8..80 &&
                !clean.contains("http://") &&
                !clean.contains("https://") &&
                !clean.all { ch -> ch.isDigit() }
    }

    @Test
    fun testEligiblePersianPhrases() {
        assertTrue(isEligibleTemplate("درود، در جلسه هستم تماس می‌گیرم."))
        assertTrue(isEligibleTemplate("سلام، رسیدم به مقصد."))
        assertTrue(isEligibleTemplate("لطفاً آدرس دقیق را ارسال بفرمایید."))
    }

    @Test
    fun testFilterShortOrLongPhrases() {
        assertFalse(isEligibleTemplate("سلام")) // کمتر از ۸ کاراکتر
        assertFalse(isEligibleTemplate("بله")) // کمتر از ۸ کاراکتر
        val tooLong = "این یک پیامک بسیار طولانی است که کاربر ارسال کرده و نباید به عنوان الگوی پاسخ آماده انتخاب شود چون بیشتر از هشتاد کاراکتر طول دارد و برای پاسخ سریع مناسب نیست."
        assertFalse(isEligibleTemplate(tooLong))
    }

    @Test
    fun testFilterUrlsAndAllNumbers() {
        assertFalse(isEligibleTemplate("لطفاً از سایت ما بازدید کنید https://example.com"))
        assertFalse(isEligibleTemplate("123456789012"))
    }
}
