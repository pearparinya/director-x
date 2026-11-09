package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterDiversityTest {
    @Test fun unclothedAnimalDoesNotRequireHumanWardrobe() {
        val animal=JSONObject().put("name","แมว").put("character_type","animal").put("outfit","none")
        val scene=JSONObject().put("scene",1).put("characters",JSONArray().put(animal))
        assertTrue(AutoDirectorWardrobe.issues(listOf(AutoDirectorWardrobe.Shot(1,scene))).isEmpty())
        AutoDirectorContent.validateWardrobes(JSONObject().put("scenes",JSONArray().put(scene)),true)
    }

    @Test fun humanCannotBypassWardrobeByUsingNone() {
        val human=JSONObject().put("name","เด็ก").put("character_type","human").put("outfit","none")
        val scene=JSONObject().put("scene",1).put("characters",JSONArray().put(human))
        assertFalse(AutoDirectorWardrobe.issues(listOf(AutoDirectorWardrobe.Shot(1,scene))).isEmpty())
    }

    @Test fun mechanicalAndMythicalFormsCanRemainUnclothed() {
        for(type in listOf("robot", "spirit", "deity_or_demon", "fantasy", "other")) {
            val character=JSONObject().put("name",type).put("character_type",type).put("outfit","none")
            val scene=JSONObject().put("scene",1).put("characters",JSONArray().put(character))
            assertTrue(AutoDirectorWardrobe.issues(listOf(AutoDirectorWardrobe.Shot(1,scene))).isEmpty())
            AutoDirectorContent.validateWardrobes(JSONObject().put("scenes",JSONArray().put(scene)),true)
        }
    }

    @Test fun castingPromptPreservesSpeciesAndAssignedAge() {
        val animal=JSONObject().put("id","CHAR_201").put("character_type","animal").put("character_age",0)
        val item=AutoCast.promptCatalog(JSONArray().put(animal)).getJSONObject(0)
        assertTrue(item.getString("character_type")=="animal" && item.getInt("character_age")==0)
    }
}
