package com.pearparinya.automovie

import org.json.JSONObject

/** Labels describe attached people, never upload order or filesystem names. */
internal object FlowReferencePrompt {
    fun alias(id: String)="CHARACTER_${id.uppercase()}"
    fun normalize(raw: String): String {
        var text=raw
        FlowProductionPack.names.keys.forEach { id ->
            text=text.replace(Regex("(?:MASTERS[/\\\\])?${id}_MASTER(?:[.](?:png|jpe?g|webp))?",RegexOption.IGNORE_CASE),alias(id))
        }
        return text
    }
    fun header(people: List<JSONObject>): String {
        val ids=people.map { StoryIdentity.keyFor(it.getString("name")) ?: error("ไม่รู้จักตัวละคร") }
        require(ids.isNotEmpty() && ids.distinct().size==ids.size && ids.all { it in FlowProductionPack.names })
        return "แนบภาพตัวละคร: "+ids.joinToString(" + ") { FlowProductionPack.names.getValue(it) }+"\nREFERENCE BINDING — จับคู่ภาพที่แนบกับตัวละคร:\n"+
            people.mapIndexed { index,p -> val id=ids[index]
                "${alias(id)} = ภาพอ้างอิงของ ${FlowProductionPack.names.getValue(id)}\n${FlowProductionPack.dna.getValue(id)}\nชุดในฉาก: ${p.optString("outfit")} | ตำแหน่ง: ${p.optString("screen_position", "ตามองค์ประกอบภาพ")}"
            }.joinToString("\n")+"\nรหัส CHARACTER เป็นชื่ออ้างอิงในคำสั่ง ไม่ใช่ชื่อไฟล์ ไม่ต้องเปลี่ยนชื่อรูปจริง และไม่อิงลำดับอัปโหลด ตรวจว่าจับคู่ถูกคนก่อนสร้าง\nOnly these listed adult characters appear. Bind each attached reference exclusively to its named character label; no identity mixing.\n"
    }
    fun people(scene: JSONObject): List<JSONObject> = scene.getJSONArray("characters").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    fun scene(scene: JSONObject,ep: Int,kind: String?=null): String {
        val key="EP%02d_SC%03d".format(ep,scene.getInt("scene"))
        val intro=header(people(scene))+"\n=== $key | 8 seconds | 9:16 ===\nเหตุการณ์: ${scene.getString("action")}\nสถานที่: ${scene.getString("location")}\nสถานะต้น: ${scene.getString("start_state")}\nสถานะจบ: ${scene.getString("end_state")}\nผู้พูด: ${scene.optString("speaker")}\nบทพูด: ${scene.optString("dialogue")}\nเสียงบรรยาย: ${scene.optString("narration")}\n${FlowVisualDirection.identity}\n${FlowVisualDirection.color}\n"
        val image=if(kind==null || kind=="image") "\nIMAGE PROMPT (Flow):\n"+normalize(scene.getString("image_prompt"))+"\n" else ""
        val video=if(kind==null || kind=="video") "\nVIDEO PROMPT (Flow • ตรวจผู้พูดและตำแหน่งกับภาพจริงก่อนสร้าง):\n"+normalize(scene.getString("flow_prompt"))+"\n" else ""
        return intro+image+video+"\n"+FlowVisualDirection.referencePriority+"\n"+FlowVisualDirection.color+"\n"+FlowVisualDirection.referenceReview+"\n"
    }
}
