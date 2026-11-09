package com.pearparinya.automovie

/** Describe visible proportions without inferring measurements or reusing another person's build. */
internal object BodyReferencePolicy {
    const val preciseIdentity = "REFERENCE BODY MATCH — For nonhuman characters, apply human anatomical terms only when visible and applicable: preserve the original form, texture, fur, feathers, scales, metal, light, markings, limbs and silhouette without adding human anatomy. Map every requested character to their own independent original MASTER identity (The Man, Woman 1 and Woman 2 when present). Use each person's own facial geometry, hairstyle, hair length/color, shoulder span, torso length, chest/bust volume relative to shoulders and waist, waist width, hip width, upper-arm thickness and visible limb proportions. Preserve the source silhouette at comparable framing and pose. Fit the planned garment around that body; never change the body to fit a garment. Keep the two women distinct. Glamorous editorial hourglass styling describes presentation only where supported by that person's reference; do not enlarge, shrink, lift, slim, average or transfer anatomy. Perspective, pose and fabric may change apparent volume; do not compensate by reshaping anatomy. Use natural eye-level perspective; do not stretch, elongate, widen, squeeze, warp or reshape the face, head, neck or body. Avoid wide-angle face or body distortion and extreme foreshortening. Recompose independently for each aspect ratio; never stretch or squeeze an existing image to fit. Original MASTER overrides a drifted generated frame. Compare face, hair and these proportions before accepting; 1:1 is a visual target, not a measured or guaranteed match."
    const val imageIdentityLock = "FINAL ORIGINAL MASTER IDENTITY LOCK FOR EVERY CHARACTER: For nonhuman forms, use only features present in that character’s own reference; do not impose human anatomy. Use each person's own attached MASTER for face, hair, skin tone and visible body proportions; do not mix or replace identities. Preserve face shape, eyes, eyebrows, nose, lips, jaw and hairstyle, and the natural silhouette including shoulders, chest, waist, hips and limbs. Do not enlarge, shrink, reshape or transfer features; do not guess concealed anatomy. For every outfit change, fit clothing naturally; never slim, reduce or reshape the body. Do not stretch, elongate, widen, squeeze or warp the face, head, neck or body. Use natural eye-level perspective without wide-angle distortion. Recompose independently for each aspect ratio; do not stretch a prior image. Compare each generated person against the original MASTER; exact identity or proportions cannot be guaranteed by a prompt."
    val fields = listOf("body" to "รูปร่างโดยรวม", "shoulders_torso" to "ไหล่และความยาวลำตัว",
        "chest_waist" to "ความสัมพันธ์ทรวงอกและเอว", "hips_limbs" to "สะโพก แขนและขาที่เห็น", "reference_limits" to "ส่วนที่มองไม่เห็น / ข้อจำกัดมุมภาพ")
    val guide = "ยึดโครงร่างจาก MASTER ตรงรายคน: ความสัมพันธ์ของไหล่ ทรวงอก เอว สะโพก ความยาวลำตัวและแขนขาที่มองเห็น ให้เสื้อผ้ารับกับรูปร่างเดิมตามธรรมชาติ ไม่ปรับรูปร่างให้เข้ากับชุด ไม่ทำให้ผอมหรือเพิ่มสัดส่วนเอง ไม่ใช้รูปร่างคนหนึ่งแทนอีกคน คำนึงถึงมุมกล้อง ท่าทางและเนื้อผ้า ไม่เดาขนาดจริงหรือรายละเอียดส่วนที่ภาพไม่แสดง ภาพฉากก่อนหน้าใช้ดูสถานที่และอุปกรณ์ ไม่ใช้แทน MASTER ด้านรูปร่าง"
    fun singleCheck(name: String, reference: String, profile: String, outfit: String, aspect: String): String = """
สร้างภาพทดสอบหนึ่งภาพของตัวละคร $name เพียงคนเดียว ใช้ $reference ที่แนบเป็นต้นฉบับ
${ImagePromptPolicy.referenceAppeal}
${EditorialImagePolicy.referenceRendering}
ข้อมูลรายคน: $profile
$guide
ชุดของฉากที่จะใช้จริง: $outfit
ยืนผ่อนคลาย หันหน้าหากล้อง แขนไม่บังลำตัว ระยะศีรษะถึงเข่า มุมกล้องระดับสายตา พื้นหลังเรียบ แสงนุ่มสม่ำเสมอ ภาพถ่ายสมจริง $aspect ไม่มีข้อความหรือลายน้ำ
ภาพนี้ใช้ตรวจเทียบใบหน้าและโครงร่างกับ MASTER ก่อนสร้างฉากหลายคน ไม่แทนที่ MASTER ต้นฉบับโดยอัตโนมัติ หากสร้างไม่ได้ให้แจ้งตามจริงและหยุด
    """.trimIndent()
}

internal data class BodyReviewStamp(val sceneHash: String, val masterHash: String, val outfit: String) {
    fun matches(scene: String, master: String, clothes: String) =
        sceneHash.isNotBlank() && masterHash.isNotBlank() && sceneHash == scene && masterHash == master && outfit == clothes
}
