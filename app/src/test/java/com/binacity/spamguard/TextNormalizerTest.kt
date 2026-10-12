package com.binacity.spamguard

import com.binacity.spamguard.ai.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun testArabicLetterNormalization() {
        // تبدیل ي عربی به ی فارسی و ك به ک
        val input = "پيامك جديد بانك"
        val normalized = TextNormalizer.normalize(input)
        assertEquals("پیامک جدید بانک", normalized)
    }

    @Test
    fun testHalfSpaceRemoval() {
        val input = "می‌خواهم این‌جا باشم"
        val normalized = TextNormalizer.normalize(input)
        assertEquals("می خواهم این جا باشم", normalized)
    }

    @Test
    fun testNumberAndUrlReplacement() {
        val input = "مبلغ 50000 تومان به https://bank.ir/pay پرداخت شد"
        val normalized = TextNormalizer.normalize(input)
        assertTrue(normalized.contains("<num>"))
        assertTrue(normalized.contains("<url>"))
    }

    @Test
    fun testEmptyAndBlankHandling() {
        assertEquals("", TextNormalizer.normalize(null))
        assertEquals("", TextNormalizer.normalize(""))
        assertEquals("", TextNormalizer.normalize("   "))
    }
}
