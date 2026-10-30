package com.pearparinya.automovie

import java.io.File

/** The requested cast reset runs once; newly imported references survive subsequent launches. */
internal object CharacterLibraryReset {
    fun install(files: File) {
        val marker=File(files,"empty_character_library_v1.installed")
        if(marker.isFile) return
        fun remove(file: File) { check(!file.exists() || file.deleteRecursively()) { "ล้างรูปตัวละครไม่สำเร็จ: ${file.name}" } }
        listOf("character_masters","master_library","supporting_masters","character_memory").forEach { remove(File(files,it)) }
        remove(File(files,"supporting_cast.json"))
        File(files,"studio").listFiles()?.filter { it.isDirectory }?.forEach { project ->
            listOf("original_masters","masters","master_candidates").forEach { remove(File(project,it)) }
            File(project,"versions").walkTopDown().filter { it.isFile && (it.name.contains(".master") || it.parentFile?.name=="master") }.toList().forEach(::remove)
        }
        marker.parentFile!!.mkdirs()
        marker.writeText("Character images cleared; new imports are preserved.")
    }
}
