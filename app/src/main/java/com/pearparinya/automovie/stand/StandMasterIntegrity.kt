package com.pearparinya.automovie.stand

import java.io.InputStream
import java.security.MessageDigest

data class StandMasterRecord(val standId: String, val sha256: String) {
    init {
        require(StandCatalog.find(standId) != null)
        require(sha256.matches(Regex("[0-9a-f]{64}")))
    }
}

object StandMasterIntegrity {
    fun digest(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) md.update(buffer, 0, count)
        }
        return md.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun verify(record: StandMasterRecord, input: InputStream): Boolean =
        digest(input) == record.sha256

    fun complete(records: List<StandMasterRecord>): Boolean =
        records.size == StandCatalog.all.size &&
            records.map { it.standId }.toSet() == StandCatalog.all.map { it.id }.toSet() &&
            records.map { it.sha256 }.distinct().size == records.size
}
