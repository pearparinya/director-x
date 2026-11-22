package com.pearparinya.automovie

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SceneLockProgressTest {
    @Test fun countsIndependentlyAndResumesAtFirstUnapprovedScene() {
        val dir = Files.createTempDirectory("scene-progress").toFile()
        try {
            val files = (1..3).associate { n ->
                val id = "EP01_SC%03d".format(n)
                id to File(dir, "$id.png").apply { writeText("frame-$n") }
            }
            val state = JSONObject()
            SceneLocks.approve(state, "EP01_SC001", SceneLocks.Medium.IMAGE, files.getValue("EP01_SC001"))
            SceneLocks.approve(state, "EP01_SC003", SceneLocks.Medium.IMAGE, files.getValue("EP01_SC003"))
            val scenes = files.keys.toList()
            val image = SceneLockProgress.summarize(state, scenes, SceneLocks.Medium.IMAGE) { files[it] }
            assertEquals(3, image.total)
            assertEquals(2, image.approved)
            assertEquals("EP01_SC002", image.nextSceneId)
            assertEquals("🔒 2/3", image.label)
            val flow = SceneLockProgress.summarize(state, scenes, SceneLocks.Medium.FLOW) { files[it] }
            assertEquals(0, flow.approved)
            assertEquals("EP01_SC001", flow.nextSceneId)
            SceneLocks.approve(state, "EP01_SC002", SceneLocks.Medium.IMAGE, files.getValue("EP01_SC002"))
            val complete = SceneLockProgress.summarize(state, scenes, SceneLocks.Medium.IMAGE) { files[it] }
            assertEquals(3, complete.approved)
            assertNull(complete.nextSceneId)
        } finally { dir.deleteRecursively() }
    }
}
