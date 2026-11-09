package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FaceReferenceStoreTest {
    @Test fun cropsAreSeparatedByOriginalBytesAndNeverOverwritePreviousVersions() {
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"face-store-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val store=FaceReferenceStore(File(dir,"crops"))
            val a=File(dir,"a.master").apply { writeText("original-a") }
            val b=File(dir,"b.master").apply { writeText("original-b") }
            val one=store.newFile(a).apply { parentFile!!.mkdirs(); writeText("first") ; setLastModified(1000L) }
            assertEquals(one,store.latest(a)); assertNull(store.latest(b))
            val two=store.newFile(a).apply { writeText("second"); setLastModified(2000L) }
            assertNotEquals(one,two); assertEquals(two,store.latest(a)); assertEquals("first",one.readText())
            a.writeText("changed-master")
            assertNull(store.latest(a)); assertNull(store.latest(b))
        } finally { dir.deleteRecursively() }
    }
}
