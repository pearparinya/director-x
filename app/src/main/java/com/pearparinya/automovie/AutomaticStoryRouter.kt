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
    private val routes=listOf(StoryProvider.GROQ to StoryProvider.GEMINI,StoryProvider.CEREBRAS to StoryProvider.DEEPSEEK,StoryProvider.OPENROUTER to null)

    fun generate(prompt: String,schema: JSONObject): JSONObject {
        val sceneBatch=schema.getJSONObject("properties").has("scenes")
        var last: Exception?=null
        for((writer,formatter) in routes) {
            if(cancelled()) throw CancellationException()
            if(writer !in clients || formatter != null && formatter !in clients) continue
            try {
                val digest=MessageDigest.getInstance("SHA-256").digest((writer.id+"\n"+prompt+"\n"+schema).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                val cache=File(folder,"auto_drafts/$digest.json")
                val draft=if(cache.isFile) GeminiStoryFiles.read(cache) else {
                    progress(writer,"กำลังเขียนบท")
                    clients.getValue(writer).generate(prompt,schema).also { GeminiStoryFiles.write(cache,it) }
                }
                if(cancelled()) throw CancellationException()
                val result=if(sceneBatch && formatter!=null) {
                    progress(formatter,"กำลังจัดรูปแบบ XML")
                    val request="""Create a well-formed XML document with root <flow>. For every input scene include exactly one <scene id="NUMBER"><prompt>ENGLISH ACTION</prompt><dialogue>EXACT THAI DIALOGUE</dialogue></scene>. The scene id and dialogue must exactly match the input; use empty dialogue for silent or narrated scenes. Escape XML special characters. The prompt must be an English visible action using only the same people and events; preserve their names and do not add speech. No other scene or tag. Return JSON with one field named xml containing the XML. Input scenes: ${draft.getJSONArray("scenes")}"""
                    val xmlSchema=JSONObject().put("type","object").put("properties",JSONObject().put("xml",JSONObject().put("type","string"))).put("required",JSONArray().put("xml"))
                    val formatted=clients.getValue(formatter).generate(request,xmlSchema)
                    try { SceneXml.apply(draft,formatted.getString("xml")) }
                    catch(e: Exception) { throw InvalidSceneXml(e) }
                } else draft
                cache.delete()
                return result
            } catch(e: Exception) {
                if(cancelled() || e is CancellationException) throw CancellationException()
                if(!canSwitch(e)) throw e
                last=e
            }
        }
        throw last ?: IllegalStateException("ตั้งค่า Groq + Gemini หรือ Cerebras + DeepSeek หรือ OpenRouter ก่อนเริ่ม")
    }
    private fun canSwitch(e: Exception): Boolean = e is InvalidSceneXml || StoryRequestRetry.canFallback(e) ||
        e is StoryHttpFailure && e.status in setOf(401,403,404)
}

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
