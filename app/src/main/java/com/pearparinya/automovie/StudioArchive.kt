package com.pearparinya.automovie

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Full workspace backup, distinct from delivery ZIPs; restores into a new directory. */
internal object StudioArchive {
    fun write(folder: File, output: OutputStream, catalogIds: File? = null) {
        require(File(folder,"story.json").isFile) { "ไม่มีบทสำหรับสำรอง" }
        ZipOutputStream(output).use { zip ->
            folder.walkTopDown().filter { it.isFile && !it.name.endsWith(".pending") && !it.name.endsWith(".tmp") && it.relativeTo(folder).invariantSeparatorsPath != "__director_meta/short_catalog_ids.json" }.forEach { file ->
                zip.putNextEntry(ZipEntry(file.relativeTo(folder).invariantSeparatorsPath))
                file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
            if(catalogIds?.isFile == true) {
                zip.putNextEntry(ZipEntry("__director_meta/short_catalog_ids.json"))
                catalogIds.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }
    fun read(input: InputStream, staging: File, maxBytes: Long = 1024L * 1024 * 1024) {
        require(!staging.exists()) { "พื้นที่กู้คืนต้องเป็นโฟลเดอร์ใหม่" }
        staging.mkdirs()
        try {
            val root = staging.canonicalPath + File.separator
            var bytes = 0L; var count = 0
            val seen = mutableSetOf<String>()
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    require(++count <= 10000) { "ชุดสำรองมีไฟล์มากเกินไป" }
                    val name = entry.name.replace('\\','/')
                    val file = File(staging,name)
                    require(!name.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(name) && file.canonicalPath.startsWith(root)) { "เส้นทางใน ZIP ไม่ถูกต้อง" }
                    require(seen.add(file.canonicalPath)) { "ไฟล์ซ้ำใน ZIP" }
                    if (!entry.isDirectory) {
                        file.parentFile!!.mkdirs()
                        file.outputStream().use { out ->
                            val buffer = ByteArray(65536)
                            var n = zip.read(buffer)
                            while(n != -1) {
                                bytes += n; require(bytes <= maxBytes) { "ชุดสำรองใหญ่เกินขนาด" }
                                out.write(buffer,0,n); n = zip.read(buffer)
                            }
                        }
                    }
                    zip.closeEntry(); entry = zip.nextEntry
                }
            }
            require(File(staging,"story.json").isFile) { "ไม่ใช่ชุดสำรอง DIRECTOR X • ไม่พบ story.json" }
        } catch (e: Exception) { staging.deleteRecursively(); throw e }
    }
}
