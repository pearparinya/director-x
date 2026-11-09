package com.pearparinya.automovie

import java.io.File
import java.util.UUID

/** Keep reviewed crops keyed by original image bytes; never overwrite either original or prior crops. */
internal class FaceReferenceStore(private val root: File) {
    private fun directory(master: File): File = File(root,MasterLibrary.digest(master.readBytes()))
    fun latest(master: File): File? = directory(master).listFiles().orEmpty()
        .filter { it.isFile && it.extension.equals("png",true) }
        .maxWithOrNull(compareBy<File> { it.lastModified() }.thenBy { it.name })
    fun newFile(master: File): File = File(directory(master),"${UUID.randomUUID()}.png")
}
