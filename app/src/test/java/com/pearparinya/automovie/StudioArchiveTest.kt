package com.pearparinya.automovie
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
class StudioArchiveTest {
    @Test fun roundTripKeepsNestedAssetsAndVersions() {
        val root=Files.createTempDirectory("archive").toFile()
        try {
            val source=File(root,"source").apply { mkdirs() }
            File(source,"story.json").writeText("{}")
            File(source,"versions/EP01_SC001/image/old.img").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1,2,3)) }
            File(source,"videos/EP01_SC001.mp4").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(4,5)) }
            val bytes=ByteArrayOutputStream(); StudioArchive.write(source,bytes)
            val restored=File(root,"restored"); StudioArchive.read(bytes.toByteArray().inputStream(),restored)
            assertArrayEquals(byteArrayOf(1,2,3),File(restored,"versions/EP01_SC001/image/old.img").readBytes())
            assertArrayEquals(byteArrayOf(4,5),File(restored,"videos/EP01_SC001.mp4").readBytes())
        } finally { root.deleteRecursively() }
    }
    @Test fun rejectsTraversalAndCleansFailedRestore() {
        val root=Files.createTempDirectory("archive").toFile()
        try {
            val bytes=ByteArrayOutputStream(); ZipOutputStream(bytes).use { it.putNextEntry(ZipEntry("../outside")); it.write(1); it.closeEntry() }
            val target=File(root,"target")
            try { StudioArchive.read(bytes.toByteArray().inputStream(),target); fail("unsafe archive accepted") } catch (_: IllegalArgumentException) { }
            assertFalse(target.exists()); assertFalse(File(root,"outside").exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun rejectsOversizedBackup() {
        val root=Files.createTempDirectory("archive").toFile()
        try {
            val bytes=ByteArrayOutputStream(); ZipOutputStream(bytes).use { it.putNextEntry(ZipEntry("story.json")); it.write(ByteArray(100)); it.closeEntry() }
            val target=File(root,"target")
            try { StudioArchive.read(bytes.toByteArray().inputStream(),target,10); fail("oversized accepted") } catch (_: IllegalArgumentException) { }
            assertFalse(target.exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun backupIncludesGlobalCatalogAliases() {
        val root=Files.createTempDirectory("archive-ids").toFile()
        try {
            val source=File(root,"source").apply { mkdirs() }; File(source,"story.json").writeText("{}")
            val registry=File(root,"registry.json"); registry.writeText("{\"legacy\":\"H001\"}")
            val bytes=ByteArrayOutputStream(); StudioArchive.write(source,bytes,registry)
            val restored=File(root,"restored"); StudioArchive.read(bytes.toByteArray().inputStream(),restored)
            assertEquals(registry.readText(),File(restored,"__director_meta/short_catalog_ids.json").readText())
        } finally { root.deleteRecursively() }
    }
}
