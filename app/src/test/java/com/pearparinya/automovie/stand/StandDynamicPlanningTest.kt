package com.pearparinya.automovie.stand
import org.junit.Assert.*
import org.junit.Test

class StandDynamicPlanningTest {
    private val arc = StandLongTermArc("Protect the universe",5)
    private val plan = StandSeriesPlan(arc,StandEnding.HOPEFUL)
    private val owners = mapOf("ST-013" to "PIEROS")
    private fun ep(n: Int) = StandPersistentEpisode(n,"Episode "+n,owners,emptyMap())
    @Test fun recommendsOrigin() {
        assertEquals(StandStoryType.ORIGIN,StandDynamicPlanning.recommend(plan,emptyList()))
    }
    @Test fun recommendsClimax() {
        assertEquals(StandStoryType.FINAL_CONFLICT,StandDynamicPlanning.recommend(plan,(1..3).map { ep(it) }))
    }
    @Test fun recommendsResolution() {
        assertEquals(StandStoryType.ALLIANCE,StandDynamicPlanning.recommend(plan,(1..4).map { ep(it) }))
    }
    @Test fun finalRejectsUnresolvedPlot() {
        assertFalse(StandPlotContinuity.validate(5,owners,arc,emptyList(),emptyList(),
            emptyList(),listOf(StandPlotThread("p","Mystery",1))))
    }
    @Test fun achievedGoalCannotRegress() {
        val goal = StandCharacterGoal("PIEROS","Protect",true)
        assertFalse(StandPlotContinuity.validate(2,owners,arc,listOf(goal),listOf(goal.copy(achieved=false)),emptyList(),emptyList()))
    }
}
