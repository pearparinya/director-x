package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CinematicPropStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun saveReloadAndClearInventory() {
        val store = CinematicPropStore(context)
        val sceneId = "test_prop_store_roundtrip"
        store.clear(sceneId)
        try {
            assertFalse(store.hasSavedInventory(sceneId))
            assertTrue(store.load(sceneId).isEmpty())
            val props = listOf(CinematicProp("cup", true, true, "left", "left", 1, 1))
            assertTrue(store.save(sceneId, props))
            assertTrue(store.hasSavedInventory(sceneId))
            assertEquals(props, CinematicPropStore(context).load(sceneId))
        } finally {
            assertTrue(store.clear(sceneId))
        }
        assertFalse(store.hasSavedInventory(sceneId))
    }

    @Test fun rejectedInventoryDoesNotOverwriteSavedData() {
        val store = CinematicPropStore(context)
        val sceneId = "test_prop_store_invalid"
        store.clear(sceneId)
        val original = listOf(CinematicProp("table", true, false))
        try {
            assertTrue(store.save(sceneId, original))
            try {
                store.save(sceneId, listOf(CinematicProp("missing", false, true)))
                fail("Expected validation failure")
            } catch (_: IllegalArgumentException) {
                assertEquals(original, store.load(sceneId))
            }
        } finally {
            store.clear(sceneId)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankSceneIdentifierIsRejected() {
        CinematicPropStore(context).load(" ")
    }
}
