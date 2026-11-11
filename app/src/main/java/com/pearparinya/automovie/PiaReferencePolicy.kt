package com.pearparinya.automovie

import java.io.File

/** Keep the presenter's identity MASTER independent from the chosen outfit. */
internal object PiaReferencePolicy {
    fun capture(original: File, outfit: File?, masterTarget: File, outfitTarget: File) {
        require(original.isFile) { "ไม่พบ MASTER ต้นฉบับ" }
        require(original.canonicalFile != masterTarget.canonicalFile && original.canonicalFile != outfitTarget.canonicalFile && masterTarget.canonicalFile != outfitTarget.canonicalFile) { "ไฟล์อ้างอิงซ้ำกัน" }
        if(outfit!=null) require(outfit.isFile && outfit.canonicalFile!=outfitTarget.canonicalFile) { "ไม่พบภาพชุดที่เลือก" }
        masterTarget.parentFile!!.mkdirs()
        original.copyTo(masterTarget,overwrite=true)
        if(outfit!=null) { outfitTarget.parentFile!!.mkdirs(); outfit.copyTo(outfitTarget,overwrite=true) }
    }
    fun outfitInitiallySelected(taskId: String): Boolean = taskId.contains("_COVER_") || taskId=="EP01_SC001"
}
