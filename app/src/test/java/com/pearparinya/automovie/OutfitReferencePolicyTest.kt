package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class OutfitReferencePolicyTest {
    @Test fun selectsOnlyApprovedSinglePersonSameMasterDayAndOutfit() {
        val good=OutfitReferencePolicy.Candidate("EP01_SC001", "MASTER_A", "DAY01", "shirt and trousers", true, 1)
        val samples=listOf(good.copy(sceneId="bad1",approved=false),good.copy(sceneId="bad2",people=2),
            good.copy(sceneId="bad3",master="MASTER_B"),good.copy(sceneId="bad4",day="DAY02"),
            good.copy(sceneId="bad5",outfit="different"),good)
        assertEquals(good,OutfitReferencePolicy.choose(samples,"EP01_SC002","master_a","DAY01","shirt and trousers"))
        assertNull(OutfitReferencePolicy.choose(listOf(good),"EP01_SC001","MASTER_A","DAY01","shirt and trousers"))
        assertNull(OutfitReferencePolicy.choose(listOf(good),"EP01_SC002","MASTER_A","","shirt and trousers"))
        assertNull(OutfitReferencePolicy.choose(listOf(good),"EP01_SC002","MASTER_A","DAY01",""))
    }
}
