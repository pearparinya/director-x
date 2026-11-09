package com.pearparinya.automovie

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class CharacterLibraryResetTest {
    @Test fun clearsReferencesOncePreservingScriptsMediaAndSubsequentImports() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"reset-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            fun seed(path: String)=File(root,path).apply { parentFile!!.mkdirs(); writeText(path) }
            val removed=listOf("character_masters/gawin.master","master_library/CHAR_001.png","supporting_masters/a.master","supporting_cast.json",
                "studio/story/original_masters/a.master","studio/story/masters/A.png","studio/story/master_candidates/A.png",
                "studio/story/versions/old/a.master_123","studio/story/versions/a/master/123.png").map(::seed)
            val kept=listOf("studio/story/story.json","studio/story/images/EP01_SC01.png","studio/story/videos/EP01_SC01.mp4","studio/story/versions/old/story.json_123").map(::seed)
            CharacterLibraryReset.install(root)
            assertTrue(removed.none { it.exists() })
            kept.forEach { assertEquals(it.relativeTo(root).path,it.readText()) }
            val fresh=seed("character_masters/new.master")
            CharacterLibraryReset.install(root)
            assertTrue(fresh.isFile)
        } finally { root.deleteRecursively() }
    }
    @Test fun replacementLibraryContainsAllOriginalSheetsAndNames() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.cacheDir,"new-library-${UUID.randomUUID()}")
        try {
            val library=MasterLibrary(root) { context.assets.open(it).use { input -> input.readBytes() } }
            val entries=library.entries()
            assertEquals((listOf(103)+(130..152)+(300..309)).map { "CHAR_%03d".format(it) },entries.map { it.id })
            assertEquals(listOf("กวิน","คมสัน","ภูวดล","ธำรง","ปกรณ์","วายุ","ภาคิน","ลุงสมบัติ","นที","ธีรภัทร","อัคนี","บุญมี","กฤต","ชาญ","ภาณุ","ศุภชัย","พิทักษ์","ปิ่นมณี","ลลิตา","เพ็ญศรี","พิมพ์ชนก","ภัทรวดี","ณัฐชา","ศุภรัตน์"),entries.take(24).map { it.displayName })
            assertEquals(listOf(38,33,50,25,42,28,46),entries.take(24).takeLast(7).map { it.characterAge })
            assertEquals(entries.size,entries.map { it.sha256 }.distinct().size)
            assertEquals(listOf("animal","animal","fantasy","spirit","human","fantasy","deity_or_demon","animal","deity_or_demon","fantasy"),entries.takeLast(10).map { it.characterType })
            entries.forEach { entry ->
                val source=library.source(entry)
                assertEquals(entry.sha256,MasterLibrary.digest(source.readBytes()))
                val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                android.graphics.BitmapFactory.decodeFile(source.path,bounds)
                assertTrue(bounds.outWidth>0 && bounds.outHeight>0)
            }
            assertTrue(context.assets.list("character_masters").orEmpty().isEmpty())
            assertEquals(entries.size+1,context.assets.list("master_library")!!.size)
        } finally { root.deleteRecursively() }
    }
}
