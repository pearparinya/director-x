package com.pearparinya.automovie

import java.io.File

/** Reject changed, missing or colliding references before sharing their bytes. */
internal object ReferenceSnapshot {
    fun capture(references: List<Pair<String,File>>): Map<String,String> {
        require(references.map { it.first.lowercase() }.distinct().size==references.size) { "รหัสภาพอ้างอิงซ้ำ กรุณาตรวจรายการ" }
        return references.associate { (name,file) ->
            require(file.isFile) { "ไม่พบภาพอ้างอิง $name" }
            name to MasterLibrary.digest(file.readBytes())
        }
    }
    fun copyVerified(source: File,destination: File,expected: String) {
        require(source.canonicalFile!=destination.canonicalFile) { "ไฟล์ต้นฉบับและไฟล์ส่งต้องไม่ซ้ำกัน" }
        try {
            require(source.isFile) { "ไม่พบภาพอ้างอิง" }
            destination.parentFile!!.mkdirs()
            source.copyTo(destination,overwrite=true)
            require(MasterLibrary.digest(destination.readBytes())==expected) { "ภาพอ้างอิงเปลี่ยนระหว่างเตรียมส่ง กรุณาตรวจใหม่" }
        } catch(e: Exception) { destination.delete(); throw e }
    }
}
