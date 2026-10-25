package com.pearparinya.automovie

import org.json.JSONObject

internal object ChannelCatalog {
    const val channel = "สตอรี่ทรงโต - ซีรีส์ลับฉบับผู้ใหญ่"
    data class Topic(val id: Int, val title: String, val premise: String)
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
            Topic(24,"🎬 Backstage Drama","นางเอกกับนักแสดงดาวรุ่งหญิงอายุ 25 ปีขึ้นไป แข่งขันแย่งงานและความรัก"))))
    val topics get() = groups.flatMap { it.topics }
    fun category(topic: Topic) = groups.first { topic in it.topics }.title + " / " + topic.title
    fun stamp(value: JSONObject): JSONObject {
        val topic=topics.firstOrNull { category(it)==value.optString("category") } ?: return value
        return value.put("main_category",groups.first { topic in it.topics }.title).put("subcategory",topic.title).put("subcategory_id",topic.id)
    }
    const val safety = "All characters are fictional adults aged 25 or older. Non-explicit adult relationship drama; consensual romance only. No sexual or romantic stepfamily relationships, sexual coercion, sexual debt repayment, or intimate imagery. Family plots concern inheritance and secrets. Female styling follows the MASTER identity and existing SFW wardrobe rules."
    fun direction(selectedCategory: String): String = topics.firstOrNull { category(it)==selectedCategory }?.let { safety+"\nSelected premise: "+it.premise }.orEmpty()
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
