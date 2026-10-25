package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class CoverTypographyTest {
    @Test fun storyAndEpisodeHaveTheirExactThreeRequiredEntries() {
        assertEquals(listOf("ชื่อช่อง","ชื่อเรื่อง 100%","คำโปรยเรื่อง"),CoverTypography.lines("ชื่อช่อง","ชื่อเรื่อง 100%",tagline="คำโปรยเรื่อง"))
        assertEquals(listOf("ชื่อช่อง","ชื่อเรื่อง 100%","ตอนที่ 3 ชื่อตอน"),CoverTypography.lines("ชื่อช่อง","ชื่อเรื่อง 100%",3,"ชื่อตอน","ห้ามแสดงคำโปรย EP"))
        val poster=CoverTypography.prompt("ชื่อช่อง","ชื่อเรื่อง",3,"ชื่อตอน","คำโปรยที่ไม่ใช้")
        assertTrue(poster.contains("BEGIN EXACT COVER TEXT\nชื่อช่อง\nชื่อเรื่อง\nตอนที่ 3 ชื่อตอน\nEND EXACT COVER TEXT"))
        assertFalse(poster.contains("คำโปรยที่ไม่ใช้"))
        assertTrue(poster.contains("INSIDE THE ACTUAL IMAGE")); assertTrue(poster.contains("PENDING"))
        assertTrue(poster.contains("do not advance to next"))
    }
    @Test fun missingMetadataStopsInsteadOfInventingTitlesOrProducingTextlessCovers() {
        listOf(CoverTypography.prompt("","เรื่อง",tagline="คำโปรย"),CoverTypography.prompt("ช่อง","",tagline="คำโปรย"),
            CoverTypography.prompt("ช่อง","เรื่อง"),CoverTypography.prompt("ช่อง","เรื่อง",1,""))
            .forEach { assertTrue(it.startsWith("STOP")); assertTrue(it.contains("PENDING")); assertFalse(it.contains("BEGIN EXACT COVER TEXT")) }
        assertFalse(CoverTypography.prompt("ช่อง","เรื่อง",1,"ตอน").startsWith("STOP"))
    }
}
