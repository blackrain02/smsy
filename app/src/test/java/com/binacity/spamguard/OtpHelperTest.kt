package com.binacity.spamguard

import com.binacity.spamguard.ui.util.OtpHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpHelperTest {

    @Test
    fun testConvertDigitsToEnglish() {
        val persian = "۰۱۲۳۴۵۶۷۸۹"
        val arabic = "٠١٢٣٤٥٦٧٨٩"
        assertEquals("0123456789", OtpHelper.convertDigitsToEnglish(persian))
        assertEquals("0123456789", OtpHelper.convertDigitsToEnglish(arabic))
        assertEquals("Code: 123456", OtpHelper.convertDigitsToEnglish("Code: ۱۲۳۴۵۶"))
    }

    @Test
    fun testIsOtpMessage() {
        val msg1 = "رمز یکبار مصرف شما: 492817 است. بانک سامان"
        val msg2 = "کد تایید ورود به حساب: ۵۸۲۹۱"
        val msg3 = "Your verification code is 884102. Valid for 5 minutes."
        val msg4 = "سلام، فردا جلسه ساعت ۱۰ برگزار می‌شود."
        val msg5 = "تخفیف ۵۰ درصدی پاییزی فروشگاه! همین حالا خرید کنید."

        assertTrue(OtpHelper.isOtpMessage(msg1))
        assertTrue(OtpHelper.isOtpMessage(msg2))
        assertTrue(OtpHelper.isOtpMessage(msg3))
        assertFalse(OtpHelper.isOtpMessage(msg4))
        assertFalse(OtpHelper.isOtpMessage(msg5))
    }

    @Test
    fun testExtractOtpPersianAndEnglish() {
        val bankMelli = "رمز پویا: 729104\nانتقال وجه بانک ملی"
        assertEquals("729104", OtpHelper.extractOtp(bankMelli))

        val snapp = "کد فعالسازی اسنپ: ۳۹۱۸۴"
        assertEquals("39184", OtpHelper.extractOtp(snapp))

        val google = "G-849201 is your Google verification code."
        assertEquals("849201", OtpHelper.extractOtp(google))

        val colonOtp = "رمز ورود: 4019"
        assertEquals("4019", OtpHelper.extractOtp(colonOtp))
    }

    @Test
    fun testIgnoreCardNumbersAndYears() {
        val bankMsgWithCard = "رمز پویا: 51920\nکارت ۶۰۳۷۹۹۱۸۲۳۴۵۶۷۸۹\nمبلغ ۱۰۰,۰۰۰ ریال"
        assertEquals("51920", OtpHelper.extractOtp(bankMsgWithCard))

        val dateText = "کد ورود: 8129 سال 1403"
        assertEquals("8129", OtpHelper.extractOtp(dateText))

        val normalSpam = "فرصت استثنایی خرید ملک در سال 1403"
        assertNull(OtpHelper.extractOtp(normalSpam))
    }
}
