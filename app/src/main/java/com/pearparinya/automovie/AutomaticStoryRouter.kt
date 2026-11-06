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
    var onFailure: (StoryProvider,String)->Unit = { _,_ -> }
    private val routes=listOf(StoryProvider.GROQ to StoryProvider.GEMINI,StoryProvider.GEMINI to StoryProvider.DEEPSEEK,StoryProvider.OPENROUTER to null,StoryProvider.COHERE to null)
    private val unavailable=mutableMapOf<StoryProvider,StoryHttpFailure>()

    fun generate(prompt: String,schema: JSONObject): JSONObject {
        val sceneBatch=schema.getJSONObject("properties").has("scenes")
        val range=Regex("Write exactly scenes ([0-9]+) to ([0-9]+)").find(prompt)
        val multipleScenes=range?.let { it.groupValues[1].toIntOrNull()?.let { first ->
            it.groupValues[2].toIntOrNull()?.let { last -> last>first }
        } } == true
        var last: Exception?=null
        var temporary: Exception?=null
        var oversized=false
        // Repair attempts begin with different configured routes while retaining the same scene.
        val repair=Regex("DIRECTOR_INTERNAL_REPAIR_ATTEMPT=([1-3])").find(prompt)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        // Give the final repair to Cohere first when configured, instead of
        // repeatedly accepting invalid semantic output from the same writers.
        val offset=if(repair==3) routes.lastIndex else (repair-1)%routes.size
        val ordered=routes.drop(offset)+routes.take(offset)
        for((writer,formatter) in ordered) {
            if(cancelled()) throw CancellationException()
            // The preferred formatter may be unavailable; another configured provider
            // can still format the writer's draft without losing completed work.
            if(writer !in clients || writer in unavailable) continue
            var current=writer
            try {
                val cache=draftFile(writer,prompt,schema)
                val draft=if(cache.isFile) GeminiStoryFiles.read(cache) else {
                    progress(writer,"กำลังเขียนบท")
                    generateChecked(writer,prompt,schema).also { GeminiStoryFiles.write(cache,it) }
                }
                if(cancelled()) throw CancellationException()
                val result=if(sceneBatch && formatter!=null) {
                    val request="""Create a well-formed XML document with root <flow>. For every input scene include exactly one <scene id="NUMBER"><prompt>ENGLISH ACTION</prompt><dialogue>EXACT THAI DIALOGUE</dialogue></scene>. The scene id and dialogue must exactly match the input; use empty dialogue for silent or narrated scenes. Escape XML special characters. The prompt must be an English visible action using only the same people and events; preserve their names and do not add speech. No other scene or tag. Return JSON with one field named xml containing the XML. Input scenes: ${draft.getJSONArray("scenes")}"""
                    val xmlSchema=JSONObject().put("type","object").put("properties",JSONObject().put("xml",JSONObject().put("type","string"))).put("required",JSONArray().put("xml"))
                    fun formatWith(provider: StoryProvider): JSONObject {
                        current=provider
                        progress(provider,"กำลังจัดรูปแบบ XML")
                        val formatted=generateChecked(provider,request,xmlSchema)
                        return try { SceneXml.apply(draft,formatted.getString("xml")) }
                        catch(e: Exception) { throw InvalidSceneXml(e) }
                    }
                    // Pass the same completed draft to available formatters; never
                    // rewrite the writer's scenes merely because formatting failed.
                    val formatters=(listOf(formatter,StoryProvider.DEEPSEEK,StoryProvider.OPENROUTER,StoryProvider.COHERE))
                        .distinct().filter { it!=writer && it in clients && it !in unavailable }
                    var formatted: JSONObject?=null
                    var formatFailure: Exception?=null
                    for(target in formatters) {
                        try { formatted=formatWith(target); break }
                        catch(e: Exception) {
                            if(cancelled() || e is CancellationException) throw CancellationException()
                            if(!canSwitch(e)) throw e
                            if(target!=formatters.last()) onFailure(target,failureCategory(e))
                            if(e is StoryHttpFailure && e.status==402) unavailable[target]=e
                            if(StoryRequestRetry.canRetry(e)) temporary=e
                            formatFailure=e
                        }
                    }
                    formatted ?: throw (formatFailure ?: IllegalStateException("ไม่มีบริการจัดรูปแบบที่พร้อม"))
                } else draft
                routes.forEach { (candidate,_) -> draftFile(candidate,prompt,schema).delete() }
                return result
            } catch(e: Exception) {
                if(cancelled() || e is CancellationException) throw CancellationException()
                onFailure(current,failureCategory(e))
                if(e is StoryHttpFailure && e.status==402) unavailable[current]=e
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
        temporary?.let { throw it }
        if(sceneBatch && oversized) throw StoryBatchTooLarge()
        throw last ?: unavailable.values.firstOrNull() ?: IllegalStateException("ตั้งค่า Groq + Gemini หรือ Gemini + DeepSeek หรือ OpenRouter หรือ Cohere ก่อนเริ่ม")
    }
    private fun generateChecked(provider: StoryProvider,prompt: String,schema: JSONObject): JSONObject = try {
        clients.getValue(provider).generate(prompt,schema)
    } catch(e: InvalidStoryResponse) {
        if(cancelled()) throw CancellationException()
        onFailure(provider,"ตรวจคำตอบไม่ผ่าน")
        progress(provider,"คำตอบไม่สมบูรณ์ • ลองใหม่ 1 ครั้ง")
        clients.getValue(provider).generate(prompt,schema)
    }
    private fun failureCategory(e: Exception): String = when(e) {
        is InvalidSceneXml -> "XML ไม่ตรงบท"
        is StoryHttpFailure -> "HTTP ${e.status}" + if(e.detail in setOf("invalid_schema","context_length_exceeded","json_validate_failed")) " • ${e.detail}" else ""
        is java.net.SocketTimeoutException -> "หมดเวลารอ"
        is javax.net.ssl.SSLException -> "TLS ขัดข้อง"
        is java.io.IOException -> "เครือข่ายขัดข้อง"
        else -> "ตรวจคำตอบไม่ผ่าน"
    }
    private fun canSwitch(e: Exception): Boolean = e is InvalidSceneXml || e is InvalidStoryResponse || StoryRequestRetry.canFallback(e) ||
        e is StoryHttpFailure && e.status in setOf(401,402,403,404)
    private fun draftFile(writer: StoryProvider,prompt: String,schema: JSONObject): File {
        val digest=MessageDigest.getInstance("SHA-256").digest((writer.id+"\n"+prompt+"\n"+schema).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return File(folder,"auto_drafts/$digest.json")
    }
}

internal class StoryBatchTooLarge: IllegalStateException("คำขอใหญ่เกินขีดจำกัด • กรุณาตรวจขนาดคำสั่งและโควตา")

internal class InvalidSceneXml(cause: Exception): IllegalArgumentException("XML ที่ได้ไม่ตรงกับบทต้นฉบับ",cause)

/** Reject DTDs, missing/duplicate IDs and changed dialogue before merging formatted actions. */
internal object SceneXml {
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
