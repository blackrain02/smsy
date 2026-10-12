package com.binacity.spamguard

import com.binacity.spamguard.ui.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilsTest {

    @Test
    fun testGregorianToJalaliNowruz() {
        // 2024-03-20 -> 1403/01/01
        val jDate1 = DateUtils.gregorianToJalali(2024, 3, 20)
        assertEquals(1403, jDate1.year)
        assertEquals(1, jDate1.month)
        assertEquals(1, jDate1.day)

        // 2025-03-21 -> 1404/01/01
        val jDate2 = DateUtils.gregorianToJalali(2025, 3, 21)
        assertEquals(1404, jDate2.year)
        assertEquals(1, jDate2.month)
        assertEquals(1, jDate2.day)
    }

    @Test
    fun testGregorianToJalaliMonthBoundaries() {
        // 2024-09-21 -> 1403/06/31 (پایان شهریور)
        val jDateEndShahrivar = DateUtils.gregorianToJalali(2024, 9, 21)
        assertEquals(1403, jDateEndShahrivar.year)
        assertEquals(6, jDateEndShahrivar.month)
        assertEquals(31, jDateEndShahrivar.day)

        // 2024-09-22 -> 1403/07/01 (آغاز مهر)
        val jDateStartMehr = DateUtils.gregorianToJalali(2024, 9, 22)
        assertEquals(1403, jDateStartMehr.year)
        assertEquals(7, jDateStartMehr.month)
        assertEquals(1, jDateStartMehr.day)
    }

    @Test
    fun testGregorianToJalaliWinter() {
        // 2025-02-11 -> 1403/11/23
        val jDate22Bahman = DateUtils.gregorianToJalali(2025, 2, 11)
        assertEquals(1403, jDate22Bahman.year)
        assertEquals(11, jDate22Bahman.month)
        assertEquals(23, jDate22Bahman.day)
    }

    @Test
    fun testFormatMessageTime() {
        // تست قالب‌بندی زمان با تایم‌استمپ نامعتبر
        assertEquals("", DateUtils.formatMessageTime(-1L))
        assertEquals("", DateUtils.formatMessageTime(0L))
    }
}
