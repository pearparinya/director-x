package com.pearparinya.automovie.stand

enum class StandChannel(val id: String, val title: String) {
    PIA("PIA_STAND", "ศึกสแตนด์เหนือโชคชะตา"),
    UNIVERSE("UNIVERSE_STAND", "มหาศึกจักรวาลสแตนด์")
}

data class StandDefinition(val id: String, val name: String, val supreme: Boolean = false)

object StandCatalog {
    const val PIEROS = "PIEROS"
    const val SUPREME_ID = "ST-013"
    val all: List<StandDefinition> = listOf(
        "VIOLET EMPEROR", "CHRONOS REQUIEM", "GRAVITY KING", "POLAR DOMINION",
        "MIND SERPENT", "IRON TITAN", "CRYSTAL MIRAGE", "SILENT WING",
        "COSMIC ETERNITY", "CRIMSON REAPER", "INFERNO COLOSSUS", "PHARAOH JUDGMENT",
        "VOID ORACLE: ABSOLUTE SOVEREIGN", "CRIMSON WARLORD", "DESTINY CLOCK", "SOLAR PHOENIX"
    ).mapIndexed { index, name ->
        val id = "ST-" + (index + 1).toString().padStart(3, '0')
        StandDefinition(id, name, id == SUPREME_ID)
    }
    fun find(id: String): StandDefinition? = all.firstOrNull { it.id == id }

    fun permitsOwner(channel: StandChannel, standId: String, ownerId: String): Boolean {
        if (find(standId) == null || ownerId.isBlank()) return false
        if (channel == StandChannel.PIA) {
            return (standId == SUPREME_ID) == (ownerId == PIEROS)
        }
        return true
    }
}
