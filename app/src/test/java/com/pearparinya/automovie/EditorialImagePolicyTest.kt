package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class EditorialImagePolicyTest {
    @Test fun dramaGuidanceRequiresMeaningfulDialogueAndActionEnsembles() {
        val guidance=AutoDirectorContent.storyEnergy
        assertTrue(guidance.contains("most of the eight-second shot"))
        assertTrue(guidance.contains("two or three brief ordered turns"))
        assertTrue(guidance.contains("two or three registered relevant characters"))
        assertTrue(guidance.contains("Do not invent extra people"))
        assertTrue(AutoDirectorContent.generationRules.contains("DRAMATIC PACING"))
    }
    @Test fun storyWardrobeGuidanceSupportsRolesWithoutForcingHumanClothes() {
        val guidance=AutoDirectorContent.dailyWardrobe
        listOf("teachers", "doctors", "villagers", "Buddhist monks", "ghosts", "animals and creatures").forEach { assertTrue(guidance.contains(it)) }
        assertTrue(guidance.contains("never infer occupation from face"))
        assertTrue(guidance.contains("no forced human garments"))
        assertTrue(guidance.contains("existing story wardrobe"))
        assertTrue(AutoDirectorContent.generationRules.contains("ROLE AND SETTING WARDROBE"))
    }
    @Test fun imageRoutesShareRealismWhilePreservingReferencesAndExplicitOutfits() {
        val outfit="Top: opaque navy cotton blouse, V-neck, long-sleeve; Bottom: charcoal trousers; Shoes: black loafers; Accessories: one silver watch, left wrist"
        val references=listOf(
            ImagePromptPolicy.productionMaster("รินรดา","RINLADA_MASTER.png","long dark hair"),
            ImagePromptPolicy.scene("รินรดา: $outfit","office","hands over a folder"),
            ImagePromptPolicy.edit("scene image","แก้มือ","9:16",outfit),
            ImagePromptPolicy.coverReference("scene image"),
            BodyReferencePolicy.singleCheck("รินรดา","RINLADA_MASTER.png","",outfit,"9:16"),
            FlowVisualDirection.storyCover("9x16","refs","ช่อง","ชื่อเรื่อง","คำโปรย","folder"))
        references.forEach { assertTrue(it.contains(EditorialImagePolicy.referenceRendering)) }
        assertTrue(references[1].contains(outfit)); assertTrue(references[2].contains(outfit)); assertTrue(references[4].contains(outfit))
        assertFalse(references[0].contains(EditorialImagePolicy.freshDesign))
        assertFalse(references[1].contains(EditorialImagePolicy.freshDesign))
        assertTrue(AutoDirectorContent.femaleFashion.contains(EditorialImagePolicy.wardrobe))
        assertTrue(FlowVisualDirection.identity.contains(EditorialImagePolicy.wardrobe))
        val fresh=ImagePromptPolicy.newCharacter("style reference","office")
        assertTrue(fresh.contains(EditorialImagePolicy.freshDesign))
        assertTrue(fresh.contains(EditorialImagePolicy.referenceRendering))
        assertTrue(fresh.contains("9:16"))
        assertTrue(EditorialImagePolicy.referenceRendering.contains("Never enlarge, shrink or exaggerate reference anatomy"))
        assertTrue(EditorialImagePolicy.wardrobe.contains("Same story day repeats the exact approved outfit"))
    }
}
