package com.pearparinya.automovie

import java.text.SimpleDateFormat
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

internal object DisplayText {
    fun category(value: String): String {
        val source=value.trim()
        val match=Regex("^(.*?)\\s*\\(([^()]*)\\)\\s*$").matchEntire(source) ?: return source
        val main=match.groupValues[1].trim(); val sub=match.groupValues[2].trim()
        return if(main.equals(sub,ignoreCase=true)) main else source
    }
    fun thaiDateTime(time: Long,zone: TimeZone=TimeZone.getDefault()): String {
        val locale=Locale("th","TH")
        val date=Date(time)
        val day=SimpleDateFormat("d MMM",locale).apply { timeZone=zone }.format(date)
        val clock=SimpleDateFormat("HH.mm",Locale.ROOT).apply { timeZone=zone }.format(date)
        val year=GregorianCalendar(zone,Locale.ROOT).apply { this.time=time }.get(java.util.Calendar.YEAR)+543
        return "$day $year เวลา $clock น."
    }
}
