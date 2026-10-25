package com.pearparinya.automovie

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaCodec
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

internal object EpisodeMedia {
    data class Info(val file: File, val width: Int, val height: Int, val durationMs: Long, val fps: Float, val videoMime: String, val audioMime: String?, val rotation: Int, val bitrate: Int) {
        val displayWidth: Int get() = if(rotation % 180 != 0) height else width
        val displayHeight: Int get() = if(rotation % 180 != 0) width else height
    }
    fun inspect(file: File): Info {
        require(file.isFile && file.length()>0) { "ไม่พบคลิป ${file.name}" }
        val extractor=MediaExtractor(); val metadata=MediaMetadataRetriever()
        try {
            extractor.setDataSource(file.path); metadata.setDataSource(file.path)
            val video=(0 until extractor.trackCount).map { extractor.getTrackFormat(it) }.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true } ?: error("ไม่มีภาพวิดีโอ: ${file.name}")
            val audio=(0 until extractor.trackCount).map { extractor.getTrackFormat(it) }.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true }
            val duration=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            require(duration>0) { "คลิปเล่นไม่ได้: ${file.name}" }
            val fps=runCatching { video.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }.getOrElse { runCatching { video.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrDefault(30f) }
            return Info(file,video.getInteger(MediaFormat.KEY_WIDTH),video.getInteger(MediaFormat.KEY_HEIGHT),duration,fps.coerceIn(1f,120f),video.getString(MediaFormat.KEY_MIME)!!,audio?.getString(MediaFormat.KEY_MIME),metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0)
        } finally { extractor.release(); metadata.release() }
    }
    fun digest(file: File): String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer=ByteArray(65536); var n=input.read(buffer); while(n != -1) { digest.update(buffer,0,n); n=input.read(buffer) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun fingerprint(format: MediaFormat): String {
        val keys=listOf(MediaFormat.KEY_MIME,MediaFormat.KEY_WIDTH,MediaFormat.KEY_HEIGHT,MediaFormat.KEY_SAMPLE_RATE,MediaFormat.KEY_CHANNEL_COUNT,"profile","level","color-standard","color-transfer","color-range")
        val values=keys.joinToString("|") { "$it=${if(format.containsKey(it)) format.getStringOrNumber(it) else ""}" }
        val config=(0..2).joinToString("|") { index -> val key="csd-$index"; val buffer=if(format.containsKey(key)) format.getByteBuffer(key)?.duplicate() else null
            if(buffer==null) "" else ByteArray(buffer.remaining()).also { buffer.get(it) }.joinToString("") { "%02x".format(it) }
        }
        return "$values|$config"
    }
    private fun MediaFormat.getStringOrNumber(key: String): String = runCatching { getString(key).orEmpty() }.getOrElse { runCatching { getInteger(key).toString() }.getOrDefault("") }
    /** Strict packet copy only. Never silently falls back to encoding. */
    fun remux(files: List<File>, output: File, cancelled: () -> Boolean, progress: (Int) -> Unit) {
        require(files.isNotEmpty())
        val infos=files.map { inspect(it) }
        require(infos.all { it.displayWidth==EpisodeRules.WIDTH && it.displayHeight==EpisodeRules.HEIGHT && it.rotation in listOf(0,90,180,270) && it.rotation==infos.first().rotation && kotlin.math.abs(it.fps-infos.first().fps)<0.1f }) { "ขนาด/เฟรมเรตไม่ตรงกัน เลือกโหมดปรับไฟล์คุณภาพสูง" }
        val formats=files.map { file -> val e=MediaExtractor(); try { e.setDataSource(file.path); (0 until e.trackCount).map { e.getTrackFormat(it) }.filter { it.getString(MediaFormat.KEY_MIME)?.let { m -> m.startsWith("video/") || m.startsWith("audio/") }==true }.sortedBy { it.getString(MediaFormat.KEY_MIME) } } finally { e.release() } }
        val signatures=formats.map { tracks -> tracks.map { fingerprint(it) } }
        require(signatures.all { it==signatures.first() }) { "ปกเปิดหรือคลิปใช้ codec/config/เสียงต่างกัน ต่อแบบไม่เข้ารหัสใหม่ไม่ได้ กรุณาเลือกปรับไฟล์คุณภาพสูง" }
        val muxer=MediaMuxer(output.path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started=false
        try {
            muxer.setOrientationHint(infos.first().rotation)
            val target=formats.first().map { muxer.addTrack(it) }; muxer.start(); started=true
            var offsetUs=0L; val totalMs=infos.sumOf { it.durationMs }
            val buffer=ByteBuffer.allocateDirect(32*1024*1024)
            val lastPts=LongArray(target.size) { -1L }
            files.forEachIndexed { index,file ->
                val e=MediaExtractor()
                try {
                    e.setDataSource(file.path)
                    val tracks=(0 until e.trackCount).filter { e.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.let { m -> m.startsWith("video/") || m.startsWith("audio/") }==true }.sortedBy { e.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
                    val origin=tracks.map { track -> e.selectTrack(track); e.seekTo(0,MediaExtractor.SEEK_TO_PREVIOUS_SYNC); val first=e.sampleTime; e.unselectTrack(track); first }.filter { it>=0 }.minOrNull() ?: 0L
                    tracks.forEachIndexed { slot,track ->
                        e.selectTrack(track); e.seekTo(0,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                        var first=-1L
                        while(true) {
                            check(!cancelled()) { "ยกเลิกการรวมแล้ว" }; buffer.clear()
                            val size=e.readSampleData(buffer,0); if(size<0) break
                            val pts=e.sampleTime; if(first<0) first=pts
                            val adjusted=offsetUs+pts-origin
                            require(adjusted>lastPts[slot]) { "เวลาคลิปไม่เรียงแบบที่รองรับ ใช้โหมดปรับไฟล์คุณภาพสูง" }; lastPts[slot]=adjusted
                            val info=MediaCodec.BufferInfo().apply { set(0,size,adjusted,if(e.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0) }
                            muxer.writeSampleData(target[slot],buffer,info); e.advance()
                        }
                        e.unselectTrack(track)
                    }
                } finally { e.release() }
                offsetUs+=infos[index].durationMs*1000
                progress((offsetUs/10/totalMs).toInt().coerceIn(0,99))
            }
        } finally { if(started) runCatching { muxer.stop() }; muxer.release() }
    }
}
