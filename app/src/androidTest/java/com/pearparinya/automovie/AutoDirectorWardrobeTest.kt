package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoDirectorWardrobeTest {
    private val clothes="Top: white opaque silk long-sleeve V-neck blouse, fitted waist, plain fabric, three pearl buttons; Bottom: black straight ankle-length wool trousers; Shoes: black leather closed-toe 5 cm heels; Accessories: two small silver round stud earrings, one per ear; one thin silver chain with round pendant at neck; one silver watch on left wrist; no rings or bracelets"
    private fun scene(n: Int, outfit: String=clothes, day: String="DAY01", event: String="HOME01", group: String="TALK01", name: String="รินรดา", master: String="RINLADA_MASTER.png") = JSONObject()
        .put("scene",n).put("story_day",day).put("wardrobe_event",event).put("continuity_group",group).put("location","home").put("story_time","morning")
        .put("characters",JSONArray().put(JSONObject().put("name",name).put("master",master).put("outfit",outfit)))
    private fun shot(n: Int, scene: JSONObject)=AutoDirectorWardrobe.Shot(n,scene)

    @Test fun exactClothesSurviveAbsenceCameraChangeAndEpisodeBoundary() {
        val first=scene(1)
        val other=scene(2,name="กวิน",master="GAWIN_MASTER.png")
        val later=scene(1).put("framing","close-up").put("activity","argument")
        val rows=listOf(shot(1,first),shot(1,other),shot(2,later))
        assertTrue(AutoDirectorWardrobe.issues(rows).isEmpty())
        val text=AutoDirectorWardrobe.catalog(rows)
        assertEquals(1,Regex("รินรดา \\| day=").findAll(text).count())
        assertTrue(text.contains(clothes)); assertTrue(text.contains("silver watch on left wrist"))
    }
    @Test fun sleeveColorJewelryMetalCountAndWearingSideCannotDrift() {
        for(changed in listOf(clothes.replace("white opaque","cream opaque"),clothes.replace("long-sleeve","short-sleeve"),
            clothes.replace("silver round stud","gold round stud"),clothes.replace("two small","one small"),clothes.replace("left wrist","right wrist"))) {
            val errors=AutoDirectorWardrobe.issues(listOf(shot(1,scene(1)),shot(2,scene(8,changed))))
            assertEquals(1,errors.size); assertTrue(errors.first().contains("EP1 SC1")); assertTrue(errors.first().contains("EP2 SC8"))
        }
    }
    @Test fun wholeDayLocksWardrobeEvenWhenBothEventIdentifiersAndLocationChange() {
        val red=clothes.replace("white opaque","red opaque")
        assertTrue(AutoDirectorWardrobe.issues(listOf(shot(1,scene(1)),shot(1,scene(2,red,event="NEW_EVENT")))).isNotEmpty())
        assertTrue(AutoDirectorWardrobe.issues(listOf(shot(1,scene(1)),shot(1,scene(2,red,group="NEW_GROUP")))).isNotEmpty())
        assertTrue(AutoDirectorWardrobe.issues(listOf(shot(1,scene(1)),shot(1,scene(2,red,event="DINNER",group="DINNER01")))).isNotEmpty())
        val later=scene(1,event="DINNER",group="DINNER01").put("location","restaurant").put("story_time","evening").put("activity","party")
        assertTrue(AutoDirectorWardrobe.issues(listOf(shot(1,scene(1)),shot(2,later))).isEmpty())
        later.getJSONArray("characters").getJSONObject(0).put("outfit",red)
        assertTrue(AutoDirectorWardrobe.issues(listOf(shot(1,scene(1)),shot(2,later))).isNotEmpty())
        // A bridge must satisfy BOTH known identifiers, not merely whichever was looked up first.
        assertTrue(AutoDirectorWardrobe.issues(listOf(shot(1,scene(1)),shot(1,scene(2,red,event="B",group="B")),shot(1,scene(3,event="HOME01",group="B")))).isNotEmpty())
    }
    @Test fun newDayRequiresNewTopAndBottomAndReturningToAnOldDayRestoresItsOwnOutfit() {
        val next=clothes.replace("white opaque","red opaque").replace("black straight","navy straight")
        val first=shot(1,scene(1)); val second=shot(2,scene(1,next,day="DAY02"))
        assertTrue(AutoDirectorWardrobe.issues(listOf(first,second,shot(3,scene(1)))).isEmpty())
        assertTrue(AutoDirectorWardrobe.issues(listOf(first,shot(2,scene(1,day="DAY02")))).isNotEmpty())
        for(partial in listOf(clothes.replace("white opaque","red opaque"),clothes.replace("black straight","navy straight"))) {
            assertTrue(AutoDirectorWardrobe.issues(listOf(first,shot(2,scene(1,partial,day="DAY02")))).isNotEmpty())
        }
        val dress="Top: red opaque fitted deep-V dress; Bottom: none; Shoes: black heels; Accessories: none"
        assertTrue(AutoDirectorWardrobe.issues(listOf(shot(1,scene(1,dress)),shot(2,scene(1,dress.replace("red opaque","blue opaque"),day="DAY02")))).isEmpty())
        assertTrue(AutoDirectorWardrobe.issues(listOf(shot(1,scene(1,day="")))).any { it.contains("story_day") })
    }
    @Test fun sameDayTextCannotChangeCapitalizationSpacingOrPunctuation() {
        for(changed in listOf(clothes.replace("white","White"),clothes.replace("silk ","silk  "),clothes+";")) {
            assertTrue(AutoDirectorWardrobe.issues(listOf(shot(1,scene(1)),shot(2,scene(2,changed)))).any { it.contains("ทุกตัวอักษร") })
        }
    }
    @Test fun v45RequiresPreciseShadeHexAndExplicitSleeveLength() {
        val precise="Top: fitted deep-V silk blouse, shade=deep ruby red #9B111E; sleeves=long-sleeve; Bottom: fitted wool skirt, shade=ink black #101010; Shoes: closed-toe heels, shade=jet black #0A0A0A; Accessories: none"
        AutoDirectorWardrobe.v45Details(precise)
        AutoDirectorWardrobe.v45Details(precise.replace("fitted wool skirt, shade=ink black #101010","none"))
        listOf(precise.replace("sleeves=long-sleeve","sleeves=unknown"),precise.replace("#9B111E","red"),precise.replace("#0A0A0A","black")).forEach {
            assertTrue(runCatching { AutoDirectorWardrobe.v45Details(it) }.isFailure)
        }
        assertTrue(runCatching { AutoDirectorWardrobe.v45Details(clothes) }.isFailure)
    }
    @Test fun completePartsAllowExplicitNoneButRejectPlaceholdersDuplicatesAndEmptyJewelry() {
        assertEquals(4,AutoDirectorWardrobe.parts(clothes).size)
        val simple="Top: white shirt; Bottom: black trousers; Shoes: black loafers; Accessories: none"
        assertEquals("none",AutoDirectorWardrobe.parts(simple)["Accessories"])
        for(invalid in listOf(simple.replace("Accessories: none","Accessories:"),simple+"; Top: red shirt",simple.replace("white shirt","same as before"))) {
            assertTrue(runCatching { AutoDirectorWardrobe.parts(invalid) }.isFailure)
        }
        assertTrue(AutoDirectorWardrobe.contract(scene(1)).contains("Reference-image jewelry is not wardrobe authority"))
    }
}
