package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class WardrobeEvidenceTest {
    @Test fun blankAndAllNoneAreAmbiguousRatherThanConfirmed() {
        assertTrue(WardrobeEvidence.isUnknownOrAllNone(""))
        assertTrue(WardrobeEvidence.isAllNone("Top: none; Bottom: none; Shoes: none; Accessories: none"))
        assertTrue(WardrobeEvidence.isAllNone("Top: ไม่มี; Bottom: N/A; Shoes: none; Accessories: not applicable"))
    }
    @Test fun armorAndAccessoriesMustNotBeClassedAsAllNone() {
        assertFalse(WardrobeEvidence.isAllNone("Top: armor; Bottom: none; Shoes: none; Accessories: none"))
        assertFalse(WardrobeEvidence.isAllNone("Top: none; Bottom: none; Shoes: none; Accessories: amulet"))
        assertFalse(WardrobeEvidence.isAllNone("unparseable"))
    }
    @Test fun masterOnlyWhenOutfitExplicitlyRequestsIt() {
        assertTrue(WardrobeEvidence.useMasterOutfit("use MASTER outfit"))
        assertTrue(WardrobeEvidence.useMasterOutfit("ใช้ชุด MASTER"))
        assertTrue(WardrobeEvidence.useMasterOutfit("ใช้ชุดมาสเตอร์"))
        assertFalse(WardrobeEvidence.useMasterOutfit("ไม่ใช้ชุดมาสเตอร์"))
        assertFalse(WardrobeEvidence.useMasterOutfit("Top: silver dress; Bottom: none"))
        assertFalse(WardrobeEvidence.useMasterOutfit("do not use MASTER outfit"))
    }
}
