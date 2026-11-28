package com.pearparinya.automovie.stand

data class StandStoryBeat(val scene: StandStoryScene, val narrative: String)
data class StandProducedStory(val ownership: StandOwnership, val episode: StandEpisode,
    val beats: List<StandStoryBeat>)

object StandStoryProduction {
    fun generate(initial: StandOwnership, type: StandStoryType,
                 mode: StandWritingMode = StandWritingMode.KOTLIN): StandProducedStory {
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
        val turn = StandCombatEngine.resolve(state, StandFighter(a), StandFighter(b),
            StandCombatAction.SPECIAL, StandCombatAction.GUARD)
        val beats = listOf(
            StandStoryBeat(StandStoryScene(1, listOf(a.dismiss(), b.dismiss())),
                "เปิดเรื่อง: " + type.name + " เจ้าของ " + protagonist + " พบ " + rival),
            StandStoryBeat(StandStoryScene(2, listOf(a,b)),
                first + " ใช้ " + pa.ability + " ขณะที่ " + second + " ตั้งรับด้วยจุดแข็งของตน"),
            StandStoryBeat(StandStoryScene(3, listOf(a,b)),
                "ผลการปะทะ: " + first + " พลังชีวิต " + turn.first.health + " พลังงาน " +
                    turn.first.energy + "; " + second + " พลังชีวิต " + turn.second.health +
                    ". จุดอ่อนที่ต้องระวัง: " + pa.weakness + "; " + pb.weakness),
            StandStoryBeat(StandStoryScene(4, listOf(a.dismiss(),b.dismiss())),
                "ปิดเหตุการณ์และเก็บผลการปะทะเพื่อวางบทต่อไป")
        )
        val episode = StandEpisode(state.project, 1, type, mode, beats.map { it.scene })
        check(StandStoryEngine.validate(state, episode))
        check(initial.snapshot().all { (id, owner) -> state.isOwner(id, owner) })
        return StandProducedStory(state, episode, beats)
    }
}
