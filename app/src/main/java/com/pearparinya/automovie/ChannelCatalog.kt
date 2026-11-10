package com.pearparinya.automovie

import org.json.JSONObject

internal object ChannelCatalog {
    const val channel = "ร้อยฉาก"
    private val legacyChannels = setOf("สตอรี่ทรงโต - ซีรีส์ลับฉบับผู้ใหญ่", "สตอรี่หลังบ้าน - ซีรีส์สั้นดราม่าผัวเมีย")
    fun currentChannel(value: String): String = if (value.trim() in legacyChannels || value.trim()=="ร้อยฉาก - ซีรีส์สั้นหลากชีวิต") channel else value
    fun updateLegacyChannel(value: JSONObject): JSONObject {
        if (value.has("channel")) value.put("channel", currentChannel(value.optString("channel")))
        return value
    }
    data class MainCategory(val id: String,val name: String,val style: String,val tone: String,val icon: String,val direction: String) {
        val label get() = "$name ($style)"
        val menuLabel get() = if(name.trim().equals(style.trim(),ignoreCase=true)) "$icon $name" else "$icon $name\n($style)"
    }
    val mainCategories = listOf(
        MainCategory("domestic_secrets","ความลับในรั้วบ้าน","ดราม่าครอบครัว • ความลับ • หักมุม","drama","🏡","Family drama anchored in the home: conflicting loyalties, a meaningful household secret and a credible twist. Family relationships are non-sexual. Vary the household, adult roles, secret and resolution; never force an affair or inheritance plot every time."),
        MainCategory("betrayal_rivalry","ชิงรักหักสวาทและทรยศ","ดราม่าความรัก • รักสามเส้า • หักหลัง","drama","💔","Relationship drama about trust, rivalry and betrayal. Build layered adult motives, consequences and a clear emotional resolution. A love triangle is an option, not a compulsory preset. Do not assign permanent spouse or villain roles to the reference actors."),
        MainCategory("office_career","ที่ทำงานและสายอาชีพ","ดราม่าในที่ทำงาน • ความรัก • ชิงอำนาจ","drama","🏢","Workplace drama about professional ambitions, affection and power. Give the work conflict a concrete stake and credible occupation-specific setting. Vary the profession and hierarchy; do not always choose a boss-secretary affair. Do not turn the main story into a sitcom."),
        MainCategory("high_society_underworld","สังคมไฮโซและวงการสีเทา","ดราม่าหรูหรา • อาชญากรรม • ระทึกขวัญ","drama","💎","Elegant high-society crime suspense: public image, concealed interests, pressure and a consequential reveal. Use tension and restrained, location-appropriate sound rather than comedy stings. Do not provide actionable criminal methods or graphic violence."),
        MainCategory("revenge_disguise","ล้างแค้นและปลอมตัว","ดราม่าเข้มข้น • สืบความจริง • หักเหลี่ยม","drama","🔥","Intense revenge, investigation or disguise drama: a personal wrong, strategic discovery, a fair but surprising reversal and consequences. Disguise changes the role or cover story, not the actor's face/body identity. Vary the grievance, evidence and ending without adding criminal tutorials."),
        MainCategory("age_status_romance","รักต่างวัยและสถานะต่างขั้ว","โรแมนติกดราม่า • ต่างวัย • ต่างฐานะ","drama","❤️","Consensual adult romantic drama across age or social status: competing expectations, mutual agency and earned emotional development. Everyone is 25+. Financial dependence is not consent; no sexual debt repayment. Vary occupations and obstacles without forcing a patron-student premise."),
        MainCategory("office_sitcom","ออฟฟิศป่วนฮา","ซิตคอม • ตลกสถานการณ์ • มุกในที่ทำงาน","comedy","😂","Workplace sitcom: an everyday work problem, contrasting colleagues, setup, escalating misunderstandings and a satisfying comic payoff. Invent a fresh office incident and roles rather than prescribing clumsy secretary, scared boss or coffee rivalry. Keep the primary tone playful and the setting work-related."),
        MainCategory("neighborhood_comedy","เพื่อนบ้านสายฮาและชีวิตประจำวัน","คอมเมดี้เบาสมอง • เรื่องใกล้ตัว • อบอุ่น","comedy","🏘️","Warm everyday neighborhood comedy: a small relatable inconvenience, neighbors with different habits, safe situational humor and an affectionate resolution. Vary the place, task and misunderstanding; do not require delivery, pet or watering-plant plots. Do not force crime, dark betrayal or humiliation."),
        MainCategory("romantic_misunderstanding","รักวุ่นชวนเข้าใจผิด","โรแมนติกคอมเมดี้ • ความเข้าใจผิด • รักวุ่นวาย","comedy","💞","Romantic comedy: a plausible adult misunderstanding, contrasting wants, playful chemistry, clarification and a romantic or hopeful payoff. Both romance and comedy remain central. Vary the trigger and resolution; mistaken identity and wrong appointments are optional ideas, not a fixed plot."),
        MainCategory("career_buddy_comedy","คู่ป่วนต่างอาชีพ","คอมเมดี้คู่หู • ภารกิจวุ่น • คนต่างขั้ว","comedy","🎭","Buddy comedy pairing adults from different professions or approaches on one concrete shared task. Their contrasting skills create a safe comic escalation and cooperative payoff. Romance is optional. Vary the professions and task; do not lock the pair to tutor-student or cook-partner roles."),
        MainCategory("younger_man_older_woman","รักต่างวัย | หนุ่มรุ่นน้องกับสาวรุ่นพี่","โรแมนติกดราม่า • เปิดใจใหม่ • ความต่างวัย","drama","💗","Consensual adult romance between a male lead aged 25–30 and a female lead aged 45–55, using assigned catalog ages. The woman is older by at least 15 years. Explore mutual respect, life-stage differences and earned trust. Vary occupations, conflict and ending; no fixed patron or financial-dependence plot."),
        MainCategory("older_man_younger_woman","รักต่างวัย | หนุ่มรุ่นใหญ่กับสาวรุ่นน้อง","โรแมนติกดราม่า • ต่างวัย • เปิดใจรัก","drama","💖","Consensual adult romance with a male lead aged 45–60 and a female lead aged 25–35, at least 15 years younger, based on assigned MASTER ages. For example, 50 and 25 or 30. Build mutual respect and believable character goals; vary occupations, conflicts and endings."),
        MainCategory("mystery_twist","สืบสวน ความลับ และหักมุม","สืบความจริง • ความลับ • เฉลย","drama","🔎","Mystery drama with clues, investigation and a coherent reveal. Keep all cast identities and roles consistent. Avoid graphic violence and criminal instructions."),
        MainCategory("heartwarming","อบอุ่นหัวใจ","น้ำใจ • ครอบครัว • มิตรภาพ","drama","🌤️","Heartwarming everyday story with a specific small need, a meaningful caring action, and a satisfying emotional resolution. Keep family relationships non-sexual; show warmth through behavior, not speeches."),
        MainCategory("everyday_lesson","ข้อคิดใกล้ตัว","ชีวิตประจำวัน • ทางเลือก • ข้อคิด","drama","💡","A relatable everyday choice and its visible consequence, ending with a clear takeaway through action. Avoid lectures, forced morals and added subplots."))
    private val legacyFilmCategories = listOf(
        MainCategory("film_drama","ดราม่าชีวิตและครอบครัว","ดราม่าชีวิตและครอบครัว","drama","🎭","Create grounded family and life drama. Match visuals, sound and performances to the selected story and its genre; preserve identities and continuity."),
        MainCategory("film_romance","รักและความสัมพันธ์","รักและความสัมพันธ์","drama","❤️","Create adult romance and relationships. Match visuals, sound and performances to the selected story and its genre; preserve identities and continuity."),
        MainCategory("film_comedy","ตลกและชีวิตประจำวัน","ตลกและชีวิตประจำวัน","comedy","😂","Create situational comedy and everyday life. Match visuals, sound and performances to the selected story and its genre; preserve identities and continuity."),
        MainCategory("film_mystery","สืบสวนและปริศนา","สืบสวนและปริศนา","drama","🔍","Create mystery with coherent clues and reveal. Match visuals, sound and performances to the selected story and its genre; preserve identities and continuity."),
        MainCategory("film_action","แอ็กชันและการต่อสู้","แอ็กชันและการต่อสู้","drama","⚔️","Create cinematic action with believable safe staging. Match visuals, sound and performances to the selected story and its genre; preserve identities and continuity."),
        MainCategory("film_fantasy","แฟนตาซีและพลังพิเศษ","แฟนตาซีและพลังพิเศษ","drama","✨","Create photorealistic fantasy with consistent supernatural effects, explicit power rules and guardian identity. Match visuals, sound and performances to the selected story and its genre; preserve identities and continuity."),
        MainCategory("film_horror","สยองขวัญและเรื่องลี้ลับ","สยองขวัญและเรื่องลี้ลับ","drama","👻","Create atmospheric supernatural suspense. Match visuals, sound and performances to the selected story and its genre; preserve identities and continuity."),
        MainCategory("film_business","ธุรกิจ อำนาจ และการแก้แค้น","ธุรกิจ อำนาจ และการแก้แค้น","drama","💼","Create business and revenge drama with credible motivations. Match visuals, sound and performances to the selected story and its genre; preserve identities and continuity."),
        MainCategory("film_warm","อบอุ่นหัวใจและแรงบันดาลใจ","อบอุ่นหัวใจและแรงบันดาลใจ","drama","🌤️","Create heartwarming inspiring drama. Match visuals, sound and performances to the selected story and its genre; preserve identities and continuity."),
        MainCategory("film_time","ย้อนยุคและข้ามเวลา","ย้อนยุคและข้ามเวลา","drama","⏳","Create period drama and time travel with consistent rules. Match visuals, sound and performances to the selected story and its genre; preserve identities and continuity.")
    )
    val filmCategories = listOf(
        MainCategory("cinema_fantasy","แฟนตาซีและพลังเหนือธรรมชาติ","แฟนตาซีและพลังเหนือธรรมชาติ","drama","✨","Create a cinematic story matching the selected category and situation. Preserve character identities and continuity."),
        MainCategory("cinema_horror","สยองขวัญและวิญญาณอาฆาต","สยองขวัญและวิญญาณอาฆาต","drama","👻","Create a cinematic story matching the selected category and situation. Preserve character identities and continuity."),
        MainCategory("cinema_mystery","ลึกลับ สืบสวน และปริศนา","ลึกลับ สืบสวน และปริศนา","drama","🔍","Create a cinematic story matching the selected category and situation. Preserve character identities and continuity."),
        MainCategory("cinema_legend","ตำนาน ความเชื่อ และสิ่งศักดิ์สิทธิ์","ตำนาน ความเชื่อ และสิ่งศักดิ์สิทธิ์","drama","🐉","Create a cinematic story matching the selected category and situation. Preserve character identities and continuity."),
        MainCategory("cinema_creature","สัตว์ประหลาดและอสูรในตำนาน","สัตว์ประหลาดและอสูรในตำนาน","drama","🐲","Create a cinematic story matching the selected category and situation. Preserve character identities and continuity."),
        MainCategory("cinema_curse","ไสยศาสตร์ คำสาป และกรรมตามสนอง","ไสยศาสตร์ คำสาป และกรรมตามสนอง","drama","🔮","Create a cinematic story matching the selected category and situation. Preserve character identities and continuity."),
        MainCategory("cinema_time","ย้อนยุค ข้ามเวลา และเปลี่ยนชะตา","ย้อนยุค ข้ามเวลา และเปลี่ยนชะตา","drama","⏳","Create a cinematic story matching the selected category and situation. Preserve character identities and continuity."),
        MainCategory("cinema_adventure","ผจญภัยและเอาชีวิตรอด","ผจญภัยและเอาชีวิตรอด","drama","🧭","Create a cinematic story matching the selected category and situation. Preserve character identities and continuity."),
        MainCategory("cinema_scifi","ไซไฟและโลกอนาคต","ไซไฟและโลกอนาคต","drama","🚀","Create a cinematic story matching the selected category and situation. Preserve character identities and continuity."),
        MainCategory("cinema_relationships","ดราม่าชีวิตคู่และความสัมพันธ์","ดราม่าชีวิตคู่และความสัมพันธ์","drama","💔","Create a cinematic story matching the selected category and situation. Preserve character identities and continuity.")
    )
    val personalCategories = listOf(
        MainCategory("pia_benefits","สวัสดิการและสิทธิประโยชน์","ข่าวสิทธิ • ข้อมูลใกล้ตัว","drama","📌","Explain only information explicitly supplied and verified by the user; do not invent current benefits, amounts, eligibility, deadlines or official claims. If facts are missing ask the user for them. Treat as factual explainer, not fictional news."),
        MainCategory("pia_safety","ความรู้และเตือนภัย","ความรู้ • เตือนภัย","drama","💡","Explain the user-supplied facts clearly. Do not invent scams, incidents, safety statistics or official claims; ask for evidence if missing."),
        MainCategory("pia_products","นายเปียพาช้อป","รีวิวสินค้า • ของใช้","drama","🛍️","Review only product details explicitly supplied by the user. Do not invent demonstrations, personal experience, prices, endorsements or product claims. Ask for missing product facts.")
    )
    val personalDrama = MainCategory("personal_social_drama","ละครสั้นสะท้อนสังคม","เรื่องเล่า • สะท้อนสังคม","drama","🎬","Fictional social drama based on the selected topic with empathetic characters, clear consequences and a hopeful or thought-provoking resolution. Do not portray invented claims as verified news.")
    val allCategories get() = mainCategories + personalCategories + filmCategories + legacyFilmCategories + listOf(personalDrama)
    private val genreDisplay = mapOf(
        "domestic_secrets" to ("ความลับในครอบครัว | Family Secrets" to "ความสัมพันธ์ในบ้านกับความลับที่เปลี่ยนชีวิต"),
        "betrayal_rivalry" to ("รัก ความไว้ใจ และการทรยศ | Love & Betrayal" to "ความสัมพันธ์ที่ถูกทดสอบด้วยความจริงและการตัดสินใจ"),
        "office_career" to ("ชีวิตการทำงาน | Workplace Drama" to "ความฝัน ความกดดัน และความสัมพันธ์ในอาชีพ"),
        "high_society_underworld" to ("สังคมชั้นสูงและเงื่อนงำ | High Society Suspense" to "ภาพลักษณ์ อำนาจ และความลับในสังคม"),
        "revenge_disguise" to ("ทวงความจริงและปลอมตัว | Revenge & Disguise" to "ค้นหาความจริงและเผชิญผลของการแก้แค้น"),
        "age_status_romance" to ("รักต่างวัยและต่างฐานะ | Age & Status Romance" to "ความรักของผู้ใหญ่ที่มีชีวิตและความคาดหวังต่างกัน"),
        "office_sitcom" to ("ออฟฟิศป่วน | Workplace Comedy" to "มุกจากงานและความเข้าใจผิดของเพื่อนร่วมงาน"),
        "neighborhood_comedy" to ("เพื่อนบ้านชวนฮา | Neighborhood Comedy" to "เหตุการณ์ใกล้ตัวชวนยิ้มในชุมชน"),
        "romantic_misunderstanding" to ("รักวุ่นชวนเข้าใจผิด | Romantic Comedy" to "ความรักและความเข้าใจผิดที่คลี่คลายด้วยอารมณ์ขัน"),
        "career_buddy_comedy" to ("คู่หูต่างอาชีพ | Buddy Comedy" to "คนต่างความคิดร่วมภารกิจจนเกิดเรื่องขำ"),
        "younger_man_older_woman" to ("หนุ่มรุ่นน้องกับสาวรุ่นพี่ | Later-Life Romance" to "รักต่างวัยของผู้ใหญ่ที่เรียนรู้และเคารพกัน"),
        "older_man_younger_woman" to ("หนุ่มรุ่นใหญ่กับสาวรุ่นน้อง | Older Man & Younger Woman" to "ความรักต่างวัยของผู้ใหญ่ที่ค่อย ๆ เรียนรู้และเคารพกัน"),
        "mystery_twist" to ("สืบความจริงและหักมุม | Mystery & Revelation" to "ปริศนา เบาะแส และคำตอบที่มีเหตุผล"),
        "heartwarming" to ("น้ำใจและความผูกพัน | Heartwarming Drama" to "การช่วยเหลือเล็ก ๆ ที่สร้างความเปลี่ยนแปลง"),
        "everyday_lesson" to ("ทางเลือกและข้อคิด | Everyday Lessons" to "การตัดสินใจใกล้ตัวและผลที่มองเห็นได้")
    )
    fun genreMenuLabel(category: MainCategory): String = genreDisplay[category.id]?.let { "${category.icon} ${it.first}\n${it.second}" } ?: category.menuLabel
    data class StoryShelf(val title: String, val categoryIds: List<String>) {
        val menuLabel: String get() = when(title) {
            "😂 ยิ้มได้ทุกวัน" -> "😂 คอมเมดี้และชีวิตประจำวัน\nComedy & Everyday Life"
            "❤️ ร้อยเรื่องรัก" -> "❤️ รักและความสัมพันธ์\nRomance & Relationships"
            "💔 รอยร้าวในใจ" -> "💔 ดราม่าชีวิตและครอบครัว\nLife & Family Drama"
            "🔎 ปมลับชวนติดตาม" -> "🔎 สืบสวนและปมปริศนา\nMystery & Suspense"
            "🌤️ เรื่องเล่าอบอุ่นใจ" -> "🌤️ เรื่องเล่าอบอุ่นหัวใจ\nHeartwarming Stories"
            "💡 ชีวิตสอนใจ" -> "💡 เรื่องเล่าชีวิตและข้อคิด\nLife Stories & Lessons"
            else -> title
        }
    }
    // Menu grouping is independent of saved category IDs, so old stories remain readable.
    val storyShelves = listOf(
        StoryShelf("😂 ยิ้มได้ทุกวัน", listOf("office_sitcom", "neighborhood_comedy", "career_buddy_comedy")),
        StoryShelf("❤️ ร้อยเรื่องรัก", listOf("romantic_misunderstanding", "age_status_romance", "younger_man_older_woman", "older_man_younger_woman")),
        StoryShelf("💔 รอยร้าวในใจ", listOf("domestic_secrets", "betrayal_rivalry", "office_career")),
        StoryShelf("🔎 ปมลับชวนติดตาม", listOf("mystery_twist", "revenge_disguise", "high_society_underworld")),
        StoryShelf("🌤️ เรื่องเล่าอบอุ่นใจ", listOf("heartwarming")),
        StoryShelf("💡 ชีวิตสอนใจ", listOf("everyday_lesson"))
    )
    // These are story seeds, not fixed scripts; users can also choose automatic selection.
    fun shortTopics(id: String): List<String> = when(id) {
        "cinema_fantasy" -> listOf("ผู้มีพลังพิเศษซ่อนตัว","การค้นพบพลังครั้งแรก","เวทมนตร์และจอมเวท","อัศวินวิญญาณผู้พิทักษ์","การอัญเชิญเทพหรือวิญญาณ","วัตถุและอาวุธวิเศษ","ผู้สืบทอดพลังโบราณ","สำนักฝึกฝนพลัง","สงครามระหว่างผู้มีพลัง","พลังแห่งแสงและพลังแห่งความมืด")
        "cinema_horror" -> listOf("บ้านผีสิง","ผีอาฆาตและการทวงแค้น","วิญญาณเร่ร่อน","ผีในโรงเรียนหรือโรงพยาบาล","หมู่บ้านผีสิง","ผีที่ติดตามบุคคล","คืนหลอนในสถานที่ร้าง","วิญญาณที่ต้องการความช่วยเหลือ","ผีแฝงตัวอยู่ท่ามกลางคน","การเผชิญหน้ากับสิ่งที่มองไม่เห็น")
        "cinema_mystery" -> listOf("คดีคนหาย","คดีฆาตกรรมปริศนา","นักสืบกับความลับเหนือธรรมชาติ","หมู่บ้านที่ซ่อนความลับ","ห้องหรืออาคารปริศนา","จดหมายและข้อความลึกลับ","บุคคลนิรนาม","เหตุการณ์ที่เกิดซ้ำอย่างประหลาด","การตามหาความจริงในอดีต","ปริศนาที่มีจุดหักมุม")
        "cinema_legend" -> listOf("พญานาคและเมืองบาดาล","พญาครุฑและดินแดนสวรรค์","เจ้าป่าเจ้าเขา","เทพผู้พิทักษ์สถานที่","ศาลเจ้าที่และคำบนบาน","ตำนานสิ่งศักดิ์สิทธิ์ประจำหมู่บ้าน","ขุมทรัพย์โบราณที่มีผู้เฝ้า","ดวงวิญญาณแห่งป่าและสายน้ำ","พิธีกรรมและความเชื่อพื้นบ้าน","ตำนานโบราณที่กลับมามีชีวิต")
        "cinema_creature" -> listOf("มังกรผู้พิทักษ์","เสือสมิง","งูยักษ์และอสรพิษลึกลับ","สัตว์แปลงกายเป็นมนุษย์","อสูรเฝ้าป่า","ยักษ์และอสูรโบราณ","สัตว์ประหลาดใต้ทะเล","นกวิเศษและสัตว์แห่งท้องฟ้า","สัตว์เลี้ยงที่มีพลังพิเศษ","การต่อสู้ระหว่างอสูรยักษ์")
        "cinema_curse" -> listOf("เครื่องรางลึกลับ","คำสาปจากของโบราณ","หมอผีกับการแก้คุณไสย","ผู้ใช้มนตร์ดำ","พิธีกรรมต้องห้าม","การบนบานแล้วไม่แก้บน","คำสาปที่ส่งต่อในครอบครัว","ของขลังที่มาพร้อมข้อแลกเปลี่ยน","ความโลภที่นำไปสู่หายนะ","คนทำผิดได้รับผลจากการกระทำ")
        "cinema_time" -> listOf("คนปัจจุบันย้อนสู่อดีต","คนโบราณมาอยู่ในยุคปัจจุบัน","กลับมาเกิดใหม่","ย้อนเวลาแก้ไขความผิดพลาด","ข้ามภพไปยังอีกโลก","พบตัวเองในอีกช่วงเวลา","วัตถุที่เปิดประตูข้ามเวลา","ความลับของบรรพบุรุษ","สงครามหรือเหตุการณ์ในอดีต","เปลี่ยนชะตาของคนในครอบครัว")
        "cinema_adventure" -> listOf("ติดอยู่ในป่าลึกลับ","สำรวจถ้ำต้องห้าม","เกาะร้างและการเอาชีวิตรอด","เกมปริศนาเพื่อเอาชีวิตรอด","หลบหนีจากเมืองร้าง","ตามหาสมบัติที่สาบสูญ","เดินทางสู่ดินแดนลับ","ฝ่าด่านและกับดักโบราณ","ถูกไล่ล่าโดยสิ่งลึกลับ","กลุ่มคนแปลกหน้าที่ต้องร่วมมือกัน")
        "cinema_scifi" -> listOf("ปัญญาประดิษฐ์ที่มีความลับ","หุ่นยนต์และมนุษย์","แอปพลิเคชันทำนายอนาคต","โลกอนาคตหลังภัยพิบัติ","การทดลองวิทยาศาสตร์ผิดพลาด","มนุษย์ที่ได้รับพลังจากเทคโนโลยี","โลกเสมือนจริง","สิ่งมีชีวิตจากนอกโลก","องค์กรลับควบคุมเทคโนโลยี","ประตูมิติสู่จักรวาลอื่น")
        "cinema_relationships" -> listOf("สามีนอกใจภรรยา","เมียน้อยและความลับในครอบครัว","ชู้สาวและรักซ้อน","สาวใหญ่กับความรักครั้งใหม่","ความรักที่หวังผลประโยชน์","การแย่งชิงทรัพย์สินและมรดก","คนรักหักหลังและการแก้แค้น","ความลับระหว่างสามีภรรยา","ครอบครัวแตกแยกและการให้อภัย","เริ่มต้นชีวิตใหม่หลังถูกทรยศ")
        "film_drama" -> listOf("ความลับในครอบครัว","แม่เลี้ยงเดี่ยวสู้ชีวิต","พ่อแม่กับลูกที่ห่างเหิน","พี่น้องแย่งมรดก","ชีวิตคู่และความไว้ใจ","ลูกสะใภ้กับครอบครัวสามี","คนจนที่ถูกดูถูก","วันที่ครอบครัวกลับมาคืนดีกัน")
        "film_romance" -> listOf("รักต่างวัย","รักในที่ทำงาน","แอบรักเพื่อนสนิท","รักครั้งใหม่หลังผิดหวัง","คู่กัดกลายเป็นคู่รัก","รักต่างฐานะ","คนรักเก่ากลับมา","หัวใจกับครอบครัว")
        "film_comedy" -> listOf("ออฟฟิศป่วนฮา","เพื่อนบ้านชวนปวดหัว","ส่งข้อความผิดคน","ครอบครัวสายฮา","ลูกค้ากับร้านค้าสุดวุ่น","เจ้านายกับลูกน้องคู่ปรับ","เดตแรกที่ไม่เป็นไปตามแผน","เข้าใจผิดกันทั้งหมู่บ้าน")
        "film_mystery" -> listOf("คนหายตัวไปอย่างลึกลับ","จดหมายหรือพัสดุปริศนา","ความลับในบ้านหลังเก่า","หลักฐานที่ถูกซ่อนไว้","ใครคือคนที่โกหก","คดีเก่าที่ไม่มีใครอยากพูดถึง","ตัวตนที่ถูกปลอมแปลง","พยานคนสุดท้าย")
        "film_action" -> listOf("ภารกิจช่วยคนสำคัญ","ผู้พิทักษ์ชุมชน","อดีตนักสู้ปกป้องครอบครัว","หนีการไล่ล่า","บอดี้การ์ดกับคนที่ต้องปกป้อง","ภารกิจลับทวงคืนหลักฐาน","รวมทีมต่อสู้กับผู้มีอิทธิพล","ปกป้องหมู่บ้านจากภัยคุกคาม")
        "film_fantasy" -> listOf("ปลุกพลังที่หลับใหล","อัญเชิญผู้พิทักษ์ยักษ์","เกราะพลังและนักรบในตำนาน","คนธรรมดาที่ได้รับพลังพิเศษ","ผู้ใช้พลังที่ต้องปิดบังตัวตน","โรงเรียนผู้มีพลัง","สัตว์วิญญาณคู่ใจ","ศึกชิงพลังระหว่างแสงกับเงา")
        "film_horror" -> listOf("บ้านร้างท้ายหมู่บ้าน","วิญญาณที่ต้องการบอกความจริง","คำสาปประจำตระกูล","ของเก่าที่มีเรื่องเล่า","ห้องพักต้องห้าม","คนแปลกหน้าที่ไม่มีใครจำได้","เสียงเรียกกลางดึก","เรื่องลี้ลับจากความเชื่อพื้นบ้าน")
        "film_business" -> listOf("แย่งชิงตำแหน่งผู้บริหาร","ทายาทที่ถูกกีดกัน","พนักงานธรรมดาที่ซ่อนตัวตน","ถูกใส่ร้ายในที่ทำงาน","หุ้นส่วนหักหลัง","กลับมาทวงคืนบริษัท","เปิดโปงการทุจริต","แก้แค้นด้วยความจริง")
        "film_warm" -> listOf("คนแปลกหน้าที่ยื่นมือช่วย","ลูกตอบแทนพระคุณพ่อแม่","ครูกับศิษย์ที่ไม่ยอมแพ้","เริ่มต้นชีวิตใหม่","เพื่อนแท้ในวันที่ลำบาก","ชุมชนร่วมใจกันช่วยคน","มิตรภาพระหว่างคนกับสัตว์","ความฝันเล็ก ๆ ที่เปลี่ยนชีวิต")
        "film_time" -> listOf("กลับไปแก้ไขอดีต","ตื่นขึ้นมาในอีกยุคหนึ่ง","จดหมายจากอนาคต","ย้อนวัยกลับไปเริ่มต้นใหม่","รักข้ามภพข้ามเวลา","ความลับของบรรพบุรุษ","ของโบราณพาข้ามเวลา","พบตัวเองในอีกช่วงชีวิต")
        "domestic_secrets" -> listOf("ของหายในบ้าน","ความลับที่เข้าใจผิด","มื้ออาหารชวนสงสัย","จดหมายในลิ้นชัก","มรดกที่ไม่คาดคิด","ภาพเก่าที่ทำให้เข้าใจใหม่","กุญแจของสมาชิกในบ้าน")
        "betrayal_rivalry" -> listOf("ข้อความส่งผิดคน","เจอแฟนเก่าโดยบังเอิญ","คำโกหกที่จับได้","ความจริงจากเพื่อนสนิท","คำสัญญาที่ผิดนัด","ความไว้วางใจที่ต้องพิสูจน์","การตัดสินใจในวันสำคัญ")
        "office_career" -> listOf("ประชุมผิดห้อง","ความดีที่ไม่มีใครเห็น","ลูกค้าคนสำคัญ","เพื่อนร่วมงานเข้าใจผิด","งานชิ้นสุดท้ายก่อนเลิกงาน","ผลงานที่ถูกเข้าใจผิด","ทางเลือกก่อนรับตำแหน่งใหม่")
        "high_society_underworld" -> listOf("แขกปริศนาในงานเลี้ยง","ของหรูที่มีเรื่องซ่อน","คนธรรมดาในงานไฮโซ","คำเชิญลึกลับ","ความลับในกระเป๋าหรู","ภาพถ่ายในงานประมูล","คำสัญญาในงานเลี้ยง")
        "revenge_disguise" -> listOf("กลับมาพิสูจน์ตัวเอง","คนแปลกหน้าที่เคยรู้จัก","แผนเอาคืนที่พลิกล็อก","จดหมายจากอดีต","เปิดโปงความเข้าใจผิด","หลักฐานที่หายไป","กลับมาทวงความยุติธรรม")
        "age_status_romance" -> listOf("เดตแรกต่างวัย","เข้าใจผิดเรื่องฐานะ","ดูแลกันในวันธรรมดา","ของขวัญแทนคำขอบคุณ","คนต่างวัยช่วยกันแก้ปัญหา","มุมมองต่างกันในวันสำคัญ","จดหมายขอบคุณที่ไม่คาดคิด")
        "office_sitcom" -> listOf("ส่งข้อความผิดกลุ่ม","ประชุมเปิดไมค์ค้าง","ของกินในตู้เย็นหาย","เตรียมเอกสารผิดชุด","เจ้านายได้ยินผิด","พรีเซนต์ผิดไฟล์","อีเมลตอบทุกคน")
        "neighborhood_comedy" -> listOf("รับพัสดุผิดบ้าน","เสียงปริศนาข้างบ้าน","ยืมของแล้วเรื่องบาน","ตะกร้าหน้าบ้านสลับกัน","ทักคนผิดหน้าร้าน","ป้ายหน้าบ้านสลับกัน","ของฝากส่งผิดคน")
        "romantic_misunderstanding" -> listOf("เข้าใจผิดว่าเป็นแฟน","นัดเจอผิดคน","ของขวัญผิดเจ้าของ","แชตผิดห้อง","คำสารภาพที่เข้าใจผิด","ข้อความลับจากคนใกล้ตัว","คู่เดตจำเป็น")
        "career_buddy_comedy" -> listOf("ลูกค้ากับช่างคู่กัด","ภารกิจส่งของ","สองอาชีพช่วยกันแก้ปัญหา","ช่างกับลูกค้าต่างความคิด","แผนงานสลับบทบาท","ภารกิจแข่งกับเวลา","คู่หูรับงานผิดที่")
        "younger_man_older_woman" -> listOf("คำชวนที่คิดไม่ถึง","รุ่นน้องช่วยงาน","ความห่วงใยที่ตีความผิด","อาหารมื้อแรกที่ทำด้วยกัน","คำขอบคุณที่ไม่ทันพูด","ร่มคันเดียวในวันฝนตก","พบกันอีกครั้งหลังเลิกงาน")
        "older_man_younger_woman" -> listOf("พบกันในวันธรรมดา","เพื่อนร่วมงานต่างวัย","คำขอบคุณที่เปลี่ยนความสัมพันธ์","งานอดิเรกร่วมกัน","มุมมองต่างวัยในวันสำคัญ","ความห่วงใยที่ไม่คาดคิด","โอกาสครั้งใหม่ของหัวใจ")
        "mystery_twist" -> listOf("เบาะแสชิ้นเดียว","คนที่หายไปไหน","ความจริงที่ไม่เหมือนที่คิด","กุญแจที่ไม่มีเจ้าของ","เสียงจากห้องข้าง ๆ","รอยเท้าในภาพเก่า","ข้อความที่ปรากฏซ้ำ")
        "heartwarming" -> listOf("น้ำใจเล็ก ๆ ในวันยาก","คนแปลกหน้าช่วยกัน","ของขวัญที่มีความหมาย","มื้ออาหารแบ่งปัน","ความช่วยเหลือระหว่างทาง","จดหมายจากคนเคยช่วย","รอยยิ้มในวันฝนตก")
        "everyday_lesson" -> listOf("ฟังให้จบก่อนตัดสิน","ความซื่อสัตย์เรื่องเล็ก","โอกาสที่สอง","คำขอโทษที่ไม่สาย","สิ่งเล็ก ๆ ที่มองข้าม","คำพูดที่เปลี่ยนวันหนึ่ง","คืนของที่เก็บได้")
        else -> emptyList()
    }
    private fun main(category: String) = allCategories.firstOrNull { category==it.label || category==it.name }
    private fun main(value: JSONObject) = main(value.optString("category")) ?: allCategories.firstOrNull { it.id==value.optString("category_id") }
    val acceptedCategories get() = allCategories.flatMap { listOf(it.label,it.name) } + topics.map { category(it) } + groups.map { it.title }
    private const val filmCraft = "FILMMAKING DIRECTION: Develop a clear premise, character goals and stakes, motivated actions, coherent setup and payoff. Direct natural performances and meaningful visual storytelling; use composition, lighting, camera and sound appropriate to the selected genre and location. Make the title, synopsis, cover and scenes agree with the actual story. Keep each shot continuous for 8 seconds; avoid filler and unrelated events."
    fun genre(value: JSONObject): String = main(value)?.let {
        if(it.id.startsWith("pia_")) "FACTUAL CONTENT: ${it.direction} Preserve only the user-supplied facts and approved speech; do not invent events, product experiences or factual claims."
        else "SELECTED GENRE: ${it.id}. ${it.direction} Preserve the written scene and assigned speech; genre direction does not add events, characters or sounds. $filmCraft"
    }.orEmpty()
    private const val creative = "MAIN CATEGORY ONLY: Invent a fresh Thai title, central incident, adult occupations/relationships, obstacles, escalation and ending inside the selected category. Treat a user-selected situation as the creative starting point, not a fixed script; vary its characters and resolution. Maintain the selected setting and primary tone through outline, every scene, acting, covers and sound. A twist must deepen this genre rather than switch it. Use only registered reference actors and decide roles freely; names/reference identities are not automatic husband-wife-affair roles. After outline approval keep the agreed roles, motives and continuity stable across EPs."
    data class Topic(val id: Int, val title: String, val premise: String, val tone: String = "drama")
    data class Group(val title: String, val topics: List<Topic>)
    val groups = listOf(
        Group("ความลับในรั้วบ้าน (Domestic Secrets)", listOf(
            Topic(1,"🧹 Maid's Secret","เศรษฐีหนุ่มกับแม่บ้านสาวผู้ซ่อนเป้าหมายแอบแฝง"),
            Topic(2,"🏡 Stepmom's Trap","แม่เลี้ยงกับลูกเลี้ยงผู้ใหญ่เผชิญเกมมรดกและความลับในบ้าน ไม่มีความรักหรือความสัมพันธ์ทางเพศระหว่างสมาชิกครอบครัว"),
            Topic(3,"🎓 Private Tutor","ติวเตอร์สาวกับลูกชายเจ้าของบ้านวัยทำงานอายุ 25 ปีขึ้นไป ผู้กลับมาเรียนเสริม"),
            Topic(4,"🚗 Chauffeur's Game","คุณนายกับคนขับรถหนุ่มที่รู้ความลับของเธอ"))),
        Group("ชิงรักหักสวาท & ทรยศ (Betrayal & Rivalry)", listOf(
            Topic(5,"👩‍🏫 Forbidden Desire","สาวใหญ่กับชายหนุ่มอายุ 25 ปีขึ้นไป เผชิญความต่างวัยและความคาดหวัง"),
            Topic(6,"👯‍♀️ Sisters' Rivalry","พี่น้องผู้ใหญ่แข่งขันเรื่องความรักและศักดิ์ศรี ไม่มีความสัมพันธ์ทางเพศระหว่างพี่น้อง"),
            Topic(7,"🍷 Ex-Lover's Return","แฟนเก่ากลับมาทวงคืนสามีของนางเอก"),
            Topic(8,"🤝 Best Friend's Betrayal","เพื่อนสนิทที่ทำทีหวังดีแต่ซ่อนความสัมพันธ์กับสามีเพื่อน"))),
        Group("ที่ทำงาน & สายอาชีพ (Office & Career)", listOf(
            Topic(9,"🏢 Office Affair","ประธานหนุ่มกับเลขาสาวจอมวางแผน"),
            Topic(10,"🏋️‍♀️ Fitness Flirt","เทรนเนอร์หนุ่มกับลูกค้าสาวไฮโซที่มีเป้าหมายซ่อนเร้น"),
            Topic(11,"📸 Private Studio","ช่างภาพกับนางแบบผู้ซ่อนความลับในสตูดิโอ"),
            Topic(12,"🩺 Night Shift Secret","หมอหรือคนไข้ VIP กับพยาบาลผู้พบความลับระหว่างกะดึก"))),
        Group("สังคมไฮโซ & วงการสีเทา (High Society & Underworld)", listOf(
            Topic(13,"👑 Mafia's Mistress","หญิงคนรักของเจ้าพ่อกับบอดี้การ์ดที่ต้องเลือกระหว่างหน้าที่และความจริง"),
            Topic(14,"🃏 Casino Queen","เจ้าแม่คาสิโนกับเซียนพนันหน้าใหม่ที่ท้าทายอำนาจ"),
            Topic(15,"💎 Socialite's Secret","ไฮโซสาวสร้างภาพครอบครัวอบอุ่นแต่ซ่อนคนรักชายอายุ 25 ปีขึ้นไป"),
            Topic(16,"💰 Wealthy Widow","แม่ม่ายผู้รับมรดกกับญาติฝ่ายสามีที่จ้องจับผิด"))),
        Group("ล้างแค้น & ปลอมตัว (Revenge & Disguise)", listOf(
            Topic(17,"🔥 Sweet Vengeance","หญิงสาวเปลี่ยนภาพลักษณ์กลับมาทวงศักดิ์ศรีจากแฟนเก่าและมือที่สาม คงรูปร่างตาม MASTER ไม่เปลี่ยนขนาดร่างกาย"),
            Topic(18,"🕵️‍♀️ Undercover Spy","หญิงสาวแฝงตัวเป็นแม่บ้านหรือเลขาเพื่อสืบความจริง"),
            Topic(19,"🎭 Fake Marriage","หนุ่มถังแตกกับหญิงสาวที่เสนอการแต่งงานหลอกเพื่อผลประโยชน์"),
            Topic(20,"💔 The Blackmail","หญิงสาวรวบรวมหลักฐานเอาผิดคนข่มขู่ด้วยข้อมูลส่วนตัว ไม่สร้างภาพลับหรือฉากบังคับทางเพศ"))),
        Group("รักต่างวัย & สถานะต่างขั้ว (Age Gap & Status Clash)", listOf(
            Topic(21,"💸 Sugar Mommy","นักธุรกิจหญิงกับนักศึกษาระดับบัณฑิตชายอายุ 25 ปีขึ้นไป ความรักและผลประโยชน์โดยสมัครใจ ไม่มีการแลกเพศกับเงิน"),
            Topic(22,"🔑 The Landlady","เจ้าของหอกับผู้เช่าค้างค่าเช่าซึ่งตกลงทำงานซ่อมแซมหรือบริหารหอเพื่อชำระหนี้ ไม่มีการแลกเพศกับหนี้"),
            Topic(23,"🎙️ Idol's Double Life","สตรีมเมอร์สาวสืบหาคนสะกดรอยและปกป้องชีวิตส่วนตัว ไม่ทำให้การคุกคามดูโรแมนติก"),
            Topic(24,"🎬 Backstage Drama","นางเอกกับนักแสดงดาวรุ่งหญิงอายุ 25 ปีขึ้นไป แข่งขันแย่งงานและความรัก"))),
        Group("ออฟฟิศป่วนฮา (Office Sitcom)", listOf(
            Topic(25,"📎 Clumsy Secretary","เลขาสาวทรงโตสุดโก๊ะทำแฟ้มตกหรือกาแฟหกบนโต๊ะ ประธานหนุ่มช่วยเก็บจนสลับเอกสารผิดชุดและประชุมผิดเรื่อง มุกจากความซุ่มซ่ามและคำพูดผิดจังหวะ เสื้อผ้าทึบและอยู่ครบ ไม่ใช้เสื้อเปียกแนบเนื้อหรือมุมกล้องแอบมอง", "comedy"),
            Topic(26,"💼 The Scaredy Boss","เจ้านายสาวทรงโตมาดดุกลัวจิ้งจกจนหลบหลังลูกน้องหนุ่ม แล้วพยายามรักษามาดระหว่างภารกิจจับสัตว์ออกอย่างปลอดภัย ใช้มุกสลับบทคนกล้ากับคนกลัว ไม่กระโดดกอดหรือสัมผัสโดยไม่ยินยอม", "comedy"),
            Topic(27,"☕ Pantry Rivalry","สาวออฟฟิศทรงโตสองคนแข่งชงกาแฟและอวดความสามารถให้พนักงานใหม่ประทับใจ แต่สลับน้ำตาลกับเกลือจนต้องร่วมมือแก้สถานการณ์ มุกประชันไหวพริบและปฏิกิริยาโดยไม่เน้นหน้าอก", "comedy"))),
        Group("เพื่อนบ้านสายฮา & ชีวิตประจำวัน (Neighborhood Comedy)", listOf(
            Topic(28,"🏠 The Oblivious Neighbor","สาวข้างบ้านทรงโตใส่ชุดอยู่บ้านทึบกระชับที่ออกแบบใหม่ รดน้ำต้นไม้เพลินจนฉีดน้ำผิดทิศ หนุ่มข้างบ้านประหม่าและสื่อสารผิดจนเกิดเรื่องชวนขำ เสื้อไม่โปร่งไม่เปียกและไม่เปิดร่างกาย", "comedy"),
            Topic(29,"📦 Delivery Disaster","ลูกค้าสาวทรงโตเพิ่งตื่นสวมชุดอยู่บ้านทึบเรียบร้อย รับพัสดุแบบงัวเงียแล้วสลับของกับเพื่อนบ้าน พนักงานส่งของสุภาพพยายามอธิบายแต่ยิ่งเข้าใจผิด มุกจากป้ายกล่องและบทสนทนา ไม่มีเสื้อผ้าหลุด", "comedy"),
            Topic(30,"🐶 Pet Lover's Panic","สาวทรงโตพาสุนัขไปสวน สุนัขวิ่งตามลูกบอลจนสายจูงพันม้านั่ง เพื่อนบ้านช่วยคลายปมอย่างปลอดภัย กลับเข้าใจคำสั่งสุนัขผิดกันทั้งกลุ่ม สัตว์ไม่บาดเจ็บ เสื้อผ้าคงเดิมและไม่เปิดร่างกาย", "comedy"))),
        Group("สถานการณ์พาซวยชวนคิดลึก (Awkward Situations)", listOf(
            Topic(31,"🏋️‍♀️ Gym Fail","สาวทรงโตใช้เครื่องออกกำลังกายผิดโหมดจนจังหวะไม่ตรงกับเทรนเนอร์ เทรนเนอร์หยุดเครื่องและสาธิตอย่างปลอดภัย เกิดมุกเข้าใจศัพท์ฟิตเนสผิด ใช้ชุดกีฬาทึบที่รองรับร่างกาย ไม่จัดท่าหรือถ่ายเน้นอวัยวะ", "comedy"),
            Topic(32,"🏕️ Camping Chaos","สาวทรงโตตั้งแคมป์ครั้งแรก กางเต็นท์กลับด้านและประกอบเก้าอี้ผิด หนุ่มข้างเต็นท์มาช่วยแต่หยิบคู่มือผิดเล่ม จบด้วยการช่วยกันแก้ ไม่มีเสื้อไหม้ การบาดเจ็บ หรือการสาธิตวิธีจุดไฟเสี่ยง", "comedy"),
            Topic(33,"🍻 Drunk Confession","หญิงสาวผู้ใหญ่มึนเล็กน้อยสารภาพรักผิดชื่อ เพื่อนสนิทช่วยชี้แจงและจัดคนขับที่ไม่ดื่มพากลับอย่างปลอดภัย ขำจากคำพูดคลาดเคลื่อน ไม่ล้อเลียนผู้หมดสติ ไม่มีการสัมผัสเชิงเพศหรือฉวยโอกาสจากคนเมา", "comedy"))),
        Group("ครู/ติวเตอร์สายป่วน (The Clueless Tutor)", listOf(
            Topic(34,"📚 The Clueless Tutor","ติวเตอร์สาวทรงโตกับผู้เรียนชายวัยทำงาน ทุกคนอายุ 25 ปีขึ้นไป ตั้งใจสอนเสริมแต่ผู้เรียนประหม่าและแปลโจทย์ผิดเป็นเรื่องทำงาน ติวเตอร์แก้ด้วยตัวอย่างที่ยิ่งชวนเข้าใจผิด ไม่มีนักเรียนผู้เยาว์หรือมุกจ้องหน้าอก", "comedy"),
            Topic(35,"🍳 Cooking Disaster","สาวทรงโตอยากโชว์ฝีมือทำอาหารให้แฟนหนุ่ม แต่หยิบเครื่องปรุงผิดและทำแป้งฟุ้ง ทั้งคู่ช่วยกันแก้จนกลายเป็นเมนูใหม่ ใช้มุกคู่รักและพร็อพ ไม่ถ่ายการกระเพื่อมของหน้าอก ไม่มีไฟลุก เสื้อไหม้ หรือการวิ่งหนีอันตราย", "comedy"))))
    val topics get() = groups.flatMap { it.topics }
    fun category(topic: Topic) = groups.first { topic in it.topics }.title + " / " + topic.title
    fun stamp(value: JSONObject): JSONObject {
        main(value)?.let { selected ->
            value.put("category_version",2).put("category_id",selected.id).put("main_category",selected.name).put("category_style",selected.style).put("story_tone",selected.tone)
            listOf("subcategory","subcategory_id").forEach { value.remove(it) }
            return value
        }
        val topic=topics.firstOrNull { category(it)==value.optString("category") } ?: return value
        return value.put("main_category",groups.first { topic in it.topics }.title).put("subcategory",topic.title).put("subcategory_id",topic.id).put("story_tone",topic.tone)
    }
    /** A short is a complete story. Count remains governed by ClipPlan, never by editorial trimming. */
    fun shortDirection(selectedCategory: String, sceneCount: Int): String {
        val selected = main(selectedCategory)
        if(selected?.id in setOf("pia_benefits","pia_safety","pia_products")) return "FACTUAL EXPLAINER: Use only user-supplied verified facts; never invent claims, figures, prices, eligibility, deadlines, official endorsements, product experience or events. Ask for missing information. Present a clear concise explanation and preserve the exact meaning. Each scene is 8 seconds; avoid forcing fictional drama."
        val tone = when {
            selected?.tone == "comedy" || topics.any { it.tone == "comedy" && category(it) == selectedCategory } ->
                "COMEDY: establish a relatable situation, escalate one misunderstanding or prop gag, then deliver one clear visual or verbal payoff and reaction. No canned laughter."
            selected?.id == "heartwarming" -> "HEARTWARMING: show a specific need and a caring action, ending with a clear emotional response."
            selected?.id == "everyday_lesson" -> "EVERYDAY LESSON: show one choice and its consequence; let the action convey the takeaway without a lecture."
            selected?.id == "mystery_twist" -> "MYSTERY: set up one question, show a relevant clue, then reveal an understandable answer within this clip."
            selected?.id in setOf("age_status_romance", "younger_man_older_woman", "older_man_younger_woman", "romantic_misunderstanding") -> "ROMANCE: show a concrete interaction and a small believable change in mutual understanding, with a clear emotional ending."
            else -> "DRAMA: show one understandable conflict, a visible response and an earned resolution within this clip."
        }
        val structure = if (sceneCount == 4) "FOUR-SCENE STRUCTURE: Scene 1 introduce the situation immediately; scene 2 develop one problem; scene 3 turn or escalation; scene 4 resolve the situation or deliver the payoff and reaction. Do not leave the plot unfinished." else "Distribute setup, development, turn and resolution across the selected scenes; finish the story in the final scene."
        return "SHORT CLIP: One complete standalone story, not a serial or teaser. Use one clear situation, preferably one or two visible characters and one location where appropriate. A Thai title and cover must honestly reflect the actual situation and payoff; no invented events or promises. Make the story understandable without previous episodes. Use natural concise spoken Thai with phrase spacing; let actions and reactions carry meaning, with no extra narration needed. Each scene is exactly eight seconds, one continuous shot, without cuts, trimming, speeding up or montage. Cover time is separate. $structure $tone"
    }
    const val longDirection = "LONG-FORM STORY: Develop clear character goals, escalating conflicts, cause and effect and continuity across the selected episodes. Give each episode a meaningful progression and a concrete payoff. Before the final episode, end with a specific unresolved question or consequential development earned by that episode; begin the next episode by following its consequences, preserving who knows what, location, character goals, relationships, props and timeline. Escalate through plausible cause and effect, not arbitrary cliffhangers, contradictory revelations or repeated recaps. The final episode resolves the central story. Do not add a cliffhanger to factual content or one-episode stories. Each scene is exactly 8 seconds in one continuous shot; never cut, trim, speed up or use filler. Cover time is separate."
    const val storyConsistency = "STORY CONSISTENCY REVIEW: Before returning the story, check that the title, synopsis, cover incident, selected genre, chosen situation and scenes describe the same actual story. Use only registered characters and preserve their roles, names and MASTER references. Each scene's visible cast and assigned speakers must match the action and ordered dialogue. Revise contradictions before returning; do not claim visual results were generated or verified."
    const val safety = "All characters are fictional; preserve each assigned age and the reference appearance. Age 0 means an infant if explicitly assigned; supernatural age does not imply a human appearance. Children must have age-appropriate nonsexual roles and clothing. Romance involves consenting adults only; non-explicit relationship drama. No sexual or romantic stepfamily relationships, sexual coercion, sexual debt repayment, or intimate imagery. Family plots concern inheritance and secrets. Female styling follows the MASTER identity and existing SFW wardrobe rules."
    const val comedy = "COMEDY PERFORMANCE DIRECTION: Adult romantic sitcom and situational comedy. Prioritize a readable setup, misunderstanding, one safe physical or prop gag, reaction pause and payoff. Balance confidence with embarrassment and let each character retain dignity and agency. Use distinct expressive eyes, a brief double-take and playful Thai delivery when supported by the action. Do not force dark betrayal, threats, tears or a hidden villain into a comic premise. One principal action per 8-second shot; distribute setup and payoff across separate scenes when needed. No extra voices, audible laughs, canned laugh track, gasps, overlapping dialogue or ad-libs. Preserve the exact assigned speech and silent listeners. Freeze one comic instant in stills, no montage. Maintain MASTER anatomy and exact daily wardrobe. No revealing clothing accidents, transparent or wet-clothing gags, breast-focused framing or anatomy exaggeration. Any contact is consensual; no sexual activity with an intoxicated person. Props, animals and movement remain safe; no fire stunts. Use playful adult ensemble cover expressions and a relevant comedy prop, not an ominous affair-poster pose."
    fun performance(value: JSONObject): String = if(value.optString("story_tone")=="comedy" || main(value)?.tone=="comedy" || topics.any { it.tone=="comedy" && category(it)==value.optString("category") }) comedy else ""
    fun direction(selectedCategory: String): String {
        main(selectedCategory)?.let { selected ->
            if(selected.id in setOf("pia_benefits","pia_safety","pia_products")) return "FACTUAL CONTENT PRIORITY: ${selected.direction} The user supplies facts; these are not independently verified by the app. Do not invent news, dates, official statements, product results or personal experience. For required outline fields use a neutral summary of the supplied information, not a fictional event."
            return safety+"\n$creative\n$filmCraft\nSelected category: ${selected.label}\n${selected.direction}"+(if(selected.tone=="comedy") "\n$comedy" else "\nMaintain drama/suspense and use realistic emotional beats; no automatic comedy sound effects.")
        }
        return topics.firstOrNull { category(it)==selectedCategory }?.let {
            safety+"\nSelected premise: "+it.premise+if(it.tone=="comedy") "\n$comedy" else ""
        }.orEmpty()
    }
}

internal object ClipPlan {
    val shortSeconds = listOf(30,60,120,180)
    val longSeconds = listOf(120,180,300,420,600)
    fun scenes(seconds: Int): Int { require(seconds in 30..600); return (seconds+7)/8 }
    fun count(value: JSONObject): Int = if(value.optInt("clip_plan_version")==1) {
        val seconds=value.getInt("target_seconds")
        require(value.getString("clip_format") in listOf("short","long"))
        require(seconds in if(value.getString("clip_format")=="short") shortSeconds else longSeconds)
        val expected=scenes(seconds)
        require(value.optInt("scenes_per_episode",expected)==expected)
        expected
    } else 24
    fun time(seconds: Int) = "%d:%02d".format(seconds/60,seconds%60)
    fun label(seconds: Int) = "${scenes(seconds)} ซีน × 8 วินาที = ${time(scenes(seconds)*8)} เนื้อหา • ไม่รวมปกเปิด"
    fun apply(value: JSONObject, format: String, seconds: Int): JSONObject = value
        .put("clip_plan_version",1).put("clip_format",format).put("target_seconds",seconds).put("scenes_per_episode",scenes(seconds))
    fun copy(from: JSONObject, to: JSONObject) {
        if(from.optInt("clip_plan_version")==1) { count(from); listOf("clip_plan_version","clip_format","target_seconds","scenes_per_episode").forEach { to.put(it,from.get(it)) } }
    }
}
