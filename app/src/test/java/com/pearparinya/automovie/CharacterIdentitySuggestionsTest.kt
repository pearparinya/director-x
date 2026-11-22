package com.pearparinya.automovie

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CharacterIdentitySuggestionsTest {
    private fun person(name:String,master:String)=JSONObject().put("name",name).put("master",master)
    @Test fun removesHonorificsButDoesNotAssignIdentity() {
        assertEquals("pia",CharacterIdentitySuggestions.normalized("Mr. Pia"))
        assertEquals("pia",CharacterIdentitySuggestions.normalized("Dr Ms. Pia"))
        assertEquals("pia",CharacterIdentitySuggestions.normalized("Pia"))
        assertEquals("peter",CharacterIdentitySuggestions.normalized("Peter"))
    }
    @Test fun onlySuggestsUniqueMasterBackedExactName() {
        val cast=listOf(person("นายเปีย","master-a"),person("นายพีท","master-b"))
        val definitions=listOf(
            person("นายเปีย","master-a").put("english_name","Pia"),
            person("นายพีท","master-b").put("english_name","Peter")
        )
        assertEquals("นายเปีย",CharacterIdentitySuggestions.suggested("Mr. Pia",cast,definitions).single().getString("name"))
        assertTrue(CharacterIdentitySuggestions.suggested("Mr. Paul",cast,definitions).isEmpty())
    }
    @Test fun refusesAmbiguousRegistryDefinitions() {
        val cast=listOf(person("นายเปีย","master-a"))
        val defs=listOf(person("นายเปีย","master-a").put("english_name","Pia"),
            person("นายเปีย","master-b").put("english_name","Pia"))
        assertTrue(CharacterIdentitySuggestions.suggested("Mr. Pia",cast,defs).isEmpty())
    }
}
