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
    @Volatile private var clients: List<StoryTextClient> = emptyList()
    private var worker: Thread?=null
    private val statusLock=Any()
    private lateinit var liveStatus: StoryJobStatus
    private var pulse: java.util.concurrent.ScheduledExecutorService?=null
    private var hybrid=false
    private lateinit var folder: File
    private var lastPercent=0
    private var provider=StoryProvider.GEMINI
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action==CANCEL) { cancelled=true; clients.forEach { it.cancel() }; return START_NOT_STICKY }
        if(running || worker?.isAlive==true) return START_NOT_STICKY
        val id=intent?.getStringExtra("project") ?: run { stopSelf(); return START_NOT_STICKY }
        if(!Regex("[a-zA-Z0-9-]+").matches(id)) { stopSelf(); return START_NOT_STICKY }
        folder=File(filesDir,"studio/$id/gemini_job")
        lastPercent=GeminiStoryEngine.checkpointPercent(folder)
        liveStatus=StoryJobStatus(runCatching { GeminiStoryFiles.read(File(folder,"status.json")) }.getOrNull(),lastPercent)
        running=true; projectId=id; cancelled=false
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"เขียนบทด้วย AI",NotificationManager.IMPORTANCE_LOW))
        val notification=notification("กำลังเริ่มเขียนบท")
        if(Build.VERSION.SDK_INT>=29) startForeground(ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID,notification)
        status("running",lastPercent,"กำลังเริ่ม / ต่อจากช่วงที่บันทึกไว้","preparing")
        pulse=java.util.concurrent.Executors.newSingleThreadScheduledExecutor().apply {
            scheduleAtFixedRate({ runCatching { synchronized(statusLock) {
                if(running) {
                    val snapshot=liveStatus.heartbeat().put("hybrid",hybrid)
                    GeminiStoryFiles.write(File(folder,"status.json"),snapshot)
                    getSystemService(NotificationManager::class.java).notify(ID,notification(snapshot.optString("message")))
                }
            } } },1,1,java.util.concurrent.TimeUnit.SECONDS)
        }
        worker=Thread {
            try {
                java.io.FileOutputStream(File(folder,"run.lock").apply { parentFile!!.mkdirs() },true).channel.use { channel ->
                    val lock=channel.tryLock() ?: error("งานนี้กำลังทำงานอยู่")
                    try {
                        val spec=GeminiStoryFiles.read(File(folder,"spec.json"))
                        provider=StoryProvider.from(spec.optString("provider","gemini"))
                        hybrid=spec.optBoolean("hybrid")
                        val retryStatus: (String)->Unit={ message -> status("running",lastPercent,"${if(hybrid) "Groq + Gemini" else provider.label} • $message",if(message.startsWith("กำลังทำต่อ")) "requesting" else "retry_wait") }
                        val active=if(hybrid) StoryProvider.entries.toList() else listOf(provider)
                        val connections=active.associateWith { target ->
                            val settings=GeminiSettings(this,target)
                            val key=settings.key(); require(key.isNotBlank()) { "ตั้งค่า ${target.label} API Key ก่อนเริ่ม" }
                            val model=if(hybrid) spec.getString("${target.id}_model") else spec.getString("model")
                            if(target==StoryProvider.GROQ) GroqClient(key,model,{ cancelled },{ seconds -> status("running",lastPercent,if(seconds>0) "Groq • พัก $seconds วินาที • ครบเวลาแล้วทำต่ออัตโนมัติ • กดหยุดได้" else "Groq • กำลังส่งคำขอ / รอคำตอบบท",if(seconds>0) "waiting" else "requesting") },retryStatus,!hybrid)
                            else GeminiClient(key,model,{ cancelled },retryStatus,!hybrid)
                        }
                        clients=connections.values.toList()
                        val router=StoryProviderRouter({cancelled},{ target,fallback ->
                            provider=target
                            status("running",lastPercent,"${target.label} • "+if(fallback) "รับช่วงแทนบริการที่พักอยู่ • ใช้ข้อมูลเรื่องเดิม" else "กำลังเขียนตามหน้าที่","requesting")
                        })
                        status("running",0,if(hybrid) "Groq + Gemini • Gemini วางเรื่อง / Groq เขียนซีน • สลับเมื่อโควตาเต็ม" else "กำลังเขียนบท • ไม่ได้สร้างวิดีโอ")
                        val story=GeminiStoryEngine(folder,{ prompt,schema ->
                            status("running",lastPercent,"กำลังส่งคำขอ / รอคำตอบบท","requesting")
                            if(hybrid) StoryRequestRetry().execute({cancelled},retryStatus) {
                                router.generate(StoryProviderRouter.preferred(schema.getJSONObject("properties").has("scenes"))) { target -> connections.getValue(target).generate(prompt,schema) }
                            } else connections.getValue(provider).generate(prompt,schema)
                        },{cancelled}) { percent,message -> status("running",percent,message,if(message.startsWith("บันทึกแล้ว")) "saved" else "preparing") }.run(spec)
                        if(cancelled) throw CancellationException()
                        val target=File(folder.parentFile,"story.json")
                        if(target.exists()) require(ChannelCatalog.updateLegacyChannel(GeminiStoryFiles.read(target)).toString()==story.toString()) { "พบ story.json ที่แก้ไขแล้ว ไม่เขียนทับ" }
                        else GeminiStoryFiles.write(target,story)
                        status("complete",100,"บทพร้อมแล้ว ${story.getInt("episode_count")} EP • ${story.getInt("scene_count")} ซีน • ส่งออก Flow ได้ทันที")
                    } finally { lock.release() }
                }
            } catch(e: Exception) {
                if(::folder.isInitialized) status(if(e is AutoDirectorContent.AwaitingOutline) "awaiting_outline" else if(cancelled || e is CancellationException) "paused" else "failed",lastPercent,
                    if(e is AutoDirectorContent.AwaitingOutline) e.message.orEmpty() else if(cancelled || e is CancellationException) "หยุดแล้ว • บันทึกช่วงที่เสร็จไว้ กดทำต่อได้" else safeError(e))
            } finally {
                synchronized(statusLock) { running=false; pulse?.shutdownNow(); pulse=null }
                clients=emptyList(); projectId=null
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            }
        }.apply { name="director-gemini-writer"; start() }
        return START_NOT_STICKY
    }
    private fun safeError(e: Exception): String = when(e) {
        is java.net.SocketTimeoutException -> "เชื่อมต่อ ${provider.label} หมดเวลา งานที่เสร็จบันทึกไว้ กดทำต่อได้"
        is java.io.IOException -> "เชื่อมต่อ ${provider.label} ไม่สำเร็จ ตรวจอินเทอร์เน็ตแล้วกดทำต่อ"
        else -> (e.message ?: "เขียนบทไม่สำเร็จ").take(350)
    }
    private fun status(state: String,percent: Int,message: String,phase: String=state) {
        synchronized(statusLock) {
            lastPercent=if(state=="complete") 100 else maxOf(lastPercent,percent.coerceIn(0,99))
            val snapshot=liveStatus.update(state,lastPercent,message,provider.id,phase).put("hybrid",hybrid)
            GeminiStoryFiles.write(File(folder,"status.json"),snapshot)
            getSystemService(NotificationManager::class.java).notify(ID,notification(message))
        }
    }
    private fun notification(message: String): Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,StudioActivity::class.java).putExtra("gemini_project",projectId),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,GeminiStoryService::class.java).setAction(CANCEL),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.studio_movie).setContentTitle("DIRECTOR X • เขียนบท")
            .setContentText(message).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setProgress(100,lastPercent,false).addAction(Notification.Action.Builder(null,"หยุด",stop).build()).build()
    }
    override fun onTimeout(startId: Int,fgsType: Int) { cancelled=true; clients.forEach { it.cancel() }; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { pulse?.shutdownNow(); pulse=null; cancelled=true; clients.forEach { it.cancel() }; super.onDestroy() }
}
