package com.pearparinya.automovie.stand

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class StandMasterStore(filesDir: File, records: List<StandMasterRecord>) {
    private val root = File(filesDir, "stand_masters_v1")
    private val registry = records.associateBy { it.standId }
    init { require(StandMasterIntegrity.complete(records)) }

    fun importOriginal(id: String, source: InputStream): Boolean {
        val record = requireNotNull(registry[id])
        check(root.isDirectory || root.mkdirs())
        val temp = File.createTempFile("master-", ".tmp", root)
        try {
            temp.outputStream().use { source.copyTo(it) }
            if (!temp.inputStream().use { StandMasterIntegrity.verify(record, it) }) return false
            Files.move(temp.toPath(), File(root, id + ".master").toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            return verifiedFile(id) != null
        } finally { temp.delete() }
    }

    fun verifiedFile(id: String): File? {
        val record = requireNotNull(registry[id])
        val file = File(root, id + ".master")
        return file.takeIf { it.isFile && it.inputStream().use { stream ->
            StandMasterIntegrity.verify(record, stream)
        } }
    }

    fun ready(): Boolean = registry.keys.all { verifiedFile(it) != null }
}
