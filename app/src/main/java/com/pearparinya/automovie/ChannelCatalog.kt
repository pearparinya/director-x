package com.pearparinya.automovie

import org.json.JSONObject

internal object ChannelCatalog {
    const val channel = "ร้อยฉาก - ซีรีส์สั้นหลากชีวิต"
    private val legacyChannels = setOf("สตอรี่ทรงโต - ซีรีส์ลับฉบับผู้ใหญ่", "สตอรี่หลังบ้าน - ซีรีส์สั้นดราม่าผัวเมีย")
    fun currentChannel(value: String): String = if (value.trim() in legacyChannels) channel else value
    fun updateLegacyChannel(value: JSONObject): JSONObject {
        if (value.has("channel")) value.put("channel", currentChannel(value.optString("channel")))
        return value
    }
    data class MainCategory(val id: String,val name: String,val style: String,val tone: String,val icon: String,val direction: String) {
        val label get() = "$name ($style)"
        val menuLabel get() = "$icon $name\n($style)"
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
        MainCategory("mystery_twist","สืบสวน ความลับ และหักมุม","สืบความจริง • ความลับ • เฉลย","drama","🔎","Mystery drama with clues, investigation and a coherent reveal. Keep all cast identities and roles consistent. Avoid graphic violence and criminal instructions."))
    private fun main(category: String) = mainCategories.firstOrNull { category==it.label || category==it.name }
    private fun main(value: JSONObject) = main(value.optString("category")) ?: mainCategories.firstOrNull { it.id==value.optString("category_id") }
    val acceptedCategories get() = mainCategories.flatMap { listOf(it.label,it.name) } + topics.map { category(it) } + groups.map { it.title }
    fun genre(value: JSONObject): String = main(value)?.let { "SELECTED GENRE: ${it.id}. ${it.direction} Preserve the written scene and assigned speech; genre direction does not add events, characters or sounds." }.orEmpty()
    private const val creative = "MAIN CATEGORY ONLY: Invent a fresh Thai title, central incident, adult occupations/relationships, obstacles, escalation and ending inside the selected category. There is no mandatory subcategory or canned incident to reproduce. Maintain the selected setting and primary tone through outline, every scene, acting, covers and sound. A twist must deepen this genre rather than switch it. Use only registered reference actors and decide roles freely; names/reference identities are not automatic husband-wife-affair roles. After outline approval keep the agreed roles, motives and continuity stable across EPs."
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
    const val safety = "All characters are fictional adults aged 25 or older. Non-explicit adult relationship drama; consensual romance only. No sexual or romantic stepfamily relationships, sexual coercion, sexual debt repayment, or intimate imagery. Family plots concern inheritance and secrets. Female styling follows the MASTER identity and existing SFW wardrobe rules."
    const val comedy = "COMEDY PERFORMANCE DIRECTION: Adult romantic sitcom and situational comedy. Prioritize a readable setup, misunderstanding, one safe physical or prop gag, reaction pause and payoff. Balance confidence with embarrassment and let each character retain dignity and agency. Use distinct expressive eyes, a brief double-take and playful Thai delivery when supported by the action. Do not force dark betrayal, threats, tears or a hidden villain into a comic premise. One principal action per 8-second shot; distribute setup and payoff across separate scenes when needed. No extra voices, audible laughs, canned laugh track, gasps, overlapping dialogue or ad-libs. Preserve the exact assigned speech and silent listeners. Freeze one comic instant in stills, no montage. Maintain MASTER anatomy and exact daily wardrobe. No revealing clothing accidents, transparent or wet-clothing gags, breast-focused framing or anatomy exaggeration. Any contact is consensual; no sexual activity with an intoxicated person. Props, animals and movement remain safe; no fire stunts. Use playful adult ensemble cover expressions and a relevant comedy prop, not an ominous affair-poster pose."
    fun performance(value: JSONObject): String = if(value.optString("story_tone")=="comedy" || main(value)?.tone=="comedy" || topics.any { it.tone=="comedy" && category(it)==value.optString("category") }) comedy else ""
    fun direction(selectedCategory: String): String {
        main(selectedCategory)?.let { selected ->
            return safety+"\n$creative\nSelected category: ${selected.label}\n${selected.direction}"+(if(selected.tone=="comedy") "\n$comedy" else "\nMaintain drama/suspense and use realistic emotional beats; no automatic comedy sound effects.")
        }
        return topics.firstOrNull { category(it)==selectedCategory }?.let {
            safety+"\nSelected premise: "+it.premise+if(it.tone=="comedy") "\n$comedy" else ""
        }.orEmpty()
    }
}

internal object ClipPlan {
    val shortSeconds = listOf(30,60,120,180)
    val longSeconds = listOf(180,300,420,600)
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
    fun label(seconds: Int) = "ประมาณ ${if(seconds<60) "$seconds วินาที" else "${seconds/60} นาที"} • ${scenes(seconds)} ซีน = ${time(scenes(seconds)*8)}"
    fun apply(value: JSONObject, format: String, seconds: Int): JSONObject = value
        .put("clip_plan_version",1).put("clip_format",format).put("target_seconds",seconds).put("scenes_per_episode",scenes(seconds))
    fun copy(from: JSONObject, to: JSONObject) {
        if(from.optInt("clip_plan_version")==1) { count(from); listOf("clip_plan_version","clip_format","target_seconds","scenes_per_episode").forEach { to.put(it,from.get(it)) } }
    }
}
