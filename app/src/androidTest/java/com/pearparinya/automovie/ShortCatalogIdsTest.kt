package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortCatalogIdsTest {
    @Test fun stableAcrossReorderRemovalAndNewEntries() {
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"shortid-test-${System.nanoTime()}")
        try {
            val file=File(dir,"ids.json"); val store=ShortCatalogIds(file)
            val first=store.assign(listOf("legacy-human" to "มนุษย์","legacy-ghost" to "ผี วิญญาณ และภูต","legacy-prop" to "อาวุธและอุปกรณ์"))
            assertEquals("H001",first["legacy-human"]); assertEquals("G001",first["legacy-ghost"]); assertEquals("P001",first["legacy-prop"])
            val next=ShortCatalogIds(file).assign(listOf("legacy-ghost" to "มนุษย์","new-human" to "มนุษย์"))
            assertEquals("G001",next["legacy-ghost"]); assertEquals("H001",next["legacy-human"]); assertEquals("H002",next["new-human"])
            assertEquals(4,next.values.toSet().size)
        } finally { dir.deleteRecursively() }
    }
    @Test fun corruptRegistryDoesNotSilentlyReplaceExistingIds() {
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"shortid-corrupt-${System.nanoTime()}")
        try { dir.mkdirs(); val file=File(dir,"ids.json"); file.writeText("{broken")
            assertTrue(runCatching { ShortCatalogIds(file).assign(listOf("a" to "มนุษย์")) }.isFailure)
            assertEquals("{broken",file.readText())
        } finally { dir.deleteRecursively() }
    }
    @Test fun restoreRegistryRejectsCollisionsWithoutReplacingExisting() {
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"shortid-restore-${System.nanoTime()}")
        try {
            dir.mkdirs(); val target=File(dir,"current.json"); val backup=File(dir,"backup.json")
            target.writeText("{\"legacy-a\":\"H001\"}")
            backup.writeText("{\"legacy-b\":\"G001\"}")
            ShortCatalogIds(target).mergeBackup(backup)
            val before=target.readText(); assertTrue(before.contains("G001"))
            backup.writeText("{\"legacy-c\":\"H001\"}")
            assertTrue(runCatching { ShortCatalogIds(target).mergeBackup(backup) }.isFailure)
            assertEquals(before,target.readText())
        } finally { dir.deleteRecursively() }
    }
}
