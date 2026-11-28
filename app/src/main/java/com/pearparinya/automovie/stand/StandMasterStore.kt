package com.pearparinya.automovie.stand

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

enum class StandMasterStatus { VERIFIED, MISSING, HASH_MISMATCH, UNREADABLE }
data class StandMasterAudit(val standId: String, val expectedSha256: String, val actualSha256: String?, val status: StandMasterStatus)

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

    fun audit(): List<StandMasterAudit> = StandCatalog.all.map { stand ->
        val record = registry.getValue(stand.id)
        val file = File(root, stand.id + ".master")
        if (!file.isFile) StandMasterAudit(stand.id, record.sha256, null, StandMasterStatus.MISSING)
        else try {
            val actual = file.inputStream().use { StandMasterIntegrity.digest(it) }
            StandMasterAudit(stand.id, record.sha256, actual,
                if (actual == record.sha256) StandMasterStatus.VERIFIED else StandMasterStatus.HASH_MISMATCH)
        } catch (_: java.io.IOException) {
            StandMasterAudit(stand.id, record.sha256, null, StandMasterStatus.UNREADABLE)
        }
    }

    fun ready(): Boolean = audit().all { it.status == StandMasterStatus.VERIFIED }
}
