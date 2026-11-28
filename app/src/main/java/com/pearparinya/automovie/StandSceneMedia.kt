package com.pearparinya.automovie

import com.pearparinya.automovie.stand.*
import java.io.File
import java.io.InputStream
import org.json.JSONArray

internal object StandSceneMedia {
    fun requests(ownership: StandOwnership, beats: List<StandStoryBeat>,
                 records: List<StandMasterRecord>, masters: StandMasterStore) = sequence {
        var previous: StandContinuityFrame? = null
        for (beat in beats.sortedBy { it.scene.number }) {
            val request = StandCinematicEngine.compose(ownership, beat.scene, records, masters,
                beat.scene.presences.first().ownerId, beat.narrative, emptyList(), previous = previous)
            yield(beat to request)
            previous = request.continuity
        }
    }

    fun stamp(folder: File, request: com.pearparinya.automovie.stand.StandCinematicRequest): String? = runCatching {
        val refs = request.references.map {
            val digest = EpisodeMedia.digest(it.file)
            require(digest == it.binding.masterSha256)
            digest
        }
        com.pearparinya.automovie.stand.StandMasterIntegrity.digest(
            JSONArray(listOf(request.imagePrompt,request.videoPrompt,EpisodeMedia.digest(File(folder,"frame.png"))) + refs).toString().byteInputStream())
    }.getOrNull()

    @Synchronized
    fun importMedia(folder: File, kind: String, stamp: String?, input: InputStream) {
        require(kind == "image" || kind == "video")
        folder.mkdirs()
        val temp = File.createTempFile("import-", ".tmp", folder)
        try {
            temp.outputStream().use { input.copyTo(it) }
            if (kind == "image") {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(temp.path,bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0 && kotlin.math.abs(bounds.outWidth.toDouble()/bounds.outHeight - 9.0/16) < 0.02) { "ภาพต้องเป็น 9:16" }
            } else {
                val info = EpisodeMedia.inspect(temp)
                require(info.displayWidth < info.displayHeight)
                require(stamp != null && File(folder,"approved.txt").takeIf { it.isFile }?.readText() == stamp)
            }
            val target = File(folder,if(kind == "image") "frame.png" else "clip.mp4")
            java.nio.file.Files.move(temp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            if (kind == "image") {
                File(folder,"approved.txt").delete()
                File(folder,"video_stamp.txt").delete()
            } else File(folder,"video_stamp.txt").writeText(requireNotNull(stamp) + ":" + EpisodeMedia.digest(target))
        } finally { temp.delete() }
    }

    @Synchronized
    fun approve(folder: File, request: StandCinematicRequest, reviewed: String): Boolean {
        if (stamp(folder, request) != reviewed) return false
        File(folder, "approved.txt").writeText(reviewed)
        return true
    }

    fun approved(folder: File, stamp: String?): Boolean =
        stamp != null && File(folder, "approved.txt").takeIf { it.isFile }?.readText() == stamp

    fun verifiedClip(folder: File, request: StandCinematicRequest): File {
        val stamp = stamp(folder, request)
        require(approved(folder, stamp)) { "ภาพยังไม่ผ่าน ${folder.name}" }
        val clip = File(folder, "clip.mp4")
        require(File(folder, "video_stamp.txt").readText() == stamp + ":" + EpisodeMedia.digest(clip)) {
            "วิดีโอไม่ตรงฉาก ${folder.name}"
        }
        return clip
    }
}
