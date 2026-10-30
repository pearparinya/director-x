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
    @Test fun shippedLibraryIsEmpty() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val library=MasterLibrary(context.cacheDir) { context.assets.open(it).use { input -> input.readBytes() } }
        assertTrue(library.entries().isEmpty())
        assertTrue(context.assets.list("character_masters").orEmpty().isEmpty())
        assertEquals(listOf("catalog.json"),context.assets.list("master_library")!!.toList())
    }
}
