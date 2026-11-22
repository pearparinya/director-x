package com.pearparinya.automovie

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class SceneLocksTest {
    @Test fun imageAndFlowAreIndependentAndPersist() {
        val workspace = Files.createTempDirectory("scene-locks").toFile()
        try {
            val image = java.io.File(workspace, "frame.png").apply { writeText("frame-one") }
            val flow = java.io.File(workspace, "flow.txt").apply { writeText("flow-one") }
            val state = JSONObject()
            SceneLocks.approve(state, "EP01_SC001", SceneLocks.Medium.IMAGE, image)
            assertTrue(SceneLocks.isLocked(state, "EP01_SC001", SceneLocks.Medium.IMAGE, image))
            assertFalse(SceneLocks.isLocked(state, "EP01_SC001", SceneLocks.Medium.FLOW, flow))
            SceneLocks.requireUnlocked(state, "EP01_SC001", SceneLocks.Medium.FLOW, flow)
            SceneLocks.approve(state, "EP01_SC001", SceneLocks.Medium.FLOW, flow)
            assertThrows(IllegalStateException::class.java) {
                SceneLocks.requireUnlocked(state, "EP01_SC001", SceneLocks.Medium.IMAGE, image)
            }
            assertThrows(IllegalStateException::class.java) {
                SceneLocks.requireUnlocked(state, "EP01_SC001", SceneLocks.Medium.FLOW, flow)
            }
            SceneLocks.save(workspace, state)
            val restored = SceneLocks.load(workspace)
            assertTrue(SceneLocks.isLocked(restored, "EP01_SC001", SceneLocks.Medium.IMAGE, image))
            assertTrue(SceneLocks.isLocked(restored, "EP01_SC001", SceneLocks.Medium.FLOW, flow))
            flow.writeText("flow-changed")
            assertFalse(SceneLocks.isLocked(restored, "EP01_SC001", SceneLocks.Medium.FLOW, flow))
            assertTrue(SceneLocks.isLocked(restored, "EP01_SC001", SceneLocks.Medium.IMAGE, image))
            SceneLocks.unlock(restored, "EP01_SC001", SceneLocks.Medium.IMAGE)
            assertFalse(SceneLocks.isLocked(restored, "EP01_SC001", SceneLocks.Medium.IMAGE, image))
        } finally { workspace.deleteRecursively() }
    }
    @Test fun invalidSceneAndMissingFileCannotBeApproved() {
        val workspace = Files.createTempDirectory("scene-locks").toFile()
        try {
            val image = java.io.File(workspace, "frame.png").apply { writeText("data") }
            assertThrows(IllegalArgumentException::class.java) {
                SceneLocks.approve(JSONObject(), "../bad", SceneLocks.Medium.IMAGE, image)
            }
            assertThrows(IllegalArgumentException::class.java) {
                SceneLocks.approve(JSONObject(), "EP01_SC001", SceneLocks.Medium.IMAGE, java.io.File(workspace, "missing"))
            }
        } finally { workspace.deleteRecursively() }
    }
}
