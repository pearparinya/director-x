package com.pearparinya.automovie

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** Foreground export: survives switching apps; a killed process is reported as interrupted. */
class EpisodeRenderService : Service() {
    companion object {
        @Volatile var running=false
        private const val CHANNEL="episode_render"
        private const val ID=159
        const val CANCEL="cancel"
    }
    private val handler=Handler(Looper.getMainLooper())
    private var transformer: Transformer?=null
    private var wakeLock: PowerManager.WakeLock?=null
    @Volatile private var cancelled=false
    private lateinit var job: File
    private lateinit var spec: JSONObject
    private lateinit var output: File
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action==CANCEL) { if(::job.isInitialized) { if(intent.getStringExtra("job")==job.path) cancel() } else stopSelf(); return START_NOT_STICKY }
        if(running || intent==null) { if(!running) stopSelf(); return START_NOT_STICKY }
        val path=intent.getStringExtra("job") ?: return START_NOT_STICKY
        job=File(path)
        require(job.canonicalPath.startsWith(filesDir.canonicalPath+File.separator))
        spec=JSONObject(File(job,"spec.json").readText()); output=File(job,"output.mp4")
        running=true; cancelled=false
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"รวมหนัง EP",NotificationManager.IMPORTANCE_LOW))
        val type=if(Build.VERSION.SDK_INT>=35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else if(Build.VERSION.SDK_INT>=29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        if(Build.VERSION.SDK_INT>=29) startForeground(ID,notification("กำลังเตรียมคลิป",0),type) else startForeground(ID,notification("กำลังเตรียมคลิป",0))
        wakeLock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"DIRECTORX:EpisodeRender").apply { acquire(30*60*1000L) }
        writeStatus("preparing",0,"กำลังเตรียมคลิป")
        Thread {
            try {
                val clips=spec.getJSONArray("clips")
                val copy=JSONArray()
                for(i in 0 until clips.length()) {
                    check(!cancelled); val src=File(clips.getString(i)); val dst=File(job,"inputs/$i.mp4").apply { parentFile!!.mkdirs() }; src.copyTo(dst)
                    copy.put(dst.path)
                }
                val cover=File(job,"cover.png"); File(spec.getString("cover")).copyTo(cover)
                spec.optJSONArray("input_hashes")?.let { hashes ->
                    require(hashes.length()==copy.length()+1) { "รายการตรวจไฟล์ไม่ครบ" }
                    require(EpisodeMedia.digest(cover)==hashes.getString(0)) { "ปกเปลี่ยนระหว่างเตรียมงาน กรุณารวมใหม่" }
                    for(i in 0 until copy.length()) require(EpisodeMedia.digest(File(copy.getString(i)))==hashes.getString(i+1)) { "คลิปเปลี่ยนระหว่างเตรียมงาน กรุณารวมใหม่" }
                }
                spec.put("copies",copy)
                handler.post { if(!cancelled) runExport(cover,copy) }
            } catch(e: Exception) { handler.post { fail(e) } }
        }.start()
        return START_NOT_STICKY
    }
    private fun notification(text: String, progress: Int): Notification {
        val cancel=PendingIntent.getService(this,1,Intent(this,EpisodeRenderService::class.java).setAction(CANCEL).putExtra("job",job.path),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open=PendingIntent.getActivity(this,0,Intent(this,StudioActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("DIRECTOR X • EP ${spec.optInt("ep")}").setContentText(text).setContentIntent(open).setOngoing(true).setProgress(100,progress,progress==0).addAction(Notification.Action.Builder(null,"ยกเลิก",cancel).build()).build()
    }
    private fun writeStatus(state: String, progress: Int, message: String) {
        val status=JSONObject().put("state",state).put("progress",progress).put("message",message).put("time",System.currentTimeMillis()).put("output",output.path)
        val temp=File(job,"status.pending"); temp.writeText(status.toString()); temp.renameTo(File(job,"status.json"))
        getSystemService(NotificationManager::class.java).notify(ID,notification(message,progress))
    }
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun runExport(cover: File, clips: JSONArray) {
        try {
            val fps=spec.getInt("fps"); val bitrate=spec.getInt("bitrate")
            val effect=Presentation.createForWidthAndHeight(EpisodeRules.WIDTH,EpisodeRules.HEIGHT,if(spec.optBoolean("crop")) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT)
            val intro=EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(cover)).setImageDurationMs(3000).build()).setFrameRate(fps).setEffects(Effects(emptyList(),listOf(effect))).build()
            val lossless=spec.optBoolean("lossless")
            val items=if(lossless) listOf(intro) else listOf(intro)+(0 until clips.length()).map { i -> EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(File(clips.getString(i))))).setEffects(Effects(emptyList(),listOf(effect))).build() }
            val composition=Composition.Builder(EditedMediaItemSequence.Builder(items).build()).experimentalSetForceAudioTrack(true).build()
            val encoder=DefaultEncoderFactory.Builder(this).setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build()).setEnableFallback(false).build()
            val target=if(lossless) File(job,"intro.mp4") else output
            writeStatus("rendering",0,if(lossless) "กำลังสร้างปกเปิด ก่อนตรวจรูปแบบต่อคลิป" else "กำลังเข้ารหัสคุณภาพสูง 1920 × 1080")
            transformer=Transformer.Builder(this).setPortraitEncodingEnabled(false).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).setEncoderFactory(encoder).addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    transformer=null
                    if(cancelled) return
                    Thread {
                        try {
                            if(lossless) EpisodeMedia.remux(listOf(target)+(0 until clips.length()).map { File(clips.getString(it)) },output,{cancelled}) { value -> handler.post { if(!cancelled) writeStatus("rendering",value,"ต่อคลิปโดยไม่เข้ารหัสใหม่") } }
                            check(!cancelled)
                            val info=EpisodeMedia.inspect(output)
                            require(info.width==EpisodeRules.WIDTH && info.height==EpisodeRules.HEIGHT && info.rotation==0) { "ผลลัพธ์ไม่ใช่ 1920 × 1080" }
                            require(kotlin.math.abs(info.durationMs-spec.getLong("duration_ms"))<1500) { "ความยาวไฟล์ไม่ตรงกับ EP" }
                            if(spec.optBoolean("has_audio")) require(info.audioMime!=null) { "ไม่พบเสียงในผลลัพธ์" }
                            val fingerprint=spec.getString("fingerprint")
                            File(job,"result.json").writeText(JSONObject().put("filename",spec.getString("filename")).put("fingerprint",fingerprint).put("duration_ms",info.durationMs).put("width",EpisodeRules.WIDTH).put("height",EpisodeRules.HEIGHT).put("created",System.currentTimeMillis()).toString())
                            handler.post { if(!cancelled) { writeStatus("complete",100,"รวมสำเร็จ • ยังไม่ได้ดาวน์โหลดลงตำแหน่งที่เลือก"); finish() } }
                        } catch(e: Exception) { handler.post { fail(e) } }
                    }.start()
                }
                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) { transformer=null; fail(exception) }
            }).build()
            transformer!!.start(composition,target.path)
            handler.post(object : Runnable { override fun run() {
                val t=transformer ?: return; val progress=ProgressHolder()
                val value=if(t.getProgress(progress)==Transformer.PROGRESS_STATE_AVAILABLE) progress.progress else 0
                if(!cancelled) { writeStatus("rendering",value,if(lossless) "กำลังสร้างปกเปิด" else "กำลังรวมหนังคุณภาพสูง"); handler.postDelayed(this,1000) }
            } })
        } catch(e: Exception) { fail(e) }
    }
    private fun cancel() { cancelled=true; transformer?.cancel(); transformer=null; if(::job.isInitialized) { writeStatus("cancelled",0,"ยกเลิกแล้ว • ไฟล์ต้นฉบับยังอยู่"); output.delete() }; finish() }
    private fun fail(e: Exception) { if(cancelled) return; transformer?.cancel(); transformer=null; output.delete(); writeStatus("failed",0,e.message ?: "รวมไม่สำเร็จ"); finish() }
    private fun finish() { if(wakeLock?.isHeld==true) wakeLock?.release(); wakeLock=null; running=false; if(::job.isInitialized) { File(job,"inputs").deleteRecursively(); File(job,"intro.mp4").delete() }; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { if(wakeLock?.isHeld==true) wakeLock?.release(); wakeLock=null; transformer?.cancel(); transformer=null; running=false; handler.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onTimeout(startId: Int, fgsType: Int) { fail(IllegalStateException("Android หยุดงานเพราะใช้เวลานานเกินไป กรุณารวมใหม่")) }
}
