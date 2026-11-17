package com.pearparinya.automovie

import org.json.JSONObject

/** Surface observable inconsistencies for human review, without claiming semantic verification. */
internal object ContinuityReview {
    internal fun continuityGroupLocationIssue(
        group: String, previousLocation: String, currentLocation: String
    ): String? {
        if(group.isBlank() || previousLocation.isBlank() || currentLocation.isBlank()) return null
        if(previousLocation.trim().equals(currentLocation.trim(), ignoreCase = true)) return null
        return "กลุ่มต่อเนื่อง $group ใช้สถานที่ต่างกัน ($previousLocation / $currentLocation) โปรดตรวจว่ามีการย้ายสถานที่ในเนื้อเรื่อง"
    }
    internal fun continuityGroupTimeIssue(
        group: String, previousTime: String, currentTime: String
    ): String? {
        if(group.isBlank() || previousTime.isBlank() || currentTime.isBlank()) return null
        fun period(value: String): String? {
            val text = value.trim().lowercase()
            return when(text) {
                "day", "daytime", "daylight", "กลางวัน", "ตอนกลางวัน" -> "day"
                "night", "nighttime", "กลางคืน", "ตอนกลางคืน" -> "night"
                else -> null
            }
        }
        val before = period(previousTime)
        val after = period(currentTime)
        if(before == null || after == null || before == after) return null
        return "กลุ่มต่อเนื่อง $group เปลี่ยนช่วงเวลากลางวัน/กลางคืน ($previousTime / $currentTime) โปรดตรวจการเปลี่ยนเวลาในเนื้อเรื่อง"
    }
    internal fun meaningfulTransitionNote(note: String): Boolean {
        val normalized = note.trim().lowercase().replace(Regex("\\s+"), " ")
        val placeholders = setOf("none", "n/a", "na", "todo", "tbd", "unknown", "ไม่มี", "ไม่ทราบ", "รอแก้ไข", "ตรวจสอบ", "-", "?")
        return normalized.length >= 12 && normalized !in placeholders &&
            normalized.any { it.isLetter() } && normalized.toSet().size >= 4
    }
    internal fun adjacentSceneStateIssue(
        group: String, previousEnd: String, nextStart: String, transitionNote: String = ""
    ): String? {
        if(group.isBlank() || previousEnd.isBlank() || nextStart.isBlank()) return null
        fun normalized(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ")
        if(normalized(previousEnd) == normalized(nextStart)) return null
        if(meaningfulTransitionNote(transitionNote)) return null // Plausible documentation only; semantics still need human review.
        return "กลุ่มต่อเนื่อง $group สถานะจบฉากก่อนหน้าไม่ตรงกับสถานะเริ่มฉากถัดไป โปรดตรวจการเปลี่ยนแปลงของตัวละครและสิ่งของ"
    }
    internal fun propContinuityIssue(
        prop: String, previousState: String, currentState: String, transitionNote: String
    ): String? {
        if(prop.isBlank() || previousState.isBlank() || currentState.isBlank()) return null
        fun normalize(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ")
        if(normalize(previousState) == normalize(currentState)) return null
        if(meaningfulTransitionNote(transitionNote)) return null
        return "สิ่งของ $prop เปลี่ยนสถานะ ($previousState / $currentState) โดยไม่มีคำอธิบายการเปลี่ยนแปลง โปรดตรวจ"
    }
    internal fun propEntryIssues(entries: List<Pair<String, String>>): List<String> = buildList {
        val seen = mutableSetOf<String>()
        entries.forEachIndexed { index, (name, state) ->
            val key = name.trim().lowercase()
            if(key.isEmpty()) add("สิ่งของลำดับ ${index+1} ขาดชื่อ")
            if(state.isBlank()) add("สิ่งของลำดับ ${index+1} ขาดสถานะ")
            if(key.isNotEmpty() && !seen.add(key)) add("สิ่งของลำดับ ${index+1} ชื่อ $name ซ้ำในฉากเดียวกัน")
        }
    }
    internal fun missingPropIssues(
        previousNames: Set<String>, currentNames: Set<String>, removedNames: Set<String>
    ): List<String> {
        val current = currentNames.map { it.trim().lowercase() }.toSet()
        val removed = removedNames.map { it.trim().lowercase() }.toSet()
        return previousNames.map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it !in current && it !in removed }
            .distinct()
            .map { "สิ่งของ $it ไม่ปรากฏในรายการฉากถัดไป โปรดตรวจว่าออกจากฉากโดยตั้งใจหรือข้อมูลตกหล่น" }
    }
    internal fun removedPropIssues(
        previousNames: Set<String>, currentNames: Set<String>, removedNames: List<String>
    ): List<String> = buildList {
        val previous = previousNames.map { it.trim().lowercase() }.toSet()
        val current = currentNames.map { it.trim().lowercase() }.toSet()
        val seen = mutableSetOf<String>()
        removedNames.forEachIndexed { index, raw ->
            val name = raw.trim().lowercase()
            when {
                name.isEmpty() -> add("removed_props ลำดับ ${index+1} ขาดชื่อสิ่งของ")
                !seen.add(name) -> add("removed_props ลำดับ ${index+1} ระบุสิ่งของ $raw ซ้ำ")
                name !in previous -> add("removed_props ลำดับ ${index+1} สิ่งของ $raw ไม่อยู่ในฉากก่อนหน้า")
                name in current -> add("removed_props ลำดับ ${index+1} สิ่งของ $raw ยังอยู่ในฉากปัจจุบัน")
            }
        }
    }
    internal fun returnedPropIssues(
        removedHistory: Set<String>, currentNames: Set<String>, returnedNames: List<String>
    ): List<String> = buildList {
        val history = removedHistory.map { it.trim().lowercase() }.toSet()
        val current = currentNames.map { it.trim().lowercase() }.toSet()
        val seen = mutableSetOf<String>()
        returnedNames.forEachIndexed { index, raw ->
            val name = raw.trim().lowercase()
            when {
                name.isEmpty() -> add("returned_props ลำดับ ${index+1} ขาดชื่อสิ่งของ")
                !seen.add(name) -> add("returned_props ลำดับ ${index+1} สิ่งของ $raw ซ้ำ")
                name !in history -> add("returned_props ลำดับ ${index+1} สิ่งของ $raw ไม่เคยถูกนำออกจากฉาก")
                name !in current -> add("returned_props ลำดับ ${index+1} สิ่งของ $raw ไม่ปรากฏใน props ของฉากปัจจุบัน")
            }
        }
    }
    internal fun undocumentedPropReturns(
        removedHistory: Set<String>, currentNames: Set<String>, returnedNames: List<String>
    ): List<String> {
        val returned = returnedNames.map { it.trim().lowercase() }.toSet()
        return currentNames.map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it in removedHistory && it !in returned }
            .distinct()
            .map { "สิ่งของ $it กลับมาปรากฏในฉากโดยไม่ระบุ returned_props โปรดตรวจลำดับเหตุการณ์" }
    }
    /** Human-readable triage only: severity is heuristic, not a verified scene defect. */
    internal fun formatFinding(finding: String): String {
        val manual = finding.startsWith("ตรวจด้วยตนเอง:")
        val high = listOf("ขาดชื่อ", "ขาดสถานะ", "ไม่มีภาพ MASTER", "ใช้ MASTER ต่าง", "ซ้ำ")
            .any { finding.contains(it) } && !manual
        val level = when {
            manual -> "ข้อมูลเพิ่มเติม"
            high -> "ควรแก้ไข"
            else -> "ควรตรวจสอบ"
        }
        val action = when {
            manual -> "ตรวจความสมเหตุสมผลของเนื้อเรื่องและภาพจริงด้วยตนเอง"
            finding.contains("returned_props") -> "ตรวจประวัติการนำสิ่งของออกและการกลับเข้าฉาก"
            finding.contains("removed_props") -> "ตรวจรายการสิ่งของในฉากก่อนหน้าและฉากปัจจุบัน"
            finding.contains("สิ่งของ") -> "ตรวจ props และสถานะสิ่งของระหว่างฉาก"
            finding.contains("MASTER") -> "ตรวจการจับคู่ภาพ MASTER ของตัวละคร"
            finding.contains("ชุด") || finding.contains("story_day") -> "ตรวจวันในเรื่องและเครื่องแต่งกาย"
            else -> "ตรวจข้อมูลฉากและเหตุการณ์ที่เชื่อมต่อกัน"
        }
        return "[$level] $finding | แนวทาง: $action"
    }
    fun review(story: JSONObject): List<String> {
        val findings = mutableListOf<String>()
        val groups = mutableMapOf<String, Pair<String, String>>()
        val groupTimes = mutableMapOf<String, String>()
        val groupEndStates = mutableMapOf<String, String>()
        val groupPropStates = mutableMapOf<Pair<String, String>, String>()
        val groupPreviousProps = mutableMapOf<String, Set<String>>()
        val groupRemovedHistory = mutableMapOf<String, MutableSet<String>>()
        val dailyWardrobes = mutableMapOf<Pair<String, String>, Pair<String, String>>()
        val characterMasters = mutableMapOf<String, Pair<String, String>>()
        val episodes = story.optJSONArray("episodes") ?: return listOf("ไม่มีข้อมูล EP ให้ตรวจ")
        for (e in 0 until episodes.length()) {
            val ep = episodes.optJSONObject(e) ?: continue
            val scenes = ep.optJSONArray("scenes") ?: continue
            for (i in 0 until scenes.length()) {
                val scene = scenes.optJSONObject(i) ?: continue
                val label = "EP${e + 1} ซีน ${scene.optInt("scene", i + 1)}"
                val group = scene.optString("continuity_group").trim()
                val day = scene.optString("story_day").trim()
                val location = scene.optString("location").trim()
                val storyTime = scene.optString("story_time").trim()
                val characters = scene.optJSONArray("characters")
                val props = scene.optJSONArray("props")
                if(props != null) {
                    val entries = (0 until props.length()).map { p ->
                        val item = props.optJSONObject(p)
                        (item?.optString("name") ?: "") to (item?.optString("state") ?: "")
                    }
                    findings.addAll(propEntryIssues(entries).map { "$label: $it" })
                }
                if(group.isNotBlank() && props != null) {
                    val currentNames = (0 until props.length()).mapNotNull { p ->
                        props.optJSONObject(p)?.optString("name")?.trim()?.takeIf(String::isNotBlank)
                    }.toSet()
                    val removed = scene.optJSONArray("removed_props")?.let { a ->
                        (0 until a.length()).map { p -> a.optString(p) }
                    }.orEmpty()
                    val returned = scene.optJSONArray("returned_props")?.let { a ->
                        (0 until a.length()).map { p -> a.optString(p) }
                    }.orEmpty()
                    val history = groupRemovedHistory.getOrPut(group) { mutableSetOf() }
                    findings.addAll(returnedPropIssues(history, currentNames, returned).map { "$label: $it" })
                    findings.addAll(undocumentedPropReturns(history, currentNames, returned).map { "$label: $it" })
                    groupPreviousProps[group]?.let { previous ->
                        findings.addAll(removedPropIssues(previous, currentNames, removed).map { "$label: $it" })
                        findings.addAll(missingPropIssues(previous, currentNames, removed.toSet()).map { "$label: $it" })
                        val previousNormalized = previous.map { it.trim().lowercase() }.toSet()
                        val currentNormalized = currentNames.map { it.trim().lowercase() }.toSet()
                        removed.map { it.trim().lowercase() }
                            .filter { it.isNotBlank() && it in previousNormalized && it !in currentNormalized }
                            .forEach { history.add(it) }
                    }
                    returned.map { it.trim().lowercase() }
                        .filter { it in history && it in currentNames.map { name -> name.trim().lowercase() } }
                        .forEach { history.remove(it) }
                    groupPreviousProps[group] = currentNames
                }
                if(group.isNotBlank() && props != null) for(p in 0 until props.length()) {
                    val item = props.optJSONObject(p) ?: continue
                    val prop = item.optString("name").trim()
                    val state = item.optString("state").trim()
                    if(prop.isBlank() || state.isBlank()) continue
                    val key = group to prop.lowercase()
                    val previousState = groupPropStates[key]
                    if(previousState != null) propContinuityIssue(
                        prop, previousState, state, item.optString("transition")
                    )?.let { findings.add("$label: $it") }
                    groupPropStates[key] = state
                }

                val startState = scene.optString("start_state").trim()
                val endState = scene.optString("end_state").trim()
                if (characters != null) for (c in 0 until characters.length()) {
                    val character = characters.optJSONObject(c) ?: continue
                    val name = character.optString("name").trim()
                    if (name.isBlank()) continue
                    val master = character.optString("master").trim()
                    val outfit = character.optString("outfit").trim()
                    if (master.isBlank()) findings.add("$label: $name ไม่มีภาพ MASTER ที่ระบุไว้")
                    else {
                        val previousMaster = characterMasters[name]
                        if (previousMaster != null && previousMaster.first != master)
                            findings.add("$label: $name ใช้ MASTER ต่างจาก ${previousMaster.second} โปรดตรวจการจับคู่ภาพ")
                        else characterMasters.putIfAbsent(name, master to label)
                    }
                    if (day.isBlank()) {
                        findings.add("$label: $name ไม่ระบุ story_day จึงตรวจชุดประจำวันไม่ได้")
                    } else if (outfit.isBlank()) {
                        findings.add("$label: $name ไม่มีรายละเอียดชุดสำหรับ $day")
                    } else {
                        val key = name to day
                        val previousOutfit = dailyWardrobes[key]
                        if (previousOutfit != null && previousOutfit.first != outfit)
                            findings.add("$label: $name ชุดหรือรองเท้าใน $day ต่างจาก ${previousOutfit.second} โปรดตรวจ")
                        else dailyWardrobes.putIfAbsent(key, outfit to label)
                    }
                }
                if (scene.optString("start_state").isBlank() || scene.optString("end_state").isBlank()) findings.add("$label: ตรวจสถานะเริ่มต้นและสถานะสิ้นสุด")
                if (group.isNotBlank()) {
                    val previous = groups[group]
                    if (previous != null && day.isNotBlank() && previous.first.isNotBlank() && day != previous.first)
                        findings.add("$label: กลุ่มต่อเนื่อง $group ใช้วันต่างกัน (${previous.first} / $day) โปรดตรวจ")
                    if(previous != null) continuityGroupLocationIssue(group, previous.second, location)?.let {
                        findings.add("$label: $it")
                    }
                    val previousTime = groupTimes[group]
                    if(previousTime != null) continuityGroupTimeIssue(group, previousTime, storyTime)?.let {
                        findings.add("$label: $it")
                    }
                    if(storyTime.isNotBlank()) groupTimes.putIfAbsent(group, storyTime)
                    val previousEnd = groupEndStates[group]
                    if(previousEnd != null) adjacentSceneStateIssue(group, previousEnd, startState, scene.optString("continuity_transition"))?.let {
                        findings.add("$label: $it")
                    }
                    if(endState.isNotBlank()) groupEndStates[group] = endState
                    if (previous == null) groups[group] = day to location
                }
            }
        }
        findings.add("ตรวจด้วยตนเอง: ใครรู้หลักฐานเมื่อใด บาดแผล สิ่งของ อารมณ์ และเหตุการณ์ที่เชื่อมต่อระหว่าง EP • ระบบยังไม่ยืนยันความหมายหรือภาพจริง")
        return findings.distinct().map(::formatFinding)
    }
}
