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
                        val automatic=spec.optBoolean("auto_pipeline")
                        val retryStatus: (String)->Unit={ message -> status("running",lastPercent,"${if(hybrid) "Groq + Gemini" else provider.label} • $message",if(message.startsWith("กำลังทำต่อ")) "requesting" else "retry_wait") }
                        val active=if(automatic) StoryProvider.eligible(this).filter { it.hasKey(this) && (spec.has("${it.id}_model") || StoryProvider.freeOnly(this)) } else if(hybrid) StoryProvider.hybridProviders else listOf(provider)
                        require(automatic || (!hybrid && provider in StoryProvider.eligible(this)) || (hybrid && StoryProvider.hybridProviders.all { it in StoryProvider.eligible(this) })) { "งานเดิมใช้ API ที่ไม่ได้เลือก • ตั้งค่า API และกดทำต่อใหม่" }
                        require(!StoryProvider.freeOnly(this) || automatic) { "โหมดฟรีรองรับงานอัตโนมัติที่ตั้งค่าด้วย OpenRouter ฟรีเท่านั้น กรุณาเริ่มหรือตั้งค่างานใหม่" }
                        if(automatic && active.isEmpty()) throw IllegalStateException("ไม่มี API ที่พร้อมในโหมดปัจจุบัน • ตรวจคีย์ OpenRouter และตั้งค่าการเขียนบทใหม่")
                        val connections=active.associateWith { target ->
                            val key=target.key(this); require(key.isNotBlank()) { "ตั้งค่า ${target.label} API Key ก่อนเริ่ม" }
                            val model=if(automatic || hybrid) spec.optString("${target.id}_model").ifBlank { target.model(this) } else spec.getString("model")
                            StoryClientFactory.create(target,key,model,{ cancelled },{ seconds -> status("running",lastPercent,if(seconds>0) "Groq • พัก $seconds วินาที • ครบเวลาแล้วทำต่ออัตโนมัติ • กดหยุดได้" else "Groq • กำลังส่งคำขอ / รอคำตอบบท",if(seconds>0) "waiting" else "requesting") },retryStatus,!hybrid && !automatic,context=this)
                        }
                        // Consent is opt-in and scoped to currently eligible, configured providers.
                        // Legacy single-provider jobs keep their original behavior unless opted in.
                        val approvedIds=getSharedPreferences("studio",MODE_PRIVATE)
                            .getStringSet("story_fallback_approved_ids",emptySet())?.toSet().orEmpty()
                        val allowedBackups=StoryProvider.eligible(this).filter {
                            it.id in approvedIds && it.hasKey(this) &&
                                (!StoryProvider.freeOnly(this) || it==StoryProvider.OPENROUTER)
                        }
                        val fallbackConnections=mutableMapOf<StoryProvider,StoryTextClient>()
                        clients=connections.values.toList()
                        fun singleProviderRequest(prompt: String,schema: JSONObject): JSONObject {
                            val primary=provider
                            val attempted=mutableSetOf(primary)
                            try {
                                return connections.getValue(primary).generate(prompt,schema)
                            } catch (error: Exception) {
                                if(cancelled || error is CancellationException) throw CancellationException()
                                val code=(error as? StoryHttpFailure)?.status
                                val backup=StoryFallbackPolicy.nextCandidate(
                                    primary=primary,
                                    allowed=StoryProvider.eligible(this).toSet(),
                                    configured=allowedBackups.toSet(),
                                    attempted=attempted,
                                    approvedForAutomaticUse=allowedBackups.toSet(),
                                    httpStatus=code,
                                    cancelled=cancelled
                                ) ?: throw error
                                if(cancelled) throw CancellationException()
                                val client=fallbackConnections.getOrPut(backup) {
                                    StoryClientFactory.create(
                                        backup,backup.key(this),backup.model(this),
                                        { cancelled },automaticRetry=false,context=this
                                    ).also { created ->
                                        // Register the in-flight client before its request starts so
                                        // the service CANCEL action can disconnect it immediately.
                                        clients=connections.values.toList()+fallbackConnections.values.toList()+created
                                    }
                                }
                                attempted.add(backup)
                                status("running",lastPercent,
                                    "${primary.label} ไม่พร้อม (HTTP $code) • ลอง API สำรอง ${backup.label} ครั้งเดียว",
                                    "requesting")
                                if(cancelled) { client.cancel(); throw CancellationException() }
                                val answer=client.generate(prompt,schema)
                                if(cancelled) throw CancellationException()
                                status("running",lastPercent,
                                    "API สำรอง ${backup.label} ตอบสำเร็จ • กลับไปใช้ API หลักในคำขอถัดไป",
                                    "requesting")
                                return answer
                            }
                        }
                        val autoRouter=AutomaticStoryRouter(folder,connections,{cancelled}) { target,message ->
                            provider=target; status("running",lastPercent,"อัตโนมัติ • ${target.label} • $message","requesting")
                        }.apply { verifyBeforeUse=true }
                        autoRouter.onFailure={ target,category -> synchronized(statusLock) {
                            val snapshot=liveStatus.recordFailure(target,category).put("hybrid",hybrid)
                            GeminiStoryFiles.write(File(folder,"status.json"),snapshot)
                        } }
                        val router=StoryProviderRouter({cancelled},{ target,fallback ->
                            provider=target
                            status("running",lastPercent,"${target.label} • "+if(fallback) "รับช่วงแทนบริการที่พักอยู่ • ใช้ข้อมูลเรื่องเดิม" else "กำลังเขียนตามหน้าที่","requesting")
                        })
                        status("running",0,if(automatic) "กำลังเขียนและจัดบทอัตโนมัติ" else if(hybrid) "Groq + Gemini • Gemini วางเรื่อง / Groq เขียนซีน • สลับเมื่อโควตาเต็ม" else "กำลังเขียนบท • ไม่ได้สร้างวิดีโอ")
                        val story=GeminiStoryEngine(folder,{ prompt,schema ->
                            status("running",lastPercent,"กำลังส่งคำขอ / รอคำตอบบท","requesting")
                            if(automatic) StoryRequestRetry().execute({cancelled},retryStatus) { autoRouter.generate(prompt,schema) }
                            else if(hybrid) StoryRequestRetry().execute({cancelled},retryStatus) {
                                router.generate(StoryProviderRouter.preferred(schema.getJSONObject("properties").has("scenes"))) { target -> connections.getValue(target).generate(prompt,schema) }
                            } else singleProviderRequest(prompt,schema)
                        },{cancelled}) { percent,message ->
                            synchronized(statusLock) { liveStatus.setTask(message) }
                            status("running",percent,message,if(message.startsWith("บันทึกแล้ว")) "saved" else "preparing")
                        }.run(spec)
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
