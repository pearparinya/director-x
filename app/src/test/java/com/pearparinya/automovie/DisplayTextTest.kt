package com.pearparinya.automovie

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class DisplayTextTest {
    @Test fun removesOnlyRepeatedParenthesizedCategory() {
        assertEquals("สยองขวัญ",DisplayText.category("สยองขวัญ (สยองขวัญ)"))
        assertEquals("สยองขวัญ (วิญญาณ)",DisplayText.category("สยองขวัญ (วิญญาณ)"))
    }
    @Test fun displaysBuddhistYearRegardlessOfDeviceLocale() {
        val zone=TimeZone.getTimeZone("Asia/Bangkok")
        val time=GregorianCalendar(zone,Locale.ROOT).apply { clear(); set(2026,10,14,8,39) }.timeInMillis
        assertEquals("14 พ.ย. 2569 เวลา 08.39 น.",DisplayText.thaiDateTime(time,zone))
    }
}
