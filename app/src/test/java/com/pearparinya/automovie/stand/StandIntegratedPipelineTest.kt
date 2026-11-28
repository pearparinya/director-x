package com.pearparinya.automovie.stand

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class StandIntegratedPipelineTest {
    @get:Rule val folder = TemporaryFolder()
    private val records = StandCatalog.all.map {
        StandMasterRecord(it.id, StandMasterIntegrity.digest(it.id.byteInputStream()))
    }

    @Test fun bothChannelsCompleteTwoScenePipeline() {
        for (channel in StandChannel.values()) {
            val project = StandProject(channel, "integration")
            val owner = if (channel == StandChannel.PIA) "PIEROS" else "universe-owner"
            val ownership = StandOwnership.restore(project).assign("ST-013", owner).assign("ST-001", "rival")
            val store = StandMasterStore(folder.newFolder(), records)
            for (id in listOf("ST-013", "ST-001")) assertTrue(store.importOriginal(id, id.byteInputStream()))
            val origin = StandPosition(0.0, 0.0)
            val a = StandConsciousnessEngine.react(ownership,
                StandMovementEngine.initial(ownership, "ST-013", owner, origin), true)
            val b = StandMovementEngine.summon(ownership,
                StandMovementEngine.initial(ownership, "ST-001", "rival", origin))
            val scene = StandStoryScene(1, listOf(a.presence, b.presence))
            val episode = StandEpisode(project, 1, StandStoryType.RIVALRY, StandWritingMode.AI_KOTLIN, listOf(scene))
            assertTrue(StandStoryEngine.validate(ownership, episode))
            val line = StandConsciousnessEngine.speak(ownership, a.presence, owner, "ready", StandSpeech.TELEPATHIC)
            val first = StandCinematicEngine.compose(ownership, scene, records, store, owner, "street",
                listOf(a,b), listOf(line))
            assertEquals(2, first.references.size)
            assertTrue(first.videoPrompt.contains("No lip movement"))
            val turn = StandCombatEngine.resolve(ownership, StandFighter(a.presence), StandFighter(b.presence),
                StandCombatAction.SPECIAL, StandCombatAction.GUARD)
            assertTrue(turn.second.health < 100)
            val moved = StandMovementEngine.moveStand(ownership, a, StandPosition(2.0, 0.0), true)
            val next = StandStoryEngine.nextScene(ownership, episode, listOf(moved.presence, b.presence))
            val second = StandCinematicEngine.compose(ownership, next.scenes.last(), records, store, owner,
                "street", listOf(moved,b), previous = first.continuity)
            assertEquals(first.continuity.identities, second.continuity.identities)
            assertEquals(2, second.continuity.sceneNumber)
            assertTrue(second.videoPrompt.contains("COMBAT"))
            assertTrue(StandCinematicEngine.compose(ownership, scene, records, store, "civilian",
                "street", listOf(a,b)).references.isEmpty())
        }
    }
}
