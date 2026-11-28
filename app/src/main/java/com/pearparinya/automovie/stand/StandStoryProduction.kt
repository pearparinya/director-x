package com.pearparinya.automovie.stand

data class StandStoryBeat(val scene: StandStoryScene, val narrative: String)
data class StandProducedStory(val ownership: StandOwnership, val episode: StandEpisode,
    val beats: List<StandStoryBeat>, val vitals: Map<String, StandVitals>)

object StandStoryProduction {
    fun generate(initial: StandOwnership, type: StandStoryType,
                 mode: StandWritingMode = StandWritingMode.KOTLIN,
                 priorVitals: Map<String, StandVitals> = emptyMap()): StandProducedStory {
        require(mode == StandWritingMode.KOTLIN) { "AI mode requires an AI provider" }
        val ranked = StandCombatCatalog.all.sortedWith(compareByDescending<StandCombatProfile> {
            when(type) {
                StandStoryType.INVESTIGATION, StandStoryType.SUPERNATURAL_MYSTERY -> it.speed
                StandStoryType.RESCUE, StandStoryType.ALLIANCE -> it.defense
                else -> it.power
            }
        }.thenBy { it.id })
        var state = initial
        fun select(owner: String, excluded: Set<String>): String {
            val existing = state.standsOf(owner).sorted().firstOrNull { it !in excluded }
            if (existing != null) return existing
            val id = ranked.first { it.id !in excluded && state.ownerOf(it.id) == null &&
                StandCatalog.permitsOwner(state.project.channel, it.id, owner) }.id
            state = state.assign(id, owner)
            return id
        }
        val protagonist = if (state.project.channel == StandChannel.PIA) StandCatalog.PIEROS
            else state.snapshot().values.sorted().firstOrNull() ?: "hero"
        val first = select(protagonist, emptySet())
        val rival = state.snapshot().values.sorted().firstOrNull { it != protagonist } ?: "rival".let {
            if (it == protagonist) "rival-2" else it
        }
        val second = select(rival, setOf(first))
        val a = StandPresence(first, protagonist, true)
        val b = StandPresence(second, rival, true)
        val pa = StandCombatCatalog.profile(first)
        val pb = StandCombatCatalog.profile(second)
        fun fighter(p: StandPresence): StandFighter {
            val old = priorVitals[p.standId] ?: StandVitals(100,100)
            // Between episodes, rest restores a limited amount; no silent full reset.
            return StandFighter(p, (old.health + 25).coerceAtMost(100),
                (old.energy + 30).coerceAtMost(100))
        }
        val opening = when(type) {
            StandStoryType.INVESTIGATION, StandStoryType.SUPERNATURAL_MYSTERY ->
                "ตรวจสอบร่องรอยผิดปกติในตรอกเงียบและค้นหาต้นเหตุ"
            StandStoryType.RESCUE -> "พบผู้ที่ต้องการความช่วยเหลือและวางแผนเปิดทางปลอดภัย"
            StandStoryType.ALLIANCE -> "พบคู่เจรจาเพื่อทดสอบความไว้วางใจ"
            else -> "เผชิญคู่แข่งในพื้นที่เปิดและประเมินภัยคุกคาม"
        }
        val firstAction = when(type) {
            StandStoryType.INVESTIGATION, StandStoryType.SUPERNATURAL_MYSTERY -> StandCombatAction.GUARD
            StandStoryType.RESCUE, StandStoryType.ALLIANCE -> StandCombatAction.ATTACK
            else -> StandCombatAction.SPECIAL
        }
        val secondAction = when(type) {
            StandStoryType.ALLIANCE, StandStoryType.RESCUE -> StandCombatAction.GUARD
            StandStoryType.INVESTIGATION, StandStoryType.SUPERNATURAL_MYSTERY -> StandCombatAction.ATTACK
            else -> StandCombatAction.SPECIAL
        }
        val turn = StandCombatEngine.resolve(state, fighter(a), fighter(b), firstAction, secondAction)
        val beats = listOf(
            StandStoryBeat(StandStoryScene(1, listOf(a.dismiss(), b.dismiss())),
                "สถานที่และเป้าหมาย: " + opening + ". " + protagonist + " พบ " + rival +
                    " โดยยังไม่เรียกสแตนด์; เริ่มด้วยทั้งสองยืนห่างกันและจบด้วยการตัดสินใจเผชิญหน้า"),
            StandStoryBeat(StandStoryScene(2, listOf(a,b)),
                "เริ่มฉาก: สแตนด์ทั้งสองปรากฏข้างเจ้าของตาม MASTER; " +
                    first + " เลือก " + firstAction.name + " (" + pa.ability + ") และ " +
                    second + " เลือก " + secondAction.name + " (" + pb.ability +
                    "); แสดงพลังเฉพาะเมื่อเลือก SPECIAL; จบเมื่อเริ่มการปะทะ"),
            StandStoryBeat(StandStoryScene(3, listOf(a,b)),
                "เริ่มจากตำแหน่งท้ายฉากก่อน; " + (if(turn.firstActsFirst) first else second) +
                    " เคลื่อนไหวก่อนตามความเร็วและพลัง; ผลการปะทะ: " + first +
                    " พลังชีวิต " + turn.first.health + " พลังงาน " + turn.first.energy +
                    "; " + second + " พลังชีวิต " + turn.second.health + " พลังงาน " +
                    turn.second.energy + ". จุดอ่อน: " + pa.weakness + "; " + pb.weakness +
                    ". จบด้วยการหยุดปะทะโดยไม่ฟื้นพลังทันที"),
            StandStoryBeat(StandStoryScene(4, listOf(a.dismiss(),b.dismiss())),
                "หลังการปะทะ เจ้าของทั้งสองยืนในพื้นที่เดิมโดยสแตนด์ถูกเก็บแล้ว; " +
                    protagonist + " ประเมินผลและเลือกเป้าหมายต่อไป; คงบาดแผลและผลการปะทะไว้สำหรับ EP ถัดไป")
        )
        val episode = StandEpisode(state.project, 1, type, mode, beats.map { it.scene })
        check(StandStoryEngine.validate(state, episode))
        check(initial.snapshot().all { (id, owner) -> state.isOwner(id, owner) })
        return StandProducedStory(state, episode, beats, priorVitals + mapOf(
            first to StandVitals(turn.first.health, turn.first.energy),
            second to StandVitals(turn.second.health, turn.second.energy)))
    }
}
