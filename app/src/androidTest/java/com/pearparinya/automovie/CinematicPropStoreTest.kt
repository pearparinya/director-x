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
    @Test fun savedInventoryAppearsInGeneratedFlowPrompt() {
        val store = CinematicPropStore(context)
        val sceneId = "test_prop_store_prompt"
        store.clear(sceneId)
        try {
            assertTrue(store.save(sceneId, listOf(CinematicProp("cup", true, true, "left", "left", 1, 1))))
            val prompt = store.buildScenePrompt(sceneId, sceneSpec())
            assertTrue(prompt.contains("Prop cup: exactly 1 visible at start"))
        } finally {
            store.clear(sceneId)
        }
    }

    @Test fun unsavedSceneInventoryOverridesStoredInventory() {
        val store = CinematicPropStore(context)
        val sceneId = "test_prop_store_override"
        store.clear(sceneId)
        try {
            assertTrue(store.save(sceneId, listOf(CinematicProp("cup", true, true, firstFrameCount = 1))))
            val spec = sceneSpec().copy(cinematicProps = listOf(CinematicProp("book", true, false, firstFrameCount = 1)))
            val prompt = store.buildScenePrompt(sceneId, spec)
            assertTrue(prompt.contains("Prop book: exactly 1 visible at start"))
            assertFalse(prompt.contains("Prop cup:"))
        } finally {
            store.clear(sceneId)
        }
    }

    private fun sceneSpec() = FlowSceneSpec(
        cast = listOf(FlowPerson("A", "adult", "center", "shirt")),
        speaker = "A", dialogue = "สวัสดี", narration = "",
        action = "A stands still", location = "room",
        startState = "standing", endState = "standing",
        duration = 8, aspect = "9:16"
    )

    @Test fun explicitlyClearedUnsavedInventoryDoesNotRestoreSavedProp() {
        val store = CinematicPropStore(context)
        val sceneId = "test_prop_store_explicit_empty"
        store.clear(sceneId)
        try {
            assertTrue(store.save(sceneId, listOf(CinematicProp("cup", true, true, firstFrameCount = 1))))
            val prompt = store.buildScenePrompt(sceneId, sceneSpec(), useSpecInventory = true)
            assertFalse(prompt.contains("Prop cup:"))
            assertTrue(store.hasSavedInventory(sceneId))
            assertEquals(1, store.load(sceneId).size)
        } finally {
            store.clear(sceneId)
        }
    }

    @Test fun editorOperationsPersistAndReload() {
        val store = CinematicPropStore(context)
        val sceneId = "test_prop_store_editor_operations"
        store.clear(sceneId)
        try {
            val cup = CinematicProp("cup", true, true, firstFrameCount = 1)
            assertTrue(store.addProp(sceneId, cup))
            assertEquals(listOf(cup), CinematicPropStore(context).load(sceneId))
            val glass = cup.copy(id = "glass")
            assertTrue(store.replaceProp(sceneId, "CUP", glass))
            assertEquals(listOf(glass), store.load(sceneId))
            assertTrue(store.removeProp(sceneId, "GLASS"))
            assertTrue(store.load(sceneId).isEmpty())
        } finally {
            store.clear(sceneId)
        }
    }

}
