package com.pearparinya.automovie

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CancellationException

/** Foreground text generation; validated chunks survive a network error or process interruption. */
class GeminiStoryService : Service() {
    companion object {
        @Volatile var running=false
        @Volatile var projectId: String?=null
        const val CANCEL="cancel_gemini"
        private const val CHANNEL="gemini_story"
        private const val ID=160
    }
    @Volatile private var cancelled=false
    private var client: GeminiClient?=null
    private var worker: Thread?=null
    private lateinit var folder: File
    private var lastPercent=0
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action==CANCEL) { cancelled=true; client?.cancel(); return START_NOT_STICKY }
        if(running || worker?.isAlive==true) return START_NOT_STICKY
        val id=intent?.getStringExtra("project") ?: run { stopSelf(); return START_NOT_STICKY }
        if(!Regex("[a-zA-Z0-9-]+").matches(id)) { stopSelf(); return START_NOT_STICKY }
        folder=File(filesDir,"studio/$id/gemini_job")
        lastPercent=GeminiStoryEngine.checkpointPercent(folder)
        running=true; projectId=id; cancelled=false
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"เขียนบทด้วย Gemini",NotificationManager.IMPORTANCE_LOW))
        val notification=notification("กำลังเริ่มเขียนบท")
        if(Build.VERSION.SDK_INT>=29) startForeground(ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID,notification)
        worker=Thread {
            try {
                java.io.FileOutputStream(File(folder,"run.lock").apply { parentFile!!.mkdirs() },true).channel.use { channel ->
                    val lock=channel.tryLock() ?: error("งานนี้กำลังทำงานอยู่")
                    try {
                        val spec=GeminiStoryFiles.read(File(folder,"spec.json")); val settings=GeminiSettings(this)
                        val key=settings.key(); require(key.isNotBlank()) { "ตั้งค่า Gemini API Key ก่อนเริ่ม" }
                        client=GeminiClient(key,spec.getString("model")) { cancelled }
                        status("running",0,"กำลังเขียนบท • ไม่ได้สร้างวิดีโอ")
                        val story=GeminiStoryEngine(folder,{ prompt,schema -> client!!.generate(prompt,schema) },{cancelled}) { percent,message -> status("running",percent,message) }.run(spec)
                        if(cancelled) throw CancellationException()
                        val target=File(folder.parentFile,"story.json")
                        if(target.exists()) require(GeminiStoryFiles.read(target).toString()==story.toString()) { "พบ story.json ที่แก้ไขแล้ว ไม่เขียนทับ" }
                        else GeminiStoryFiles.write(target,story)
                        status("complete",100,"บทพร้อมแล้ว ${story.getInt("episode_count")} EP • ${story.getInt("scene_count")} ซีน • ส่งออก Flow ได้ทันที")
                    } finally { lock.release() }
                }
            } catch(e: Exception) {
                if(::folder.isInitialized) status(if(cancelled || e is CancellationException) "paused" else "failed",lastPercent,
                    if(cancelled || e is CancellationException) "หยุดแล้ว • บันทึกช่วงที่เสร็จไว้ กดทำต่อได้" else safeError(e))
            } finally {
                client=null; running=false; projectId=null
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            }
        }.apply { name="director-gemini-writer"; start() }
        return START_NOT_STICKY
    }
    private fun safeError(e: Exception): String = when(e) {
        is java.net.SocketTimeoutException -> "เชื่อมต่อ Gemini หมดเวลา งานที่เสร็จบันทึกไว้ กดทำต่อได้"
        is java.io.IOException -> "เชื่อมต่อ Gemini ไม่สำเร็จ ตรวจอินเทอร์เน็ตแล้วกดทำต่อ"
        else -> (e.message ?: "เขียนบทไม่สำเร็จ").take(350)
    }
    private fun status(state: String,percent: Int,message: String) {
        lastPercent=if(state=="complete") 100 else maxOf(lastPercent,percent.coerceIn(0,99))
        GeminiStoryFiles.write(File(folder,"status.json"),JSONObject().put("state",state).put("percent",lastPercent).put("message",message).put("updated",System.currentTimeMillis()))
        getSystemService(NotificationManager::class.java).notify(ID,notification(message))
    }
    private fun notification(message: String): Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,StudioActivity::class.java).putExtra("gemini_project",projectId),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,GeminiStoryService::class.java).setAction(CANCEL),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.studio_movie).setContentTitle("DIRECTOR X • เขียนบท")
            .setContentText(message).setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,"หยุด",stop).build()).build()
    }
    override fun onTimeout(startId: Int,fgsType: Int) { cancelled=true; client?.cancel(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { cancelled=true; client?.cancel(); super.onDestroy() }
}
