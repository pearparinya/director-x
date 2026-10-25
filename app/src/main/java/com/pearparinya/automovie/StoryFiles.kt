package com.pearparinya.automovie

import java.io.File
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** File formats shared by ChatGPT packages and Studio exports. */
internal object StoryFiles {
    data class UnpackResult(val files: List<File>, val failedEntries: List<String>)
    fun zipName(title: String): String = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(100).ifBlank { "เรื่องใหม่" } + ".zip"
    fun episodeFlow(ep: Int, title: String, scenes: List<Triple<String, String, String>>): String =
        "EP %02d • %s\nFlow / Veo 3.1 Lite • 8 วินาทีต่อซีน • 16:9\n\n".format(ep, title) +
            scenes.joinToString("\n\n--------------------\n\n") { (name, script, prompt) ->
                "$name\n$script\n\nคำสั่ง Flow (คัดลอกเฉพาะบล็อกนี้):\n$prompt"
            }
    private val separators = "[ _.-]*"
    fun sceneNumber(name: String): Pair<Int, Int>? = Regex("EP${separators}(\\d+)${separators}SC(?:ENE)?${separators}(\\d+)", RegexOption.IGNORE_CASE).find(name)?.let {
        val ep = it.groupValues[1].toIntOrNull() ?: return null
        val scene = it.groupValues[2].toIntOrNull() ?: return null
        if (ep > 0 && scene > 0) ep to scene else null
    }
    fun coverName(name: String): String? = Regex("EP${separators}(\\d+)${separators}COVER${separators}(9[xX:_-]16|16[xX:_-]9)(?:[ _-]*\\(\\d+\\))?\\.(png|jpe?g|webp)$", RegexOption.IGNORE_CASE).find(name)?.let {
        val ep = it.groupValues[1].toIntOrNull()
        val ratio = if (it.groupValues[2].startsWith("9")) "9x16" else "16x9"
        if (ep == null || ep < 1) null else "EP%02d_COVER_%s".format(ep, ratio)
    }
    fun isMaster(name: String): Boolean = Regex("^(?:CHARACTER[_ -]*\\d+|(?:GAWIN|RINLADA|MIND|SUPPORT_[A-Fa-f0-9]{8})[_ -]*MASTER|MASTER[_ -]*\\d+)(?:[_. (]|$)", RegexOption.IGNORE_CASE).containsMatchIn(name)
    fun packageName(path: String): String {
        val normalized = path.replace('\\', '/')
        val name = normalized.substringAfterLast('/')
        if (sceneNumber(name) != null || coverName(name) != null) return name
        val ep = Regex("(?:^|/)EP[ _-]*(\\d+)(?:/|$)", RegexOption.IGNORE_CASE).find(normalized.substringBeforeLast('/'))?.groupValues?.get(1) ?: return name
        return if (Regex("^(?:SC(?:ENE)?[ _-]*\\d+|COVER[ _-]*(?:9[xX:_-]16|16[xX:_-]9))", RegexOption.IGNORE_CASE).containsMatchIn(name)) "EP${ep}_$name" else name
    }
    fun unpack(input: InputStream, staging: File, maxBytes: Long = 512L * 1024 * 1024): List<File> {
        val files = mutableListOf<File>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            var count = 0
            while (entry != null) {
                require(++count <= 2000) { "ZIP มีไฟล์มากเกินไป" }
                // Flatten each entry into its own directory: archive paths cannot escape staging.
                val name = packageName(entry.name)
                if (!entry.isDirectory && !entry.name.contains("__MACOSX/") && !name.startsWith(".") && (name.endsWith(".json", true) || name.substringAfterLast('.').lowercase() in listOf("png", "jpg", "jpeg", "webp", "mp4"))) {
                    val target = File(staging, "${files.size}/$name").apply { parentFile!!.mkdirs() }
                    target.outputStream().use { out ->
                        val buffer = ByteArray(65536)
                        var n = zip.read(buffer)
                        while (n >= 0) {
                            total += n
                            require(total <= maxBytes) { "ชุดไฟล์ใหญ่เกินขนาดที่รองรับ" }
                            out.write(buffer, 0, n)
                            n = zip.read(buffer)
                        }
                    }
                    files.add(target)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return files
    }

    /** Reads a downloaded archive through its central directory and salvages valid entries. */
    fun unpackFile(archive: File, staging: File, maxBytes: Long = 512L * 1024 * 1024): UnpackResult {
        return try { unpackCentralDirectory(archive, staging, maxBytes) }
        catch (error: ZipException) {
            if (error.message?.contains("END header", true) != true) throw error
            unpackLocalEntries(archive, staging, maxBytes)
        }
    }

    private fun unpackCentralDirectory(archive: File, staging: File, maxBytes: Long): UnpackResult {
        val files = mutableListOf<File>()
        val failed = mutableListOf<String>()
        var total = 0L
        ZipFile(archive).use { zip ->
            val entries = zip.entries().toList()
            require(entries.size <= 2000) { "ZIP มีไฟล์มากเกินไป" }
            entries.forEach { entry ->
                val name = packageName(entry.name)
                if (!entry.isDirectory && !entry.name.contains("__MACOSX/") && !name.startsWith(".") && (name.endsWith(".json", true) || name.substringAfterLast('.').lowercase() in listOf("png", "jpg", "jpeg", "webp", "mp4"))) {
                    val target = File(staging, "${files.size}/$name").apply { parentFile!!.mkdirs() }
                    try {
                        zip.getInputStream(entry).use { input ->
                            target.outputStream().use { out ->
                                val buffer = ByteArray(65536)
                                var n = input.read(buffer)
                                while (n >= 0) {
                                    if (n > 0) {
                                        total += n
                                        require(total <= maxBytes) { "ชุดไฟล์ใหญ่เกินขนาดที่รองรับ" }
                                        out.write(buffer, 0, n)
                                    }
                                    n = input.read(buffer)
                                }
                            }
                        }
                        files.add(target)
                    } catch (error: Exception) {
                        target.delete()
                        if (error is IllegalArgumentException && error.message == "ชุดไฟล์ใหญ่เกินขนาดที่รองรับ") throw error
                        failed.add(entry.name)
                    }
                }
            }
        }
        return UnpackResult(files, failed)
    }

    /** Recovers local ZIP entries when the archive was delivered without its final directory. */
    private fun unpackLocalEntries(archive: File, staging: File, maxBytes: Long): UnpackResult {
        val files = mutableListOf<File>()
        val failed = mutableListOf("ส่วนปิดท้าย ZIP ไม่สมบูรณ์ • ระบบเปิดโหมดกู้ข้อมูล")
        var total = 0L
        var count = 0
        ZipInputStream(archive.inputStream()).use { zip ->
            while (true) {
                val entry = try { zip.nextEntry } catch (_: Exception) { break } ?: break
                require(++count <= 2000) { "ZIP มีไฟล์มากเกินไป" }
                val name = packageName(entry.name)
                if (!entry.isDirectory && !entry.name.contains("__MACOSX/") && !name.startsWith(".") && (name.endsWith(".json", true) || name.substringAfterLast('.').lowercase() in listOf("png", "jpg", "jpeg", "webp", "mp4"))) {
                    val target = File(staging, "${files.size}/$name").apply { parentFile!!.mkdirs() }
                    try {
                        target.outputStream().use { out ->
                            val buffer = ByteArray(65536)
                            var n = zip.read(buffer)
                            while (n >= 0) {
                                if (n > 0) {
                                    total += n
                                    require(total <= maxBytes) { "ชุดไฟล์ใหญ่เกินขนาดที่รองรับ" }
                                    out.write(buffer, 0, n)
                                }
                                n = zip.read(buffer)
                            }
                        }
                        files.add(target)
                    } catch (error: Exception) {
                        target.delete()
                        if (error is IllegalArgumentException && error.message == "ชุดไฟล์ใหญ่เกินขนาดที่รองรับ") throw error
                        failed.add("${entry.name} • อ่านไม่ครบ")
                        break
                    }
                }
                try { zip.closeEntry() } catch (_: Exception) { break }
            }
        }
        require(files.isNotEmpty()) { "ZIP ไม่มีรายการที่กู้คืนได้ กรุณารับ story.json และภาพแยกไฟล์" }
        return UnpackResult(files, failed)
    }
}
