package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WardrobeContextTest {
    @Test fun floodHelpFlagsQuestionableClothesWithoutRewritingTheStory() {
        val person=JSONObject().put("name","อาสา").put("outfit","Top: leather jacket; Bottom: black trousers; Shoes: black boots; Accessories: none")
        val scene=JSONObject().put("location","หมู่บ้านน้ำท่วม").put("action","ช่วยขนของ").put("characters",JSONArray().put(person))
        val before=scene.toString()
        assertEquals(1,WardrobeContext.warnings(scene).size)
        assertEquals(before,scene.toString())
        scene.put("action","เฝ้ามองจากบ้าน")
        assertTrue(WardrobeContext.warnings(scene).isEmpty())
    }
    @Test fun hospitalInstructionsDistinguishRoleAndDoNotInventUniforms() {
        assertTrue(WardrobeContext.planning.contains("patients from staff"))
        assertTrue(WardrobeContext.planning.contains("visitors wear ordinary clothes"))
        assertTrue(WardrobeContext.image.contains("written outfit and role"))
    }
}
