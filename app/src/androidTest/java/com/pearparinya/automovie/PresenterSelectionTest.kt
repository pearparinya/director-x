package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresenterSelectionTest {
    @Test fun selectedMasterMustBeInCastWithoutChangingLegacyCasting() {
        val value=JSONObject().put("cast",JSONArray().put(JSONObject().put("library_id","actor_1")))
        AutoCast.requirePresenter(value,"actor_1")
        AutoCast.requirePresenter(value,"")
        assertTrue(runCatching { AutoCast.requirePresenter(value,"actor_2") }.isFailure)
    }
    @Test fun personalCategoriesExistWithoutRemovingDramaCategories() {
        val ids=ChannelCatalog.mainCategories.map { it.id }.toSet()
        assertTrue(ids.containsAll(listOf("pia_benefits","pia_safety","pia_products","office_career")))
    }
}
