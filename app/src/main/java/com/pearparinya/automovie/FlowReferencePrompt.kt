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
        return text.replace(Regex("(?:MASTERS[/\\\\])?(SUPPORT_[A-Fa-f0-9]{8})_MASTER(?:[.](?:png|jpe?g|webp))?",RegexOption.IGNORE_CASE)) { alias(it.groupValues[1].lowercase()) }
    }
    fun header(people: List<JSONObject>): String {
        val ids=people.map { FlowCast.id(it) ?: error("ไม่รู้จักภาพอ้างอิงตัวละคร") }
        require(ids.isNotEmpty() && ids.distinct().size==ids.size)
        return "แนบภาพตัวละคร: "+people.joinToString(" + ") { it.getString("name") }+"\nREFERENCE BINDING — จับคู่ภาพที่แนบกับตัวละคร:\n"+
            people.mapIndexed { index,p -> val id=ids[index]
                "${alias(id)} = ภาพอ้างอิงของ ${p.getString("name")}\n${FlowProductionPack.dna[id] ?: p.getString("visual_description")}\nชุดในฉาก: ${p.optString("outfit")} | ตำแหน่ง: ${p.optString("screen_position", "ตามองค์ประกอบภาพ")}"
            }.joinToString("\n")+"\nใช้ภาพแนบเป็นต้นฉบับใบหน้าและรูปร่าง เปลี่ยนเสื้อผ้าตามฉาก เหตุการณ์ต่อเนื่องใช้ชุดเดิม\nรหัส CHARACTER เป็นชื่ออ้างอิงในคำสั่ง ไม่ใช่ชื่อไฟล์ ไม่ต้องเปลี่ยนชื่อรูปจริง และไม่อิงลำดับอัปโหลด ตรวจว่าจับคู่ถูกคนก่อนสร้าง\nOnly these listed adult characters appear. Bind each attached reference exclusively to its named character label; no identity mixing.\n"
    }
    fun people(scene: JSONObject): List<JSONObject> = scene.getJSONArray("characters").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    fun scene(scene: JSONObject,ep: Int,kind: String?=null): String {
        val key="EP%02d_SC%03d".format(ep,scene.getInt("scene"))
        val binding=if(kind=="video") "แนบภาพฉาก: $key เป็นเฟรมเริ่มต้น • ตัวละคร: "+people(scene).joinToString(" + ") { it.getString("name") }+"\n"+header(people(scene)).replace("แนบภาพตัวละคร:","ตัวละครที่ต้องอยู่ในภาพฉาก:") else header(people(scene))
        val intro=binding+"\n=== $key | 8 seconds | 9:16 ===\nเหตุการณ์: ${scene.getString("action")}\nสถานที่: ${scene.getString("location")}\nสถานะต้น: ${scene.getString("start_state")}\nสถานะจบ: ${scene.getString("end_state")}\nผู้พูด: ${scene.optString("speaker")}\nบทพูด: ${scene.optString("dialogue")}\nเสียงบรรยาย: ${scene.optString("narration")}\n${FlowVisualDirection.identity}\n${FlowVisualDirection.color}\n"
        val image=if(kind==null || kind=="image") "\nIMAGE PROMPT (Flow):\n"+normalize(scene.getString("image_prompt"))+"\n" else ""
        val video=if(kind==null || kind=="video") "\nVIDEO PROMPT • แม่แบบหลังตรวจภาพเท่านั้น\nแนบภาพฉากที่ผ่านการตรวจของ $key เป็น START FRAME ก่อนใช้ ห้ามใช้ MASTER แทนภาพฉาก รายการด้านบนเป็นแผนตัวละคร ต้องยืนยันตำแหน่งและผู้พูดกับภาพจริงในแอปก่อนเปิดคำสั่งวิดีโอพร้อมใช้\nUse the reviewed scene image as the start frame. Preserve its existing faces, proportions, outfits, lighting, composition and props. Animate only the stated action and speech; do not recreate the characters or change wardrobe.\n"+normalize(scene.getString("flow_prompt"))+"\nFINAL START FRAME PRIORITY: The reviewed scene frame controls the visible appearance and composition. Any conflicting recreation, wardrobe or position instructions above must not change that frame. Verify speaker mapping against the actual image before generation.\n" else ""
        return intro+image+video+"\n"+FlowVisualDirection.referencePriority+"\n"+FlowVisualDirection.color+"\n"+FlowVisualDirection.referenceReview+"\n"
    }
}
