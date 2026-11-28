package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** Paired providers work on the same validated chunk; incomplete writer results are resumable. */
internal class AutomaticStoryRouter(
    private val folder: File,
    private val clients: Map<StoryProvider,StoryTextClient>,
    private val cancelled: () -> Boolean,
    private val progress: (StoryProvider,String)->Unit
) {
    var verifyBeforeUse: Boolean = false
    var requestTimeoutMillis: Long = 45_000L
    var onFailure: (StoryProvider,String)->Unit = { _,_ -> }
    var onRequestStart: (StoryProvider, Long) -> Unit = { _, _ -> }
    var onRequestDetails: (String, Int) -> Unit = { _, _ -> }
    var onAttemptResult: (String) -> Unit = {}
    var onRequestEnd: () -> Unit = {}
    private val sceneBudget = SceneRequestBudget()
    private var latestWriter: StoryProvider? = null
    private val rejected = mutableSetOf<Pair<StoryProvider,String>>()
    var compactScene: (String) -> String = { it }
    fun beginScene(episode: Int, first: Int) {
        if (sceneBudget.select(episode, first)) rejected.clear()
    }
    private val routes=listOf(StoryProvider.GROQ to StoryProvider.GEMINI,StoryProvider.GEMINI to StoryProvider.DEEPSEEK,StoryProvider.OPENROUTER to null,StoryProvider.MISTRAL to null,StoryProvider.SAMBANOVA to null,StoryProvider.CEREBRAS to null,StoryProvider.COHERE to null)
    private val verified=mutableSetOf<StoryProvider>()
    private val cooldown=mutableMapOf<StoryProvider,Long>()
    private val unavailable=mutableMapOf<StoryProvider,StoryHttpFailure>()
    // Never start another request on a client whose previous transport is still blocked.
    private val activeRequests=java.util.concurrent.ConcurrentHashMap<StoryProvider,java.util.concurrent.atomic.AtomicBoolean>()
    private val workers=java.util.concurrent.Executors.newCachedThreadPool { task ->
        Thread(task,"story-provider-request").apply { isDaemon=true }
    }
    private fun boundedRequest(provider: StoryProvider,prompt: String,schema: JSONObject): JSONObject {
        val active=java.util.concurrent.atomic.AtomicBoolean(true)
        if(activeRequests.putIfAbsent(provider,active)!=null) throw java.net.SocketTimeoutException("บริการยังปิดคำขอก่อนหน้า")
        val timeout = try { sceneBudget.allow(requestTimeoutMillis) } catch(e: Exception) { activeRequests.remove(provider,active); throw e }
        val formatting = schema.optJSONObject("properties")?.let { it.has("xml") || it.has("actions") } == true
        onRequestDetails(if (formatting) "จัดรูปแบบ XML" else "สร้างเนื้อหา", prompt.toByteArray(Charsets.UTF_8).size)
        onRequestStart(provider, timeout)
        val future=workers.submit<JSONObject> {
            try {
                val client=clients.getValue(provider)
                if(verifyBeforeUse && provider !in verified) {
                    try { StoryApiProbe.check(client) } catch(e: IllegalArgumentException) { throw InvalidStoryResponse("API test returned an invalid response") } catch(e: IllegalStateException) { if(e is StoryHttpFailure) throw e; throw InvalidStoryResponse("API test returned an invalid response") }
                    verified.add(provider)
                }
                client.generate(prompt,schema)
            }
            finally { active.set(false); activeRequests.remove(provider,active) }
        }
        try {
            return future.get(timeout,java.util.concurrent.TimeUnit.MILLISECONDS).also { onAttemptResult("ได้รับคำตอบ") }
        } catch(e: java.util.concurrent.TimeoutException) {
            onAttemptResult("หมดเวลารอ")
            future.cancel(true)
            runCatching { clients.getValue(provider).cancel() }
            progress(provider,"หมดเวลารอ ${timeout/1000} วินาที • กำลังลอง API สำรอง")
            throw java.net.SocketTimeoutException("หมดเวลารอผู้ให้บริการ")
        } catch(e: java.util.concurrent.ExecutionException) {
            val cause=e.cause
            onAttemptResult(if (cause is StoryHttpFailure) "HTTP ${cause.status}" else "คำขอล้มเหลว")
            if(cause is Exception) throw cause
            throw IllegalStateException("คำขอผู้ให้บริการล้มเหลว",cause)
        } catch(e: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            throw CancellationException()
        } finally {
            onRequestEnd()
        }
    }

    var retainFormattedScenes = false
    private val completedStages = File(folder, "auto_formatted_scenes")

    fun clearFormattedScenes() {
        completedStages.listFiles()?.filter { it.name.matches(Regex("[0-9a-f]{64}[.]json")) }
            ?.forEach { it.delete() }
    }

    fun generate(prompt: String,schema: JSONObject): JSONObject {
        val sceneBatch=schema.getJSONObject("properties").has("scenes")
        if (cancelled()) throw CancellationException()
        val range=Regex("Write exactly scenes ([0-9]+) to ([0-9]+)").find(prompt)
        val recover = sceneBatch && retainFormattedScenes
        val complete = if (recover) File(completedStages, draftFile(StoryProvider.GROQ,prompt,schema).name) else null
        if (complete?.isFile == true) {
            // A damaged cache must not prevent resuming the original checkpoint.
            val cached = runCatching { GeminiStoryFiles.read(complete) }.getOrNull()
            if (cached != null && SceneXml.hasFormattedActions(cached, range?.groupValues?.get(1)?.toIntOrNull(), range?.groupValues?.get(2)?.toIntOrNull())) return cached
            // Leave the original file intact until a valid replacement is written.
        }
        val multipleScenes=range?.let { it.groupValues[1].toIntOrNull()?.let { first ->
            it.groupValues[2].toIntOrNull()?.let { last -> last>first }
        } } == true
        var last: Exception?=null
        var temporary: Exception?=null
        var oversized=false
        var xmlFailure: InvalidSceneXml?=null
        // Repair attempts begin with different configured routes while retaining the same scene.
        val repair=Regex("DIRECTOR_INTERNAL_REPAIR_ATTEMPT=([1-3])").find(prompt)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        // Give the final repair to Cohere first when configured, instead of
        // repeatedly accepting invalid semantic output from the same writers.
        val offset=if(repair==3) routes.lastIndex else (repair-1)%routes.size
        val rotated=routes.drop(offset)+routes.take(offset)
        // A saved draft does not need another writer request. Otherwise prefer
        // a ready provider over waiting inside Groq's local pacing interval.
        val ordered=rotated.sortedWith(compareBy<Pair<StoryProvider,StoryProvider?>> { (writer,_) -> if (draftFile(writer,prompt,schema).isFile) 0 else 1 }.thenBy { (writer,_) ->
            if(draftFile(writer,prompt,schema).isFile) 0L else clients[writer]?.availableDelayMillis() ?: Long.MAX_VALUE
        }.thenBy { (writer,_) -> if (repair == 1 && writer == latestWriter) 0 else 1 })
        for((writer,formatter) in ordered) {
            if(cancelled()) throw CancellationException()
            // The preferred formatter may be unavailable; another configured provider
            // can still format the writer's draft without losing completed work.
            val savedWriter = recover && draftFile(writer,prompt,schema).isFile
            if(!savedWriter && (writer !in clients || writer in unavailable || (cooldown[writer] ?: 0L)>android.os.SystemClock.elapsedRealtime())) continue
            // Do not issue a fresh writer request when no formatter is available;
            // an already saved complete draft can still be used directly.
            if(sceneBatch && formatter!=null && listOf(formatter,StoryProvider.DEEPSEEK,StoryProvider.OPENROUTER,StoryProvider.COHERE,StoryProvider.MISTRAL,StoryProvider.SAMBANOVA,StoryProvider.CEREBRAS)
                .none { it!=writer && it in clients && it !in unavailable && (cooldown[it] ?: 0L)<=android.os.SystemClock.elapsedRealtime() }) {
                val saved=draftFile(writer,prompt,schema)
                if (savedWriter && !SceneXml.hasUsableActions(GeminiStoryFiles.read(saved))) throw SceneWorkPaused("เก็บร่างบทไว้แล้ว • รอ API จัดรูปแบบ")
                if(!saved.isFile || !runCatching { SceneXml.hasUsableActions(GeminiStoryFiles.read(saved)) }.getOrDefault(false)) continue
            }
            var current=writer
            try {
                val cache=draftFile(writer,prompt,schema)
                val draft=if(cache.isFile) GeminiStoryFiles.read(cache) else {
                    progress(writer,"กำลังเขียนบท • รอคำตอบสูงสุด ${requestTimeoutMillis/1000} วินาที ก่อนสลับ API")
                    writeScene(writer,prompt,schema,sceneBatch && !multipleScenes).also { GeminiStoryFiles.write(cache,it) }
                }
                if(cancelled()) throw CancellationException()
                val result=if(sceneBatch && formatter!=null && !SceneXml.hasUsableActions(draft)) {
                    val request="""Return JSON with actions, an array of objects containing scene (integer) and action (English visible action). Include exactly one entry for each input scene, preserving people and events. Do not add speech or rewrite dialogue. Kotlin will construct XML and copy original dialogue. Input scenes: ${draft.getJSONArray("scenes")}"""
                    val xmlSchema=JSONObject("""{"type":"object","properties":{"actions":{"type":"array","items":{"type":"object","properties":{"scene":{"type":"integer"},"action":{"type":"string"}},"required":["scene","action"]}}},"required":["actions"]}""")
                    fun formatWith(provider: StoryProvider): JSONObject {
                        current=provider
                        progress(provider,"กำลังจัดรูปแบบ XML • รอคำตอบสูงสุด ${requestTimeoutMillis/1000} วินาที")
                        val formatted=generateChecked(provider,request,xmlSchema)
                        return try {
                            if(formatted.has("actions")) SceneXml.fromActions(draft,formatted.getJSONArray("actions"))
                            else SceneXml.apply(draft,SceneXml.unwrap(formatted.getString("xml")))
                        } catch(e: Exception) { throw InvalidSceneXml(e) }
                    }
                    // Pass the same completed draft to available formatters; never
                    // rewrite the writer's scenes merely because formatting failed.
                    val formatters=(listOf(formatter,StoryProvider.DEEPSEEK,StoryProvider.OPENROUTER,StoryProvider.COHERE,StoryProvider.MISTRAL,StoryProvider.SAMBANOVA,StoryProvider.CEREBRAS))
                        .distinct().filter { it!=writer && it in clients && it !in unavailable && (cooldown[it] ?: 0L)<=android.os.SystemClock.elapsedRealtime() }
                        .sortedBy { clients.getValue(it).availableDelayMillis() }
                    var formatted: JSONObject?=null
                    var formatFailure: Exception?=null
                    for(target in formatters) {
                        try { formatted=formatWith(target); break }
                        catch(e: Exception) {
                            if(cancelled() || e is CancellationException) throw CancellationException()
                            if(!canSwitch(e)) throw e
                            if(target!=formatters.last()) onFailure(target,failureCategory(e))
                            if(e is StoryHttpFailure && e.status in setOf(401,402,403,404)) unavailable[target]=e
                            coolDown(target,e)
                            if(StoryRequestRetry.canRetry(e)) temporary=e
                            formatFailure=e
                        }
                    }
                    if (recover && formatted == null) {
                        formatFailure?.let { onFailure(current,failureCategory(it)) }
                        throw SceneWorkPaused("เก็บร่างบทไว้แล้ว • จัดรูปแบบยังไม่สำเร็จ • "+(formatFailure?.let { failureCategory(it) } ?: "ไม่มี API จัดรูปแบบที่พร้อม"))
                    }
                    formatted ?: throw (formatFailure ?: IllegalStateException("ไม่มีบริการจัดรูปแบบที่พร้อม"))
                } else draft
                if (complete != null) GeminiStoryFiles.write(complete,result)
                routes.forEach { (candidate,_) -> draftFile(candidate,prompt,schema).delete() }
                latestWriter = writer
                return result
            } catch(e: Exception) {
                if(cancelled() || e is CancellationException) throw CancellationException()
                if (e is SceneWorkPaused) throw e
                onFailure(current,failureCategory(e))
                if(e is InvalidSceneXml) xmlFailure=e
                if(e is StoryHttpFailure && e.status in setOf(401,402,403,404)) unavailable[current]=e
                coolDown(current,e)
                if(StoryRequestRetry.canRetry(e)) temporary=e
                if(e is StoryHttpFailure && (e.status==413 || e.detail=="context_length_exceeded")) oversized=true
                // Split a fresh multi-scene request immediately rather than waiting for
                // every fallback to time out on the same oversized input. One-scene
                // requests retain normal provider fallback and bounded retries.
                if(sceneBatch && multipleScenes && current==writer && e is StoryHttpFailure &&
                    (e.status==413 || e.detail=="context_length_exceeded")) throw StoryBatchTooLarge()
                if(!canSwitch(e)) throw e
                last=e
            }
        }
        // A temporary failure in the last available provider can recover on retry,
        // even if another provider rejected the request size.
        if(sceneBatch && multipleScenes && xmlFailure!=null) throw StoryBatchTooLarge("XML ไม่ตรงบท • ลดเหลือครั้งละ 1 ซีน")
        if (sceneBudget.active && temporary != null) {
            throw SceneWorkPaused("บริการที่พร้อมตอบไม่สำเร็จ • " + failureCategory(temporary))
        }
        temporary?.let { throw it }
        val next=cooldown.filterKeys { it in clients }.values.filter { it>android.os.SystemClock.elapsedRealtime() }.minOrNull()
        if (sceneBudget.active && next != null) throw SceneWorkPaused("บริการสำรองกำลังพัก • ลองใหม่หลัง ${((next-android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0)+999)/1000} วินาที")
        if(next!=null) throw StoryHttpFailure(429,(((next-android.os.SystemClock.elapsedRealtime())+999)/1000).toString(),"รอ API คืนโควตา")
        if (sceneBudget.active && oversized && !multipleScenes) throw SceneWorkPaused("ซีนเดี่ยวยังเกินขนาดที่ API รองรับหลังลดคำสั่ง")
        if(sceneBatch && oversized) throw StoryBatchTooLarge()
        throw last ?: unavailable.values.firstOrNull() ?: IllegalStateException("ตั้งค่า Groq + Gemini หรือ Gemini + DeepSeek หรือ OpenRouter หรือ Cohere หรือ Mistral หรือ SambaNova หรือ Cerebras ก่อนเริ่ม")
    }
    private fun writeScene(provider: StoryProvider, prompt: String, schema: JSONObject, single: Boolean): JSONObject {
        if (!single) return generateChecked(provider,prompt,schema)
        fun send(text: String): JSONObject {
            val key = provider to draftFile(provider,text,schema).name
            if (key in rejected) throw StoryHttpFailure(413,null,"คำขอขนาดนี้เคยถูกปฏิเสธ")
            try {
                return generateChecked(provider,text,schema)
            } catch (e: StoryHttpFailure) {
                if (e.status == 413 || e.detail == "context_length_exceeded") rejected.add(key)
                throw e
            }
        }
        val compact = compactScene(prompt)
        val initial = if (prompt.toByteArray(Charsets.UTF_8).size > 24_000 && compact.toByteArray(Charsets.UTF_8).size < prompt.toByteArray(Charsets.UTF_8).size) compact else prompt
        try {
            return send(initial)
        } catch (e: StoryHttpFailure) {
            if (e.status != 413 && e.detail != "context_length_exceeded") throw e
            val smaller = compact
            if (smaller.toByteArray(Charsets.UTF_8).size >= initial.toByteArray(Charsets.UTF_8).size) throw e
            onFailure(provider,failureCategory(e))
            progress(provider,"ลดขนาดคำสั่งซีนเดี่ยว • คงบทและ MASTER")
            return send(smaller)
        }
    }
    private fun coolDown(provider: StoryProvider,e: Exception) {
        if (sceneBudget.active && (e is java.net.SocketTimeoutException || e is StoryHttpFailure && e.status == 503)) {
            val seconds = (e as? StoryHttpFailure)?.retryAfter?.let { StoryRequestRetry.retrySeconds(it,System.currentTimeMillis()) } ?: 0L
            val now = android.os.SystemClock.elapsedRealtime()
            cooldown[provider] = now + maxOf(30L,seconds).coerceAtMost((Long.MAX_VALUE-now)/1000)*1000
        }
        if(verifyBeforeUse && e is StoryHttpFailure && e.status==429) {
            val now=android.os.SystemClock.elapsedRealtime()
            val suggested=e.retryAfter?.let { StoryRequestRetry.retrySeconds(it,System.currentTimeMillis()) }
            val seconds=(if(provider==StoryProvider.GEMINI && suggested!=null) suggested.coerceAtLeast(1L) else maxOf(65L,suggested ?: 0L)).coerceAtMost((Long.MAX_VALUE-now)/1000)
            cooldown[provider]=now+seconds*1000L
        }
    }
    private fun generateChecked(provider: StoryProvider,prompt: String,schema: JSONObject): JSONObject = try {
        boundedRequest(provider,prompt,schema)
    } catch(e: InvalidStoryResponse) {
        if(cancelled()) throw CancellationException()
        onFailure(provider,"ตรวจคำตอบไม่ผ่าน")
        // Give a different configured provider a chance immediately. A single
        // provider retains one retry, so existing single-key setups still work.
        if(clients.keys.any { it!=provider && it !in unavailable }) throw e
        progress(provider,"คำตอบไม่สมบูรณ์ • ลองใหม่ 1 ครั้ง")
        boundedRequest(provider,prompt,schema)
    }
    private fun failureCategory(e: Exception): String = when(e) {
        is InvalidSceneXml -> "XML ไม่ตรงบท • "+(e.cause?.message ?: "ตรวจรูปแบบไม่ผ่าน").take(160)
        is StoryHttpFailure -> "HTTP ${e.status}" + if(e.detail in setOf("invalid_schema","context_length_exceeded","json_validate_failed")) " • ${e.detail}" else ""
        is java.net.SocketTimeoutException -> "หมดเวลารอ"
        is javax.net.ssl.SSLException -> "TLS ขัดข้อง"
        is java.io.IOException -> "เครือข่ายขัดข้อง"
        is InvalidStoryResponse -> "ตรวจคำตอบไม่ผ่าน • "+(e.message ?: "รูปแบบคำตอบไม่ถูกต้อง").take(160)
        else -> "ตรวจคำตอบไม่ผ่าน"
    }
    private fun canSwitch(e: Exception): Boolean = e is InvalidSceneXml || e is InvalidStoryResponse || StoryRequestRetry.canFallback(e) ||
        e is StoryHttpFailure && e.status in setOf(401,402,403,404)
    private fun draftFile(writer: StoryProvider,prompt: String,schema: JSONObject): File {
        val digest=MessageDigest.getInstance("SHA-256").digest((writer.id+"\n"+prompt+"\n"+schema).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return File(folder,"auto_drafts/$digest.json")
    }
}

internal class StoryBatchTooLarge(message: String = "คำขอใหญ่เกินขีดจำกัด • กรุณาตรวจขนาดคำสั่งและโควตา"): IllegalStateException(message)

internal class InvalidSceneXml(cause: Exception): IllegalArgumentException("XML ที่ได้ไม่ตรงกับบทต้นฉบับ",cause)

/** Reject DTDs, missing/duplicate IDs and changed dialogue before merging formatted actions. */
internal object SceneXml {
    /** Reuse the writer's own complete English directions; downstream scene validation still runs. */
    fun hasFormattedActions(draft: JSONObject, first: Int? = null, last: Int? = null): Boolean {
        val rows = draft.optJSONArray("scenes") ?: return false
        if (first != null && last != null && (last < first || rows.length().toLong() != last.toLong() - first + 1)) return false
        val ids = mutableSetOf<Int>()
        return rows.length() > 0 && (0 until rows.length()).all { index ->
            val row = rows.optJSONObject(index) ?: return@all false
            val id = row.optInt("scene", -1)
            id > 0 && (first == null || id.toLong() == first.toLong() + index) && ids.add(id) && !row.isNull("flow_action") && row.optString("flow_action").isNotBlank()
        }
    }
    fun hasUsableActions(draft: JSONObject): Boolean {
        val rows=draft.optJSONArray("scenes") ?: return false
        if(rows.length()==0) return false
        val ids=mutableSetOf<Int>()
        for(i in 0 until rows.length()) {
            val row=rows.optJSONObject(i) ?: return false
            val id=row.optInt("scene",-1)
            if(id<1 || !ids.add(id)) return false
            var action=row.optString("flow_action").trim()
            if(action.isEmpty()) return false
            val cast=row.optJSONArray("characters") ?: return false
            for(j in 0 until cast.length()) {
                val name=cast.optJSONObject(j)?.optString("name").orEmpty()
                if(name.isNotBlank()) action=action.replace(name,"")
            }
            if(Regex("[\\u0E00-\\u0E7F]").containsMatchIn(action)) return false
            if(!Regex("[A-Za-z]").containsMatchIn(action)) return false
        }
        return true
    }
    fun fromActions(draft: JSONObject, actions: JSONArray): JSONObject {
        val rows=draft.getJSONArray("scenes")
        require(actions.length()==rows.length()) { "จำนวนซีน action ไม่ครบ" }
        val byId=mutableMapOf<Int,String>()
        for(i in 0 until actions.length()) {
            val item=actions.getJSONObject(i)
            val id=item.getInt("scene")
            val action=item.getString("action")
            require(action.isNotBlank() && byId.put(id,action)==null) { "action ว่างหรือหมายเลขซีนซ้ำ: $id" }
        }
        val output=java.io.StringWriter()
        val serializer=XmlPullParserFactory.newInstance().newSerializer()
        serializer.setOutput(output)
        serializer.startTag(null,"flow")
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i)
            val id=row.getInt("scene")
            val action=requireNotNull(byId[id]) { "ขาด action ซีน $id" }
            serializer.startTag(null,"scene").attribute(null,"id",id.toString())
            serializer.startTag(null,"prompt").text(action).endTag(null,"prompt")
            serializer.startTag(null,"dialogue").text(row.optString("dialogue")).endTag(null,"dialogue")
            serializer.endTag(null,"scene")
        }
        serializer.endTag(null,"flow")
        serializer.flush()
        val result=apply(draft,output.toString())
        require(hasUsableActions(result)) { "action ต้องเป็นภาษาอังกฤษและตรงทะเบียนตัวละคร" }
        return result
    }
    fun unwrap(raw: String): String {
        val trimmed=raw.trim().removePrefix("\uFEFF").trim()
        return if(trimmed.startsWith("```xml",ignoreCase=true) && trimmed.endsWith("```")) trimmed.substring(6,trimmed.length-3).trim() else trimmed
    }
    fun apply(draft: JSONObject,xml: String): JSONObject {
        require(xml.length in 1..500_000 && !Regex("<!",RegexOption.IGNORE_CASE).containsMatchIn(xml)) { "XML ไม่ปลอดภัยหรือมีขนาดไม่ถูกต้อง" }
        val rows=draft.getJSONArray("scenes")
        val wanted=(0 until rows.length()).associateBy { rows.getJSONObject(it).getInt("scene") }
        require(wanted.size==rows.length()) { "หมายเลขซีนซ้ำ" }
        val found=mutableMapOf<Int,Pair<String,String>>()
        val p=XmlPullParserFactory.newInstance().newPullParser().apply { setInput(StringReader(xml)) }
        var event=p.eventType
        var root=false
        while(event!=XmlPullParser.END_DOCUMENT) {
            if(event==XmlPullParser.START_TAG) when {
                p.depth==1 -> { require(p.name=="flow" && !root); root=true }
                p.depth==2 -> {
                    require(root && p.name=="scene") { "รูปแบบซีน XML ไม่ถูกต้อง" }
                    val id=p.getAttributeValue(null,"id")?.toIntOrNull() ?: error("ขาดหมายเลขซีน")
                    require(id in wanted && id !in found) { "หมายเลขซีน XML ไม่ถูกต้องหรือซ้ำ" }
                    var action: String?=null; var dialogue: String?=null
                    while(p.next()!=XmlPullParser.END_DOCUMENT) {
                        if(p.eventType==XmlPullParser.END_TAG && p.depth==2 && p.name=="scene") break
                        if(p.eventType==XmlPullParser.START_TAG) {
                            require(p.depth==3)
                            when(p.name) {
                                "prompt" -> { require(action==null); action=p.nextText() }
                                "dialogue" -> { require(dialogue==null); dialogue=p.nextText() }
                                else -> error("แท็กในซีนไม่ถูกต้อง")
                            }
                        }
                    }
                    require(!action.isNullOrBlank() && dialogue!=null) { "ซีนขาด prompt หรือ dialogue" }
                    val source=rows.getJSONObject(wanted.getValue(id))
                    require(dialogue==source.optString("dialogue")) { "บทพูด XML ไม่ตรงบทต้นฉบับ" }
                    found[id]=action!! to dialogue!!
                }
                else -> error("XML ซ้อนแท็กไม่ถูกต้อง")
            }
            event=p.next()
        }
        require(root && found.size==rows.length()) { "จำนวนซีน XML ไม่ครบ" }
        val out=JSONObject(draft.toString()); val output=out.getJSONArray("scenes")
        for(i in 0 until output.length()) {
            val row=output.getJSONObject(i); val action=found.getValue(row.getInt("scene")).first
            row.put("flow_action",action)
        }
        return out
    }
}
