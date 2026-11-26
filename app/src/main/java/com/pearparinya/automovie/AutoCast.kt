package com.pearparinya.automovie

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Freeze AI casting before scene generation; retries reuse the same names and original bytes. */
internal object AutoCast {
    fun adultRomanceEligible(row: JSONObject): Boolean = row.optString("character_type") in listOf("", "human", "knight") &&
        row.optString("age_basis") != "unspecified" && row.optInt("character_age") >= 18
    /** Send only casting information; keep original files, metadata and hashes for local verification. */
    fun promptCatalog(catalog: JSONArray): JSONArray = JSONArray().apply {
        val fields=listOf("id","display_name","character_age","character_gender","character_type","age_basis","casting_description")
        for(i in 0 until catalog.length()) {
            val source=catalog.getJSONObject(i); val compact=JSONObject()
            fields.forEach { field -> if(source.has(field) && !(field=="character_age" && source.optString("age_basis")=="unspecified")) compact.put(field,source.get(field)) }
            put(compact)
        }
    }
    /**
     * Casting brief guides the text model without inferring personality or
     * identity from a reference photo. MASTER IDs remain the sole identity key.
     */
    fun castingBrief(category: String, presenterId: String, requiredIds: Set<String>): String {
        val anchor = if(presenterId.isNotBlank())
            "The explicitly selected presenter is the central protagonist; do not replace or duplicate this identity. "
        else ""
        val pinned = if(requiredIds.isNotEmpty())
            "Keep every required library ID exactly once: ${requiredIds.sorted().joinToString(", ")}. "
        else ""
        val genre = when {
            category.startsWith("รักต่างวัย | หนุ่มรุ่นน้องกับสาวรุ่นพี่") ->
                "Cast exactly one eligible younger adult man and one older adult woman as the romantic leads, with a catalog age gap of at least 15 years. "
            category.startsWith("รักต่างวัย | หนุ่มรุ่นใหญ่กับสาวรุ่นน้อง") ->
                "Cast exactly one eligible older adult man and one younger adult woman as the romantic leads, with a catalog age gap of at least 15 years. "
            else -> ""
        }
        return anchor+pinned+genre+
            storyCastingBrief()+"FIRST identify the essential story roles and relationships from the premise; THEN match each role to a distinct catalog ID. "+
            "Choose the smallest credible cast (1 to 8); never add filler actors or duplicate roles just to reach a fixed count. "+
            "Use catalog character ages and types only as explicitly assigned fictional metadata, not inferred from faces. "+
            "Do not infer personality, trustworthiness or talent from appearance. "+
            "Preserve the exact catalog MASTER ID for each actor, and keep names, roles and identity stable across all scenes. "+
            "Favor an eligible less-recently-used actor when role fit is comparable, but never sacrifice genre requirements or pinned IDs for variety. "
    }
    /** Explicit story evidence only; never infer a species from the genre alone. */
    private val subjectRules = listOf(
        Regex("ผีสาว|ผีหญิง|วิญญาณหญิง|กระสือ|กระหัง|ตานี") to setOf("ghost","spirit"),
        Regex("ผี|วิญญาณ|ภูต") to setOf("ghost","spirit"),
        Regex("อสูร|สัตว์ประหลาด") to setOf("creature","demon"),
        Regex("พญานาค|มังกร|สุนัข|แมว|ช้าง|เสือ|หมาป่า") to setOf("animal","creature"),
        Regex("ครุฑ") to setOf("creature","deity","animal")
    )
    private val speciesTerms=listOf("พญานาค","มังกร","สุนัข","แมว","ช้าง","เสือ","หมาป่า","ครุฑ","กระสือ","กระหัง","ตานี")
    private val appearanceTerms=listOf("ผมยาว","ผมขาว","ชุดน้ำเงิน","ชุดสีอ่อน","เครื่องทรง","หญิง","ชาย","ชรา")
    private val roleTerms=listOf("ผู้พิทักษ์","นักล่า","นักสำรวจ","จอมเวท","อัศวิน","ชาวบ้าน")
    private fun evidence(row: JSONObject)=row.optString("display_name")+" "+row.optString("casting_description")
    private fun normalized(text: String)=text.replace("ผีสาว","วิญญาณหญิง").replace("ผีหญิง","วิญญาณหญิง")
    // Mask only the referenced noun, so an actor sharing its clause remains visible.
    private val indirectSubject by lazy { Regex(
        "(?:กล่าวถึง|พูดถึง|เล่าถึง|เอ่ยถึง|ไม่พบ|ไม่เห็น|ไม่มี)\\s*" +
            "(?:ผี|วิญญาณ)?(?:วิญญาณหญิง|วิญญาณ|ผี|ภูต|อสูร|สัตว์ประหลาด|" +
            (speciesTerms + humanRoles.map { it.label }).sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")"
    ) }
    private fun visibleEvidence(text: String) = indirectSubject.replace(normalized(text), " ")
    // A labelled setting in the submitted story can be compared to a registered description.
    private val settingField = Regex("""(?:^|[\n;,•]|\s)(?:สถานที่|ยุคสมัย|ยุค|location|era)\s*[:：]\s*([^\n;,•]+)""", RegexOption.IGNORE_CASE)

    data class Criterion(val label: String, val maximum: Int, val evidence: String?) {
        val points get() = if (evidence.isNullOrBlank()) 0 else maximum
    }

    fun matchingEvidence(row: JSONObject, premise: String, category: String = "", setting: String = "",
                         previous: Set<String> = emptySet()): List<Criterion> {
        val query = visibleEvidence(premise)
        val metadata = normalized(evidence(row))
        val type = row.optString("character_type")
        val id = row.optString("id")
        val typeMatch = subjectRules.firstNotNullOfOrNull { (pattern, types) ->
            if (type in types) pattern.find(query)?.value else null
        }
        val family = explicitHumanRoles(query).firstOrNull { humanEligible(row, it) }
        fun shared(terms: List<String>) = terms.firstOrNull { it in query && it in metadata }
        fun registered(value: String) = value.takeIf { it.isNotBlank() && it in metadata }
        return listOf(
            Criterion("ประเภท", 30, typeMatch ?: family?.label),
            Criterion("บทบาทหรือสายพันธุ์", 25, shared(speciesTerms + roleTerms)),
            Criterion("ลักษณะภายนอก", 20, shared(appearanceTerms)),
            Criterion("ประเภทเรื่อง", 10, registered(category)),
            Criterion("ยุคหรือสถานที่", 10, registered(setting) ?: settingField.findAll(premise)
                .map { it.groupValues[1].trim() }.firstNotNullOfOrNull(::registered)),
            Criterion("ความต่อเนื่อง", 5, id.takeIf { it.isNotBlank() && it in previous })
        )
    }
    fun matchingScore(row: JSONObject, premise: String, category: String="", setting: String="", previous: Set<String> = emptySet()): Int =
        matchingEvidence(row, premise, category, setting, previous).sumOf { it.points }

    fun storyCatalog(catalog: JSONArray, premise: String, pinned: Set<String> = emptySet(), category: String = ""): JSONArray {
        val rows=(0 until catalog.length()).map { catalog.getJSONObject(it) }
        return JSONArray(rows.sortedWith(compareByDescending<JSONObject> { it.optString("id") in pinned }
            .thenByDescending { matchingScore(it,premise,category) }))
    }
    fun roleConflict(row: JSONObject, roleText: String): String? {
        val role = roleText.trim()
        val human = humanRoles.firstOrNull { role == it.label }
        if (human != null && !humanEligible(row,human)) return "วัยหรือเพศ MASTER ไม่ตรงกับบทบาท: $role"
        val rule = subjectRules.firstOrNull { it.first.find(role)?.range?.first == 0 } ?: return null
        if (row.optString("character_type") !in rule.second) return "ประเภท MASTER ไม่ตรงกับบทบาท: $role"
        val species = speciesTerms.firstOrNull { role.startsWith(it) || role.startsWith("ผี"+it) }
        return if (species != null && species !in evidence(row)) "สายพันธุ์ MASTER ไม่ตรงกับบทบาท: $role" else null
    }

    fun validateStoryRoles(value: JSONObject, catalog: JSONArray, pinned: Set<String> = emptySet()) {
        val cast = value.getJSONArray("cast")
        CastingSelection.requireIncluded(cast,pinned)
        val rows = (0 until catalog.length()).map { catalog.getJSONObject(it) }.associateBy { it.getString("id") }
        for (i in 0 until cast.length()) {
            val actor = cast.getJSONObject(i)
            val id = actor.getString("library_id")
            val row = requireNotNull(rows[id]) { "ไม่พบ MASTER ในคลัง: $id" }
            if (id !in pinned) roleConflict(row,actor.optString("role"))?.let { throw IllegalArgumentException(it) }
        }
    }
    // Anchor to a narrated physical appearance, never to a title, genre or a question in dialogue.
    private val appearanceSubject = Regex("""^(ผีสาว|ผีหญิง|วิญญาณหญิง|ผีกระสือ|ผีกระหัง|ผีตานี|พญานาค|ครุฑ|มังกร|อสูร|สุนัข|แมว)\s*(?:ปรากฏตัว|เดินเข้าฉาก|เดินเข้ามา|โผล่ออกมา)""")
    fun requireNarratedAppearances(value: JSONObject, catalog: JSONArray, premise: String) {
        val selected = value.getJSONArray("cast")
        val ids = (0 until selected.length()).map { selected.getJSONObject(it).getString("library_id") }.toSet()
        val actors = (0 until catalog.length()).map { catalog.getJSONObject(it) }.filter { it.optString("id") in ids }
        for (clause in premise.split(Regex("""[.!。;\n]+"""))) {
            val subject = appearanceSubject.find(clause.trim())?.groupValues?.get(1) ?: continue
            val rule = subjectRules.first { it.first.containsMatchIn(subject) }
            require(actors.any { it.optString("character_type") in rule.second && normalized(subject) in normalized(evidence(it)) }) {
                "ขาด MASTER สำหรับตัวละครที่ปรากฏในเหตุการณ์: $subject"
            }
        }
    }
    private data class HumanRole(val label: String, val gender: String, val ages: IntRange)
    private val humanRoles=listOf(
        HumanRole("พ่อ","man",18..100), HumanRole("แม่","woman",18..100),
        HumanRole("สามี","man",18..100), HumanRole("ภรรยา","woman",18..100),
        HumanRole("เด็กชาย","man",1..17), HumanRole("เด็กหญิง","woman",1..17),
        HumanRole("ชายชรา","man",60..120), HumanRole("หญิงชรา","woman",60..120))
    private fun humanEligible(row: JSONObject, role: HumanRole): Boolean =
        (row.optString("character_type") in setOf("","human") || (row.optString("character_type")=="child" && role.ages.last<=17)) &&
        row.optString("age_basis")!="unspecified" &&
        row.optString("character_gender")==role.gender && row.optInt("character_age") in role.ages
    /** Conservative explicit family roles; ambiguous mentions remain for user review. */
    private fun explicitHumanRoles(premise: String): List<HumanRole> {
        val text = premise.replace(Regex("แม่มด|พ่อค้า"), " ")
        return humanRoles.filter { role ->
            Regex("(?<!ผี)(?<!วิญญาณ)" + Regex.escape(role.label)).containsMatchIn(text)
        }
    }

    // Combine roles only when the text explicitly identifies the same individual.
    private fun familyParts(query: String): List<List<HumanRole>> {
        val roles = explicitHumanRoles(query)
        val groups = roles.map { mutableListOf(it) }.toMutableList()
        for (first in roles) for (second in roles) {
            if (first == second || first.gender != second.gender) continue
            val alias = Regex(Regex.escape(first.label) +
                """\s*(?:ซึ่งเป็น|ผู้เป็น|ที่เป็น|และยังเป็น|คนเดียวกันเป็น)\s*""" + Regex.escape(second.label))
            if (!alias.containsMatchIn(query)) continue
            val from = groups.first { first in it }
            val to = groups.first { second in it }
            if (from !== to) {
                groups.remove(to)
                from.addAll(to)
            }
        }
        return groups
    }
    /** Offline proposal from explicit story subjects, without network access or invented IDs. */
    fun kotlinProposal(catalog: JSONArray, premise: String, pinned: Set<String> = emptySet(), category: String = ""): JSONObject {
        require(premise.isNotBlank()) { "กรุณาระบุเนื้อเรื่องก่อนเลือก MASTER" }
        val rows=(0 until catalog.length()).map { catalog.getJSONObject(it) }
        val cast=JSONArray()
        val used=linkedSetOf<String>()
        val names=linkedSetOf<String>()
        fun add(row: JSONObject, role: String) {
            val id=row.getString("id")
            if(!used.add(id)) return
            val label=row.optString("display_name").ifBlank { id }
            val name=if(names.add(label)) label else id.also { require(names.add(it)) { "ชื่อตัวละครซ้ำ: "+it } }
            cast.put(JSONObject().put("library_id",id).put("name",name).put("role",role))
        }
        pinned.forEach { id -> add(requireNotNull(rows.firstOrNull { it.optString("id")==id }) { "ไม่พบ MASTER ที่ล็อกไว้: "+id },"ตัวละครที่เลือกไว้") }
        val query=visibleEvidence(premise)
        val subjects=speciesTerms.filter { query.contains(it) }.toMutableList()
        if(subjects.none { it in listOf("กระสือ","กระหัง","ตานี") }) {
            if(query.contains("วิญญาณหญิง")) subjects.add("วิญญาณหญิง")
            else if(Regex("ผี|วิญญาณ|ภูต").containsMatchIn(query)) subjects.add("วิญญาณ")
        }
        if(Regex("อสูร|สัตว์ประหลาด").containsMatchIn(query)) subjects.add("อสูร")
        for(subject in subjects) {
            val rule=subjectRules.firstOrNull { it.first.containsMatchIn(subject) }
            val eligible=rows.filter { row ->
                (rule==null || row.optString("character_type") in rule.second) &&
                (if(subject=="วิญญาณ") row.optString("character_type") in setOf("ghost","spirit") else normalized(evidence(row)).contains(subject))
            }
            val pinnedMatch=(0 until cast.length()).map { cast.getJSONObject(it) }.firstOrNull { actor ->
                actor.optString("role")=="ตัวละครที่เลือกไว้" && eligible.any { it.optString("id")==actor.optString("library_id") }
            }
            if(pinnedMatch!=null) { pinnedMatch.put("role",subject); continue }
            val best=eligible.filter { it.optString("id") !in used }.sortedWith(compareByDescending<JSONObject> { matchingScore(it,query,category) }.thenBy { it.getString("id") }).firstOrNull()
            requireNotNull(best) { "ไม่พบ MASTER ตรงกับบท: "+subject }
            add(best,subject)
        }
        for(parts in familyParts(query)) {
            val label = parts.joinToString(" / ") { it.label }
            fun eligible(row: JSONObject) = parts.all { humanEligible(row, it) }
            val existing=(0 until cast.length()).map { cast.getJSONObject(it) }.firstOrNull { actor ->
                actor.optString("role")=="ตัวละครที่เลือกไว้" && rows.any { it.optString("id")==actor.optString("library_id") && eligible(it) }
            }
            if(existing!=null) { existing.put("role",label); continue }
            val best=rows.filter { it.optString("id") !in used && eligible(it) }
                .sortedWith(compareByDescending<JSONObject> { matchingScore(it,premise,category) }.thenBy { it.getString("id") }).firstOrNull()
            requireNotNull(best) { "ไม่พบ MASTER ที่มีวัยและเพศตรงกับบท: "+label }
            add(best,label)
        }
        require(cast.length() in 1..8) { "ยังระบุบทบาทจากเรื่องไม่ได้ หรือจำนวนตัวละครเกิน 8 กรุณาเลือก MASTER เอง" }
        return JSONObject().put("story_concept",premise).put("cast",cast).also { validate(it,catalog); validateStoryRoles(it,catalog,pinned) }
    }
    fun storyCastingBrief(): String =
        "Identify essential explicitly described nonhuman and human roles before selecting actors. " +
        "For a ghost, spirit, animal, deity or creature role use the matching catalog type and species evidence; never substitute a human merely because the protagonist is human. " +
        "A genre label alone is not evidence of a creature. Preserve explicitly selected IDs; if no suitable MASTER exists report the missing role instead of inventing an ID. "
    fun schema() = JSONObject("""{"type":"object","properties":{"story_concept":{"type":"string"},"cast":{"type":"array","items":{"type":"object","properties":{"library_id":{"type":"string"},"name":{"type":"string"},"english_name":{"type":"string"},"role":{"type":"string"},"romance_role":{"type":"string"}},"required":["library_id","name","role"]}}},"required":["story_concept","cast"]}""")
    class ProposalError(message: String) : IllegalArgumentException(message)

    fun propose(prompt: String, catalog: JSONArray, presenterId: String, stopped: () -> Boolean,
                pinned: Set<String> = emptySet(), premise: String = "", generate: (String) -> JSONObject): JSONObject {
        var request = prompt
        repeat(2) { attempt ->
            if (stopped()) throw java.util.concurrent.CancellationException()
            val proposal = generate(request)
            val problem = try {
                validate(proposal, catalog)
                requirePresenter(proposal, presenterId)
                validateStoryRoles(proposal, catalog, pinned)
                requireNarratedAppearances(proposal,catalog,premise)
                return proposal
            } catch (e: IllegalArgumentException) {
                e.message ?: "รายชื่อตัวละครผิดเงื่อนไข"
            } catch (e: org.json.JSONException) {
                "รูปแบบข้อมูลตัวละครไม่ครบ • ต้องมี cast, library_id, name และ role"
            }
            if (attempt == 1) throw ProposalError(problem)
            request = "$prompt\nCorrect the previous proposal. Validation error: $problem. " +
                "Keep valid cast members and return the complete corrected JSON. Previous proposal: " +
                proposal.toString().take(12000)
        }
        error("Missing cast proposal")
    }
    fun validate(value: JSONObject, catalog: JSONArray) {
        require(value.optString("story_concept").isNotBlank()) { "ขาดแนวคิดเรื่อง" }
        val cast=requireNotNull(value.optJSONArray("cast")) { "ขาดรายการ cast หรือรูปแบบรายการไม่ถูกต้อง" }; require(cast.length() in 1..8) { "เลือกนักแสดง 1–8 คนตามบท" }
        val ids=(0 until catalog.length()).map { catalog.getJSONObject(it).getString("id") }.toSet()
        val names=mutableSetOf<String>(); val englishNames=mutableSetOf<String>(); val used=mutableSetOf<String>()
        for(i in 0 until cast.length()) {
            val p=requireNotNull(cast.optJSONObject(i)) { "ข้อมูลตัวละครลำดับที่ ${i+1} ต้องเป็นรายการข้อมูล" }
            val id=p.optString("library_id")
            require(id in ids) { "รหัส MASTER ของตัวละครลำดับที่ ${i+1} ไม่อยู่ในคลัง" }
            require(used.add(id)) { "รหัส MASTER ซ้ำที่ตัวละครลำดับที่ ${i+1}" }
            val name=p.optString("name").trim()
            val english=p.optString("english_name").trim()
            if(english.isNotEmpty()) require(Regex("[A-Za-z][A-Za-z-]{1,40}").matches(english) && englishNames.add(english.lowercase())) { "ชื่ออังกฤษต้องไม่ซ้ำและเป็นอักษรอังกฤษ" }
            require(name.isNotBlank() && names.add(name)) { "ชื่อของตัวละครลำดับที่ ${i+1} ว่างหรือซ้ำ" }
            require(p.optString("role").isNotBlank()) { "ขาดบทบาทของตัวละครลำดับที่ ${i+1}" }
        }
    }
    fun requirePresenter(value: JSONObject, presenterId: String) {
        if(presenterId.isBlank()) return
        val cast=value.getJSONArray("cast")
        require((0 until cast.length()).any { cast.getJSONObject(it).optString("library_id")==presenterId }) { "ต้องมี MASTER ผู้ดำเนินเรื่องที่เลือก" }
    }
    fun validateGenre(value: JSONObject, catalog: JSONArray, category: String) {
        val olderMan=category.startsWith("รักต่างวัย | หนุ่มรุ่นใหญ่กับสาวรุ่นน้อง")
        if(!olderMan && !category.startsWith("รักต่างวัย | หนุ่มรุ่นน้องกับสาวรุ่นพี่")) return
        val cast=value.getJSONArray("cast")
        val rows=(0 until catalog.length()).map { catalog.getJSONObject(it) }.associateBy { it.getString("id") }
        fun lead(kind: String, gender: String, ages: IntRange): JSONObject {
            val matches=(0 until cast.length()).map { cast.getJSONObject(it) }.filter { it.optString("romance_role")==kind }
            require(matches.size==1) { "ระบุ romance_role=$kind ให้คู่หลักคนเดียว" }
            val row=rows[matches.single().getString("library_id")]!!
            require(adultRomanceEligible(row) && row.optString("character_gender")==gender && row.optInt("character_age") in ages) { "คู่หลัก $kind ต้องเป็น $gender อายุ $ages ตาม MASTER" }
            return row
        }
        val man=if(olderMan) lead("older_man","man",45..60) else lead("younger_man","man",25..30)
        val woman=if(olderMan) lead("younger_woman","woman",25..35) else lead("older_woman","woman",45..55)
        require(if(olderMan) man.getInt("character_age")-woman.getInt("character_age")>=15 else woman.getInt("character_age")-man.getInt("character_age")>=15) { "คู่หลักต้องอายุห่างอย่างน้อย 15 ปี" }
    }
    /** Build the final offline cast from the reviewed selection, including manual edits. */
    fun confirmedKotlinCast(catalog: JSONArray, selected: Set<String>, roles: JSONObject,
                            premise: String, category: String): JSONObject {
        require(selected.isNotEmpty()) { "กรุณายืนยันตัวละครก่อนสร้างบท" }
        val pool = CastingSelection.filter(catalog, selected)
        val rows = (0 until pool.length()).map { pool.getJSONObject(it) }
        val cast = JSONArray()
        val actors = rows.associate { row ->
            val id = row.getString("id")
            val actor = JSONObject().put("library_id", id)
                .put("name", row.optString("display_name").ifBlank { id })
                .put("role", roles.optString(id).ifBlank { "ตัวละครที่ผู้ใช้เลือก" })
            cast.put(actor)
            id to actor
        }
        val olderMan = category.startsWith("รักต่างวัย | หนุ่มรุ่นใหญ่กับสาวรุ่นน้อง")
        if (olderMan || category.startsWith("รักต่างวัย | หนุ่มรุ่นน้องกับสาวรุ่นพี่")) {
            val men = rows.filter { adultRomanceEligible(it) && it.optString("character_gender") == "man" &&
                it.optInt("character_age") in (if (olderMan) 45..60 else 25..30) }
            val women = rows.filter { adultRomanceEligible(it) && it.optString("character_gender") == "woman" &&
                it.optInt("character_age") in (if (olderMan) 25..35 else 45..55) }
            val pairs = men.flatMap { m -> women.filter { w ->
                kotlin.math.abs(m.getInt("character_age") - w.getInt("character_age")) >= 15
            }.map { w -> m to w } }
            val (man, woman) = requireNotNull(pairs.singleOrNull()) { "กรุณาเลือก MASTER คู่หลักที่ตรงเกณฑ์อายุให้ชัดเจนหนึ่งคู่" }
            actors.getValue(man.getString("id")).put("romance_role", if (olderMan) "older_man" else "younger_man")
            actors.getValue(woman.getString("id")).put("romance_role", if (olderMan) "younger_woman" else "older_woman")
        }
        return JSONObject().put("story_concept", premise).put("cast", cast).also {
            validate(it, catalog)
            validateGenre(it, catalog, category)
        }
    }
    fun recentCasts(base: File, current: File): JSONArray {
        val casts=base.listFiles().orEmpty().filter { it.isDirectory && it.canonicalFile!=current.canonicalFile }.mapNotNull { project ->
            // The project directory timestamp may not change when an existing casting file is written.
            val files=listOf(File(project,"gemini_job/casting.json"),File(project,"story.json"))
            files.mapNotNull fileLoop@ { file ->
                if(!file.isFile) return@fileLoop null
                val value=runCatching { JSONObject(file.readText()) }.getOrNull() ?: return@fileLoop null
                val cast=value.optJSONArray("cast") ?: value.optJSONArray("supporting_characters") ?: return@fileLoop null
                val ids=(0 until cast.length()).mapNotNull { cast.optJSONObject(it)?.optString("library_id")?.takeIf(String::isNotBlank) }.distinct()
                if(ids.isEmpty()) null else file.lastModified() to ids
            }.maxByOrNull { it.first }
        }.sortedByDescending { it.first }.take(5)
        return JSONArray(casts.map { JSONArray(it.second) })
    }

    /** New jobs prefer a usable pool excluding the last cast. Old jobs keep their saved pool. */
    fun rotationPool(catalog: JSONArray, recent: JSONArray, category: String, seed: String): JSONArray {
        val rows=(0 until catalog.length()).map { catalog.getJSONObject(it) }
        val history=(0 until recent.length()).mapNotNull { recent.optJSONArray(it) }.map { a ->
            (0 until a.length()).map { a.optString(it) }.toSet()
        }
        if(history.isEmpty()) return JSONArray(rows.shuffled(kotlin.random.Random(seed.hashCode())))
        val last=history.first()
        val fresh=rows.filter { it.optString("id") !in last }
        fun preservesEligibility(candidates: List<JSONObject>): Boolean {
            if(candidates.size<2) return false
            for(gender in listOf("man","woman"))
                if(rows.any { it.optString("character_gender")==gender } && candidates.none { it.optString("character_gender")==gender }) return false
            if(category.startsWith("รักต่างวัย | หนุ่มรุ่นน้องกับสาวรุ่นพี่")) {
                val men=candidates.filter { adultRomanceEligible(it) && it.optString("character_gender")=="man" && it.optInt("character_age") in 25..30 }
                val women=candidates.filter { adultRomanceEligible(it) && it.optString("character_gender")=="woman" && it.optInt("character_age") in 45..55 }
                if(men.none { man -> women.any { woman -> woman.optInt("character_age")-man.optInt("character_age")>=15 } }) return false
            }
            if(category.startsWith("รักต่างวัย | หนุ่มรุ่นใหญ่กับสาวรุ่นน้อง")) {
                val men=candidates.filter { adultRomanceEligible(it) && it.optString("character_gender")=="man" && it.optInt("character_age") in 45..60 }
                val women=candidates.filter { adultRomanceEligible(it) && it.optString("character_gender")=="woman" && it.optInt("character_age") in 25..35 }
                if(men.none { man -> women.any { woman -> man.optInt("character_age")-woman.optInt("character_age")>=15 } }) return false
            }
            return true
        }
        val candidates=if(preservesEligibility(fresh)) fresh else rows
        // Prefer lower frequency and older use across all five known casts; vary ties per story.
        val random=kotlin.random.Random(seed.hashCode())
        val ordered=candidates.shuffled(random).sortedWith(compareBy<JSONObject> { row ->
            val id=row.optString("id"); history.count { id in it }
        }.thenByDescending { row ->
            val id=row.optString("id"); val lastUse=history.indexOfFirst { id in it }; if(lastUse<0) Int.MAX_VALUE else lastUse
        })
        return JSONArray(ordered)
    }
    /** Prefer a fresh pool, but preserve the complete catalog if there are too few choices. */
    fun candidatePool(catalog: JSONArray, recent: JSONArray, category: String): JSONArray {
        val rows=(0 until catalog.length()).map { catalog.getJSONObject(it) }
        val last=recent.optJSONArray(0) ?: return catalog
        val used=(0 until last.length()).map { last.optString(it) }.toSet()
        val fresh=rows.filter { it.optString("id") !in used }
        if(fresh.size<8) return catalog
        if(category.startsWith("รักต่างวัย | หนุ่มรุ่นน้องกับสาวรุ่นพี่")) {
            val men=fresh.filter { adultRomanceEligible(it) && it.optString("character_gender")=="man" && it.optInt("character_age") in 25..30 }
            val women=fresh.filter { adultRomanceEligible(it) && it.optString("character_gender")=="woman" && it.optInt("character_age") in 45..55 }
            if(men.none { man -> women.any { woman -> woman.optInt("character_age")-man.optInt("character_age")>=15 } }) return catalog
        }
        if(category.startsWith("รักต่างวัย | หนุ่มรุ่นใหญ่กับสาวรุ่นน้อง")) {
            val men=fresh.filter { adultRomanceEligible(it) && it.optString("character_gender")=="man" && it.optInt("character_age") in 45..60 }
            val women=fresh.filter { adultRomanceEligible(it) && it.optString("character_gender")=="woman" && it.optInt("character_age") in 25..35 }
            if(men.none { man -> women.any { woman -> man.optInt("character_age")-woman.optInt("character_age")>=15 } }) return catalog
        }
        // Preserve gender coverage when a fresh pool would eliminate an existing group.
        for(gender in listOf("man","woman")) if(rows.any { it.optString("character_gender")==gender } && fresh.none { it.optString("character_gender")==gender }) return catalog
        return JSONArray(fresh)
    }
    fun validateVariety(value: JSONObject, catalog: JSONArray, recent: JSONArray) {
        validate(value,catalog)
        // Variety is a preference; a suitable previously used pairing remains valid.
    }
    @Synchronized
    fun apply(value: JSONObject, catalog: JSONArray, project: File): JSONArray {
        validate(value,catalog)
        val result=JSONArray(); val cast=value.getJSONArray("cast")
        val rows = (0 until catalog.length()).map { catalog.getJSONObject(it) }
        val byId = rows.associateBy { it.getString("id") }
        require(rows.size == byId.size) { "รหัส MASTER ซ้ำในทะเบียน" }
        val directory = File(project,"original_masters")
        require(directory.isDirectory || directory.mkdirs()) { "เปิดโฟลเดอร์ MASTER ไม่ได้" }
        val staged = linkedMapOf<File,File>()
        val installed = mutableListOf<File>()
        val destinations = mutableSetOf<File>()
        try {
            for(i in 0 until cast.length()) {
                val p=cast.getJSONObject(i); val id=p.getString("library_id")
                val row=byId.getValue(id)
                val filename=row.getString("file")
                require(File(filename).name==filename) { "ชื่อไฟล์ MASTER ไม่ถูกต้อง" }
                val source=File(project,"master_candidates/$filename")
                val key="support_%08x".format(id.substringAfter('_').toInt())
                val target=File(directory,"$key.master")
                require(destinations.add(target)) { "รหัส MASTER อ้างถึงไฟล์เดียวกัน: $id" }
                val sha=row.getString("sha256")
                require(source.isFile) { "ขาด MASTER ต้นฉบับของ $id" }
                val bytes=source.readBytes()
                require(MasterLibrary.digest(bytes)==sha) { "ภาพต้นฉบับของ $id ไม่ตรงกับทะเบียน" }
                if(target.exists()) {
                    require(target.isFile && MasterLibrary.digest(target.readBytes())==sha) { "MASTER เดิมไม่ตรงกับทะเบียน: $id • ต้องตรวจสอบก่อนเปลี่ยน" }
                } else {
                    val pending=File.createTempFile("cast-", ".pending", directory)
                    staged[target]=pending
                    pending.writeBytes(bytes)
                    check(MasterLibrary.digest(pending.readBytes())==sha) { "สำเนาภาพ MASTER ไม่ครบ: $id" }
                }
                result.put(JSONObject().put("name",p.getString("name").trim()).put("master","${key.uppercase()}_MASTER.png")
                    .put("english_name",p.optString("english_name").trim()).put("auto_cast",true).put("library_id",id).put("role",p.getString("role"))
                    .put("romance_role",p.optString("romance_role")).put("character_age",row.optInt("character_age",0)).put("age_basis",row.optString("age_basis"))
                    .put("character_type",row.optString("character_type")).put("character_gender",row.optString("character_gender"))
                    .put("visual_description",MasterLibrary.description(id)+(if(row.has("character_age") && (row.optInt("character_age")>0 || row.optString("age_basis")=="user_assigned")) " Assigned fictional character age: ${row.getInt("character_age")} years. Keep this age consistent throughout the story." else "")+" Reference appearance: "+row.optString("casting_description")))
            }
            for ((target,pending) in staged) {
                // Install verified bytes without replacing a concurrently created reference.
                java.nio.file.Files.move(pending.toPath(),target.toPath())
                installed.add(target)
            }
        } catch (failure: Exception) {
            installed.asReversed().forEach { target ->
                try { java.nio.file.Files.delete(target.toPath()) } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
            }
            throw failure
        } finally {
            staged.values.forEach { it.delete() }
        }
        return result
    }
}
