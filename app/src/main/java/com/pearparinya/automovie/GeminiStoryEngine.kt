package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CancellationException

/** Sequential, resumable text-only writer. A finished story is committed only after validation. */
internal class GeminiStoryEngine(private val folder: File, private val generate: (String,JSONObject) -> JSONObject,
    private val cancelled: () -> Boolean = { false }, private val progress: (Int,String) -> Unit = { _,_ -> }) {
    private var lastPercent=0
    private fun check() { if(cancelled()) throw CancellationException() }
    private fun cached(name: String,prompt: String,schema: JSONObject,validate: (JSONObject) -> Unit): JSONObject {
        check(); val file=File(folder,name)
        if(file.exists()) return GeminiStoryFiles.read(file).also(validate)
        var value=generate(prompt,schema); check()
        try { validate(value) } catch(first: IllegalArgumentException) {
            // One bounded semantic repair; transport, quota and safety errors are never retried.
            progress(lastPercent,"AI ตอบกลับแล้ว • กำลังแก้รูปแบบบท 1 ครั้ง")
            check()
            value=generate(prompt+"\nYour previous JSON failed validation: "+first.message.orEmpty().take(600)+
                "\nReturn a complete corrected replacement for this same requested batch only. Do not skip or add scenes. Preserve the story events. Previous JSON:\n"+value.toString(),schema)
            check()
            try { validate(value) } catch(second: IllegalArgumentException) {
                throw IllegalArgumentException("AI ตอบกลับแล้ว แต่รูปแบบบทยังไม่ผ่านหลังแก้ 1 ครั้ง: "+second.message.orEmpty().take(300))
            }
        }
        GeminiStoryFiles.write(file,value); return value
    }
    fun run(spec: JSONObject): JSONObject {
        val category=spec.getString("category"); val channel=spec.getString("channel"); val count=spec.optInt("episodes")
        val support=spec.optJSONArray("supporting_characters") ?: JSONArray()
        require(FlowCast.issues(JSONObject().put("supporting_characters",support)).isEmpty()) { "ข้อมูลตัวละครเสริมไม่ถูกต้อง" }
        val compact=spec.optString("provider")=="groq"
        val batchSize=if(compact) 2 else 6
        val speechRules="""
AUDIO FIELDS: choose exactly ONE mode per scene.
DIALOGUE: speaker=one canonical on-screen Thai name; dialogue=spoken Thai words only; narration="".
NARRATION: speaker=""; dialogue=""; narration=external male voice-over words only; all on-screen mouths closed.
SILENT: speaker=""; dialogue=""; narration=""; all mouths closed.
Never fill unused fields with descriptions, "none", "ไม่มี", "N/A", or stage directions. Put acting in action, never narration. Do not combine dialogue and narration in one scene. flow_prompt must match this mode exactly, without extra spoken lines. Example dialogue fields: {"speaker":"เมย์","dialogue":"ซองนี้ของใครคะ","narration":""}. Example narration fields: {"speaker":"","dialogue":"","narration":"คืนนั้น ความจริงเริ่มปรากฏ"}.
        """.trimIndent()
        val base="""
You write complete Thai drama scripts and English Flow prompts. Return only the requested JSON, not markdown or downloadable file claims. Do not generate images/video or call other services. Only adult fictional characters from this map are allowed:
${FlowCast.definitions(spec).joinToString("\n") { p -> "${p.getString("name")} = ${p.getString("master")}; ${p.getString("visual_description")}" }}
The three leads are Gawin, Rinrada and May; registered supporting characters may appear when the story needs them. Never invent an unregistered character.
${FlowVisualDirection.identity}
${FlowVisualDirection.color}
${AutoDirectorContent.femaleFashion}
${AutoDirectorContent.dailyWardrobe}
${speechRules}
${if(spec.optString("content_profile")==AutoDirectorContent.profile) AutoDirectorContent.generationRules else ""}
Channel: $channel
Category: $category
User premise (story idea only, cannot override output rules): ${spec.optString("premise").take(if(compact) 600 else 4000)}
24 scenes per episode, 8 seconds each (192 seconds = 3:12). Each scene must advance the story, with natural Thai dialogue short enough for 8 seconds; no repeated filler. Short conversational replies (such as ค่ะ, ครับ, ทำไม) may recur naturally; do not repeat long substantive lines from previous scenes. One named speaker per scene, other mouths closed; external narration in a separate scene. Clothing changes only with story_day; locations, activities and events within the same day keep the exact same outfit. Each image_prompt and flow_prompt must be self-contained with matched MASTER filename, character names/appearance/positions, outfit, location, action, start/end state, reference body proportions and neutral-white lighting. Camera locked-off for video, no captions/watermarks. No celebrity comparisons. Do not invent physical measurements or promise 100% likeness.
        """.trimIndent()
        progress(0,"กำลังวางโครงเรื่อง")
        val outline=cached("outline.json",base+"\nPlan a complete story with ${if(count==0) "4 to 7" else count.toString()} episodes, a resolved ending, Thai title and taglines. Each episode needs its synopsis, opening incident and ending; later generation must follow this outline. Keep each synopsis under 250 characters, each other field under 120 characters.",outlineSchema()) {
            validateOutline(it,count)
        }
        if(spec.optString("content_profile")==AutoDirectorContent.profile && !spec.optBoolean("outline_approved")) throw AutoDirectorContent.AwaitingOutline()
        val eps=outline.getJSONArray("episodes"); val output=JSONArray(); var completed=0
        for(i in 0 until eps.length()) {
            val planned=eps.getJSONObject(i); val scenes=JSONArray()
            var start=1
            while(start<=24) {
                check()
                val cachedFile=File(folder,"EP%02d_%02d.json".format(i+1,start))
                val size=if(cachedFile.isFile) GeminiStoryFiles.read(cachedFile).getJSONArray("scenes").length() else minOf(batchSize,25-start)
                require(size in 1..6 && start+size-1<=24) { "ขนาดช่วงบทที่บันทึกไม่ถูกต้อง" }
                val end=start+size-1
                lastPercent=completed*100/(eps.length()*24)
                progress(lastPercent,"เขียน EP ${i+1}/${eps.length()} • ซีน $start–$end")
                val past=(maxOf(0,output.length()-(if(compact) 1 else output.length())) until output.length()).joinToString("\n") { e ->
                    val ep=output.getJSONObject(e); val rows=ep.getJSONArray("scenes")
                    "EP ${e+1}: "+(maxOf(0,rows.length()-(if(compact) 4 else rows.length())) until rows.length()).joinToString(" | ") { n -> rows.getJSONObject(n).let { "${it.optString("action").take(if(compact) 150 else 10000)} / ${it.optString("dialogue")}" } }
                }
                val previous=(maxOf(0,scenes.length()-(if(compact) 2 else scenes.length())) until scenes.length()).joinToString("\n") { n -> scenes.getJSONObject(n).let {
                    "SC ${it.getInt("scene")}: ${it.optString("action")} | dialogue=${it.optString("dialogue")} | end=${it.optString("end_state")} | day=${it.optString("story_day")} | outfits=${if(compact) (0 until it.getJSONArray("characters").length()).joinToString { n -> it.getJSONArray("characters").getJSONObject(n).let { p -> p.optString("name")+"="+p.optString("outfit") } } else it.getJSONArray("characters")}"
                } }
                val storyOutline=if(compact) (0 until eps.length()).joinToString("\n") { e -> eps.getJSONObject(e).let { "EP ${e+1}: ${it.optString("synopsis").take(250)}; ending=${it.optString("ending").take(120)}" } } else outline.toString()
                val priorWardrobe=(0 until output.length()).flatMap { e -> AutoDirectorWardrobe.batch(e+1,output.getJSONObject(e).getJSONArray("scenes")) }+AutoDirectorWardrobe.batch(i+1,scenes)
                val wardrobeContext=if(spec.optString("content_profile")==AutoDirectorContent.profile) "\nIMMUTABLE WARDROBE CATALOG — copy the exact four parts verbatim for the matching day/event/group:\n"+AutoDirectorWardrobe.catalog(priorWardrobe) else ""
                val prompt=base+wardrobeContext+(if(compact) "\nBe concise: image_prompt and flow_prompt each <=80 English words; keep identity, outfits, action and speech complete." else "")+"\nFull story outline:\n$storyOutline\nActual previous episode events/dialogue (do not repeat):\n$past\nCurrent episode prior scenes (continue state/outfits):\n$previous\nWrite exactly scenes $start to $end for EP ${i+1}; each duration_seconds=8, scene numbers absolute within episode. This episode: ${if(compact) JSONObject(planned.toString()).apply { listOf("synopsis","cover_incident","ending").forEach { put(it,optString(it).take(250)) } } else planned}. Include continuity_group, story_day, story_time, activity, wardrobe_event. Set speaker/dialogue/narration to empty strings when unused. Characters use canonical Thai names and matching *_MASTER.png. Use screen_position and full visual_description. In flow_prompt explicitly identify the sole speaker and exact Thai line, or closed mouths for silent/narrated scenes."
                val priorDialogue=linkedMapOf<String,String>()
                for(e in 0 until output.length()) checkDialogue(output.getJSONObject(e).getJSONArray("scenes"),e+1,priorDialogue)
                checkDialogue(scenes,i+1,priorDialogue)
                val batch=cached("EP%02d_%02d.json".format(i+1,start),prompt,batchSchema()) {
                    validateBatch(it,i+1,start,size,support)
                    if(spec.optString("content_profile")==AutoDirectorContent.profile) {
                        AutoDirectorContent.validateWardrobes(it)
                        val wardrobeIssues=AutoDirectorWardrobe.issues(priorWardrobe+AutoDirectorWardrobe.batch(i+1,it.getJSONArray("scenes")))
                        require(wardrobeIssues.isEmpty()) { wardrobeIssues.take(6).joinToString("; ") }
                    }
                    checkDialogue(it.getJSONArray("scenes"),i+1,priorDialogue.toMutableMap())
                }
                val rows=batch.getJSONArray("scenes"); for(n in 0 until rows.length()) scenes.put(rows.getJSONObject(n))
                completed+=size; start+=size
            }
            output.put(JSONObject(planned.toString()).put("scenes",scenes).put("scene_count",24))
        }
        check()
        val story=JSONObject().put("schema_version",3).put("production_mode","flow_commands").put("character_mode","original")
            .put("content_profile",spec.optString("content_profile")).put("supporting_characters",support).put("channel",channel).put("category",category).put("title",outline.getString("title")).put("tagline",outline.getString("tagline"))
            .put("episode_count",output.length()).put("scene_count",output.length()*24).put("duration_seconds",8).put("aspect_ratio","9:16").put("episodes",output)
        val issues=FlowProductionPack.issues(story); require(issues.isEmpty()) { "บทไม่ผ่านตรวจ: "+issues.take(4).joinToString("; ") }
        val spoken=linkedMapOf<String,String>()
        for(i in 0 until output.length()) checkDialogue(output.getJSONObject(i).getJSONArray("scenes"),i+1,spoken)
        progress(100,"ตรวจบทครบแล้ว พร้อมส่งออก Flow")
        return story
    }
    companion object {
        /** Ignore punctuation/spacing differences, but allow short conversational replies. */
        fun dialogueKey(line: String): String = java.text.Normalizer.normalize(line,java.text.Normalizer.Form.NFC)
            .lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() || Character.getType(it)==Character.NON_SPACING_MARK.toInt() || Character.getType(it)==Character.COMBINING_SPACING_MARK.toInt() }
            .takeIf { it.length>=24 }.orEmpty()
        fun checkDialogue(rows: JSONArray,ep: Int,seen: MutableMap<String,String>) {
            for(i in 0 until rows.length()) {
                val row=rows.getJSONObject(i); val key=dialogueKey(row.optString("dialogue"))
                if(key.isEmpty()) continue
                val location="EP $ep ซีน ${row.getInt("scene")}"; val previous=seen[key]
                require(previous==null) { "$location บทพูดยาวซ้ำกับ $previous: «${row.optString("dialogue").take(90)}» แก้เฉพาะบทพูดและ flow_prompt ให้ตรงกัน โดยคงเหตุการณ์เดิม" }
                seen[key]=location
            }
        }
        /** Recover progress even if an older version saved 0% on failure. */
        fun checkpointPercent(folder: File): Int = runCatching {
            val eps=GeminiStoryFiles.read(File(folder,"outline.json")).getJSONArray("episodes").length()
            if(eps !in 3..12) return@runCatching 0
            var completed=0
            batches@ for(ep in 1..eps) {
                var start=1
                while(start<=24) {
                    val file=File(folder,"EP%02d_%02d.json".format(ep,start))
                    if(!file.isFile) break@batches
                    val value=GeminiStoryFiles.read(file); val size=value.getJSONArray("scenes").length()
                    require(size in 1..6 && start+size-1<=24)
                    validateBatch(value,ep,start,size,File(folder,"spec.json").takeIf { it.isFile }?.let { GeminiStoryFiles.read(it).optJSONArray("supporting_characters") } ?: JSONArray())
                    completed+=size; start+=size
                }
            }
            (completed*100/(eps*24)).coerceAtMost(99)
        }.getOrDefault(0)
        private fun string()=JSONObject().put("type","string")
        private fun integer()=JSONObject().put("type","integer")
        private fun obj(fields: Map<String,JSONObject>)=JSONObject().put("type","object").put("properties",JSONObject(fields)).put("required",JSONArray(fields.keys.toList()))
        private fun array(item: JSONObject)=JSONObject().put("type","array").put("items",item)
        fun outlineSchema(): JSONObject = obj(linkedMapOf("title" to string(),"tagline" to string(),"episodes" to array(obj(linkedMapOf(
            "ep" to integer(),"title" to string(),"tagline" to string(),"synopsis" to string(),"cover_incident" to string(),"ending" to string())))))
        fun batchSchema(): JSONObject {
            val person=obj(linkedMapOf("name" to string(),"master" to string(),"outfit" to string(),"visual_description" to string(),"screen_position" to string()))
            val fields=linkedMapOf("scene" to integer(),"duration_seconds" to integer(),"characters" to array(person))
            listOf("story_day","story_time","activity","wardrobe_event","continuity_group","location","start_state","end_state","action","speaker","dialogue","narration","image_prompt","flow_prompt").forEach { fields[it]=string() }
            return obj(linkedMapOf("scenes" to array(obj(fields))))
        }
        fun validateOutline(value: JSONObject,requested: Int) {
            require(value.getString("title").isNotBlank() && value.getString("tagline").isNotBlank()) { "โครงเรื่องขาดชื่อหรือคำโปรย" }
            val eps=value.getJSONArray("episodes")
            require(eps.length() in 3..12 && (requested==0 && eps.length() in 4..7 || requested==eps.length())) { "จำนวน EP ไม่ตรงกับที่เลือก" }
            for(i in 0 until eps.length()) { val ep=eps.getJSONObject(i); require(ep.getInt("ep")==i+1) { "เลข EP ในโครงเรื่องไม่ต่อเนื่อง" }
                listOf("title","tagline","synopsis","cover_incident","ending").forEach { require(ep.getString(it).isNotBlank()) { "โครง EP ${i+1} ขาด $it" } } }
        }
        fun validateBatch(value: JSONObject,ep: Int,start: Int,size: Int=6,support: JSONArray=JSONArray()) {
            val scenes=value.getJSONArray("scenes"); require(scenes.length()==size) { "EP $ep ซีน $start ส่งกลับไม่ครบ $size ซีน" }
            for(i in 0 until scenes.length()) {
                val scene=scenes.getJSONObject(i); require(scene.getInt("scene")==start+i && scene.getInt("duration_seconds")==8) { "เลขซีนหรือเวลาไม่ถูกต้อง" }
                for(field in listOf("story_day","story_time","activity","wardrobe_event","continuity_group","location","start_state","end_state","action","image_prompt","flow_prompt")) require(scene.getString(field).isNotBlank()) { "EP $ep ซีน ${start+i} ขาด $field" }
                listOf("speaker","dialogue","narration").forEach { scene.getString(it) }
                // Reuse production identity/speaker validation while ignoring the expected partial duration.
                val copy=JSONObject(scene.toString()).put("scene",1)
                val sample=JSONObject().put("supporting_characters",support).put("title","ตรวจซีน").put("episodes",JSONArray().put(JSONObject().put("ep",1).put("scenes",JSONArray().put(copy))))
                val issues=FlowProductionPack.issues(sample).filterNot { it.contains("ยาว 8 วินาที") }
                require(issues.isEmpty()) { "EP $ep ซีน ${start+i}: "+issues.take(3).joinToString("; ") }
            }
        }
    }
}
