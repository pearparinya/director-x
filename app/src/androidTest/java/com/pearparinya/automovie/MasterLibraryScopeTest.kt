package com.pearparinya.automovie

import androidx.test.core.app.ActivityScenario
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterLibraryScopeTest {
    @Test fun libraryIncludesEveryBundledTypeWhilePresenterCastingStaysHuman() {
        ActivityScenario.launch(StudioActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val catalog = StudioActivity::class.java.getDeclaredMethod("piaCatalog", Boolean::class.javaPrimitiveType)
                catalog.isAccessible = true
                fun types(includeAll: Boolean): Set<String> {
                    val rows = catalog.invoke(activity, includeAll) as JSONArray
                    return (0 until rows.length()).map { rows.getJSONObject(it).optString("character_type") }.toSet()
                }
                assertTrue(types(true).containsAll(listOf("animal","creature","demon","deity","knight","ghost","spirit","child")))
                assertTrue(types(false).all { it.isEmpty() || it == "human" })
            }
        }
    }
}
