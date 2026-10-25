package com.pearparinya.automovie

import java.io.File
import java.util.UUID
import org.json.JSONObject

/** Apply the user-selected cast revision once; retain original files and generated media. */
internal object CharacterMasterUpgrade {
    const val REVISION = "cast_20261017_1740"
    const val FEMALE_REVISION = "female_cast_20261017_1854"
    const val LATEST_FEMALE_REVISION = "female_cast_20261017_1953"
    const val MAY_REVISION = "may_cast_20261017_2013"
    const val FEMALE_2159_REVISION = "female_cast_20261017_2159"
    const val CURRENT_REVISION = "cast_20261025_3_new_masters"
    val ids = listOf("gawin", "rinlada", "mind")
    fun install(files: File, load: (String) -> ByteArray) {
        applyRevision(files,load,REVISION,ids)
        applyRevision(files,load,FEMALE_REVISION,listOf("rinlada","mind"))
        applyRevision(files,load,LATEST_FEMALE_REVISION,listOf("rinlada","mind"))
        applyRevision(files,load,MAY_REVISION,listOf("mind"))
        applyRevision(files,load,FEMALE_2159_REVISION,listOf("rinlada","mind"))
        applyRevision(files,load,CURRENT_REVISION,ids,includePending=true)
    }
    private fun applyRevision(files: File, load: (String) -> ByteArray, revision: String, changedIds: List<String>,includePending: Boolean=false) {
        val sources=changedIds.associateWith { load("character_masters/${it.uppercase()}_MASTER.png") }
        require(sources.values.all { it.isNotEmpty() }) { "ขาดภาพ MASTER" }
        val folder=File(files,"character_masters").apply { mkdirs() }
        val marker=File(folder,"$revision.installed")
        if(!marker.exists()) {
            sources.forEach { (id,bytes) -> replace(File(folder,"$id.master"),bytes,File(folder,"versions/$revision")) }
        } else sources.forEach { (id,bytes) ->
            val file=File(folder,"$id.master"); if(!file.exists()) replace(file,bytes,File(folder,"versions/$revision"))
        }
        if(marker.exists()) return
        File(files,"studio").listFiles()?.filter { it.isDirectory }?.forEach { project ->
            val story=File(project,"story.json"); val done=File(project,"$revision.installed")
            if(done.exists()) return@forEach
            if(!story.exists()) {
                if(includePending && File(project,"gemini_job/spec.json").isFile) {
                    sources.forEach { (id,bytes) -> replace(File(project,"original_masters/$id.master"),bytes,File(project,"versions/$revision")) }
                    done.writeText("installed")
                }
                return@forEach
            }
            val plan=runCatching { JSONObject(story.readText()) }.getOrNull() ?: return@forEach
            if(plan.optString("character_mode","original")!="original") return@forEach
            sources.forEach { (id,bytes) -> replace(File(project,"original_masters/$id.master"),bytes,File(project,"versions/$revision")) }
            val episodes=plan.optJSONArray("episodes")
            if(episodes!=null) for(e in 0 until episodes.length()) {
                val scenes=episodes.getJSONObject(e).optJSONArray("scenes") ?: continue
                for(i in 0 until scenes.length()) {
                    val scene=scenes.getJSONObject(i); val cast=scene.optJSONArray("characters") ?: continue
                    if((0 until cast.length()).any { StoryIdentity.keyFor(cast.getJSONObject(it).optString("name")) in changedIds }) {
                        scene.put("user_review","needs_revision"); scene.remove("flow_image_revision")
                    }
                }
            }
            replace(story,plan.toString(2).toByteArray(),File(project,"versions/$revision"))
            done.writeText("installed")
        }
        marker.writeText("installed")
    }
    private fun replace(target: File, bytes: ByteArray, backup: File) {
        if(target.exists() && target.readBytes().contentEquals(bytes)) return
        target.parentFile!!.mkdirs()
        if(target.exists()) { backup.mkdirs(); target.copyTo(File(backup,"${target.name}_${UUID.randomUUID()}")) }
        val temp=File(target.parentFile,target.name+".upgrade.pending")
        temp.writeBytes(bytes); check(temp.renameTo(target)) { "บันทึก ${target.name} ไม่สำเร็จ" }
    }
}
