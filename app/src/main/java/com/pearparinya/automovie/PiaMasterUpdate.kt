package com.pearparinya.automovie

import java.io.File

/** One-time replacement of the presenter identity; existing projects retain their snapshots. */
internal object PiaMasterUpdate {
    fun install(target: File, image: ByteArray) {
        require(image.isNotEmpty())
        val parent = requireNotNull(target.parentFile)
        check(parent.exists() || parent.mkdirs())
        val backup = File(parent, "pia_identity_before_431.image")
        if (target.isFile && !backup.exists()) target.copyTo(backup, overwrite = false)
        val pending = File(parent, "pia_identity_431.pending")
        try {
            pending.writeBytes(image)
            check(pending.renameTo(target)) { "บันทึก MASTER นายเปียไม่สำเร็จ" }
        } finally { pending.delete() }
    }
}
