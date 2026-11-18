package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessoryContinuityContractTest {
    @Test fun rightWristIsAnatomicalAndExplicit() {
        val actor=JSONObject().put("name","นายเปีย").put("watch_side","right").put("outfit","blue shirt and wristwatch")
        val instruction=AccessoryContinuityContract.sceneInstructions(listOf(actor))
        assertTrue(instruction.contains("THEIR OWN RIGHT wrist"))
        assertTrue(AccessoryContinuityContract.conflicts(listOf(actor)).isEmpty())
    }
    @Test fun conflictingOutfitCannotSilentlyOverrideLock() {
        val actor=JSONObject().put("name","นายเปีย").put("watch_side","right").put("outfit","นาฬิกาข้อมือซ้าย")
        assertEquals(1,AccessoryContinuityContract.conflicts(listOf(actor)).size)
    }
    @Test fun unknownSideIsNotGuessed() {
        val actor=JSONObject().put("name","นายเปีย").put("outfit","นาฬิกา")
        assertEquals("",AccessoryContinuityContract.sceneInstructions(listOf(actor)))
        actor.put("watch_side","viewer_right")
        assertThrows(IllegalArgumentException::class.java) { AccessoryContinuityContract.lockedSide(actor) }
    }
}
