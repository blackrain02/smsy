package com.blackrain02.spamguard

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNormalizationTest {

    private fun normalizePhoneNumber(phone: String): String {
        val digits = phone.replace(Regex("[^0-9+]"), "")
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    private fun getConversationKey(address: String): String {
        val norm = normalizePhoneNumber(address)
        return if (norm.length >= 7) norm else address.trim().lowercase()
    }

    @Test
    fun testNormalizePhoneNumberDifferentFormats() {
        val standard = "09123456789"
        val withCountryCode = "+989123456789"
        val withZeroPrefix = "00989123456789"
        val withDashes = "0912-345-6789"
        val withSpaces = "0912 345 6789"

        assertEquals("9123456789", normalizePhoneNumber(standard))
        assertEquals("9123456789", normalizePhoneNumber(withCountryCode))
        assertEquals("9123456789", normalizePhoneNumber(withZeroPrefix))
        assertEquals("9123456789", normalizePhoneNumber(withDashes))
        assertEquals("9123456789", normalizePhoneNumber(withSpaces))
    }

    @Test
    fun testConversationKeyMatching() {
        // گفتگوهای یک مخاطب با شماره‌های با و بدون کد کشور باید کلید یکسان تولید کنند
        val key1 = getConversationKey("+989123456789")
        val key2 = getConversationKey("09123456789")
        val key3 = getConversationKey("0912 345 6789")

        assertEquals(key1, key2)
        assertEquals(key2, key3)
    }

    @Test
    fun testSenderNameKeys() {
        // سرشماره‌های متنی باید به حروف کوچک نرمال‌سازی شوند
        assertEquals("hamrahaval", getConversationKey("HamrahAval"))
        assertEquals("divar", getConversationKey("DIVAR"))
        assertEquals("snapp", getConversationKey("Snapp"))
    }
}
