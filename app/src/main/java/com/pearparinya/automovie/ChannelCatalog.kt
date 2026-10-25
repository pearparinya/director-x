package com.pearparinya.automovie

import org.json.JSONObject

internal object ChannelCatalog {
    const val channel = "สตอรี่ทรงโต - ซีรีส์ลับฉบับผู้ใหญ่"
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
        val topic=topics.firstOrNull { category(it)==value.optString("category") } ?: return value
        return value.put("main_category",groups.first { topic in it.topics }.title).put("subcategory",topic.title).put("subcategory_id",topic.id).put("story_tone",topic.tone)
    }
    const val safety = "All characters are fictional adults aged 25 or older. Non-explicit adult relationship drama; consensual romance only. No sexual or romantic stepfamily relationships, sexual coercion, sexual debt repayment, or intimate imagery. Family plots concern inheritance and secrets. Female styling follows the MASTER identity and existing SFW wardrobe rules."
    const val comedy = "COMEDY PERFORMANCE DIRECTION: Adult romantic sitcom and situational comedy. Prioritize a readable setup, misunderstanding, one safe physical or prop gag, reaction pause and payoff. Balance confidence with embarrassment and let each character retain dignity and agency. Use distinct expressive eyes, a brief double-take and playful Thai delivery when supported by the action. Do not force dark betrayal, threats, tears or a hidden villain into a comic premise. One principal action per 8-second shot; distribute setup and payoff across separate scenes when needed. No extra voices, audible laughs, canned laugh track, gasps, overlapping dialogue or ad-libs. Preserve the exact assigned speech and silent listeners. Freeze one comic instant in stills, no montage. Maintain MASTER anatomy and exact daily wardrobe. No revealing clothing accidents, transparent or wet-clothing gags, breast-focused framing or anatomy exaggeration. Any contact is consensual; no sexual activity with an intoxicated person. Props, animals and movement remain safe; no fire stunts. Use playful adult ensemble cover expressions and a relevant comedy prop, not an ominous affair-poster pose."
    fun performance(value: JSONObject): String = if(value.optString("story_tone")=="comedy" || topics.any { it.tone=="comedy" && category(it)==value.optString("category") }) comedy else ""
    fun direction(selectedCategory: String): String = topics.firstOrNull { category(it)==selectedCategory }?.let {
        safety+"\nSelected premise: "+it.premise+if(it.tone=="comedy") "\n$comedy" else ""
    }.orEmpty()
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
