package com.pearparinya.automovie

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortFlowPromptPreflightTest {
    private fun scene(location: String = "Living room", dialogue: String = "สวัสดีครับ") = FlowSceneSpec(
        cast = listOf(FlowPerson("สมชาย", "adult man", "left of frame", "white shirt")),
        speaker = "สมชาย", dialogue = dialogue, narration = "",
        action = "Character 1 looks toward camera", location = location,
        startState = "Man standing", endState = "Man standing",
        duration = 8, aspect = "9:16"
    )

    @Test fun lockedAndMovingCameraAreRejected() {
        assertTrue(ShortFlowPrompt.directionIssues(scene(), "Locked tripod camera with slow pan").isNotEmpty())
        assertTrue(ShortFlowPrompt.directionIssues(scene(), "Locked camera.").isEmpty())
    }

    @Test fun multipleShotsAreRejected() {
        assertTrue(ShortFlowPrompt.directionIssues(scene(), "Montage of multiple shots").isNotEmpty())
    }

    @Test fun untranslatedLocationIsRejected() {
        assertTrue(ShortFlowPrompt.directionIssues(scene(location = "ห้องนั่งเล่น"), "Locked camera").isNotEmpty())
    }

    @Test fun thaiDialogueIsPreservedInGeneratedPrompt() {
        val prompt = ShortFlowPrompt.build(scene(), "Locked camera.")
        assertTrue(prompt.contains("สวัสดีครับ"))
        assertTrue(prompt.contains("<language>th-TH</language>"))
        assertFalse(prompt.contains(".. "))
    }

    @Test fun cameraTranslationMustBeEnglish() {
        val result = org.json.JSONObject().put("camera", "Locked camera")
        assertTrue(FlowEnglishDirections.translatedCamera(result, "กล้องนิ่ง") == "Locked camera")
    }

    @Test fun missingCameraTranslationIsRejected() {
        val result = org.json.JSONObject().put("camera", "")
        val rejected = runCatching { FlowEnglishDirections.translatedCamera(result, "กล้องนิ่ง") }.isFailure
        assertTrue(rejected)
    }

    @Test fun speechWarningsNeverRewriteText() {
        val original = scene(dialogue = "สวัสดีครับ".repeat(20))
        assertTrue(ShortFlowPrompt.audioWarnings(original).isNotEmpty())
        assertTrue(ShortFlowPrompt.build(original, "Locked camera.").contains(original.dialogue))
    }
}
