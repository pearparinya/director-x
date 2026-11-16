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

    @Test fun finalBuildRejectsUntranslatedLocation() {
        assertTrue(runCatching {
            ShortFlowPrompt.build(scene(location = "ห้องนั่งเล่น"), "Locked camera")
        }.isFailure)
    }

    @Test fun thaiDialogueIsPreservedInGeneratedPrompt() {
        val prompt = ShortFlowPrompt.build(scene(), "Locked camera.")
        assertTrue(prompt.contains("สวัสดีครับ"))
        assertTrue(prompt.contains("<language>th-TH</language>"))
        assertFalse(prompt.contains(".. "))
    }

    @Test fun xmlEscapingPreservesOriginalThaiDialogue() {
        val original = "ช่วย A & B <ตอนนี้> \"ได้ไหม\""
        val output = ShortFlowPrompt.build(scene(dialogue = original), "Locked camera")
        val parsed = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(output)))
        assertTrue(parsed.getElementsByTagName("text").item(0).textContent == original)
    }

    @Test fun silentModeNeverEmitsSpokenDialogueOrEffects() {
        val output = ShortFlowPrompt.build(scene(), "Locked camera", silent = true)
        assertFalse(output.contains("<dialogue"))
        assertFalse(output.contains("<sound_effects>"))
    }

    @Test fun audioPreflightFlagsUnassignedSpeakerAndEmptyTurn() {
        val sample = scene().copy(
            turns = listOf(FlowDialogueTurn("", "ช่วยด้วย"), FlowDialogueTurn("สมชาย", ""))
        )
        val warnings = ShortFlowPrompt.audioWarnings(sample)
        assertTrue(warnings.any { it.contains("no speaker") })
        assertTrue(warnings.any { it.contains("no spoken text") })
    }

    @Test fun audioPreflightFlagsOffscreenNarrationAndUnvoicedShout() {
        val narrated = scene().copy(dialogue = "", narration = "คืนนี้ฝนตก")
        assertTrue(ShortFlowPrompt.audioWarnings(narrated).any { it.contains("off-screen") })
        val shouting = scene().copy(dialogue = "", action = "Character 1 shouts")
        assertTrue(ShortFlowPrompt.audioWarnings(shouting).any { it.contains("vocal performance") })
    }

    @Test fun voiceOnlyIncludesBothDialogueAndNarrationInOrder() {
        val sample = scene().copy(narration = "คืนนี้มีฝน")
        val output = ShortFlowPrompt.voiceOnly(sample)
        assertTrue(output.contains("สวัสดีครับ"))
        assertTrue(output.contains("Off-screen narrator: คืนนี้มีฝน"))
        assertTrue(output.indexOf("สวัสดีครับ") < output.indexOf("คืนนี้มีฝน"))
    }

    @Test fun voiceOnlyPreservesOrderedTurnsAndNarration() {
        val sample = scene().copy(
            dialogue = "",
            narration = "เสียงบรรยาย",
            turns = listOf(FlowDialogueTurn("สมชาย", "หนึ่ง"), FlowDialogueTurn("สมชาย", "สอง"))
        )
        val output = ShortFlowPrompt.voiceOnly(sample)
        assertTrue(output.indexOf("หนึ่ง") < output.indexOf("สอง"))
        assertTrue(output.indexOf("สอง") < output.indexOf("เสียงบรรยาย"))
    }

    @Test fun narrationOnlyHasOffscreenSpeakerAndNoVisibleDialogue() {
        val sample = scene().copy(dialogue = "", narration = "เรื่องราวเริ่มขึ้น")
        val xml = ShortFlowPrompt.build(sample, "Locked camera")
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(xml)))
        val dialogues = doc.getElementsByTagName("dialogue")
        assertTrue(dialogues.length == 1)
        assertTrue(dialogues.item(0).attributes.getNamedItem("off_screen").nodeValue == "true")
        assertTrue(doc.getElementsByTagName("speaker").item(0).textContent == "Off-screen narrator")
        assertTrue(doc.getElementsByTagName("text").item(0).textContent == "เรื่องราวเริ่มขึ้น")
    }

    @Test fun orderedDialogueAndNarrationProduceThreeExactXmlTurns() {
        val sample = scene().copy(
            dialogue = "",
            narration = "เสียงบรรยาย",
            turns = listOf(FlowDialogueTurn("สมชาย", "หนึ่ง & สอง"), FlowDialogueTurn("สมชาย", "สาม"))
        )
        val xml = ShortFlowPrompt.build(sample, "Locked camera")
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(xml)))
        val texts = doc.getElementsByTagName("text")
        assertTrue(texts.length == 3)
        assertTrue(texts.item(0).textContent == "หนึ่ง & สอง")
        assertTrue(texts.item(1).textContent == "สาม")
        assertTrue(texts.item(2).textContent == "เสียงบรรยาย")
        assertTrue(doc.getElementsByTagName("dialogue").item(2).attributes.getNamedItem("off_screen").nodeValue == "true")
    }

    @Test fun speechWarningsNeverRewriteText() {
        val original = scene(dialogue = "สวัสดีครับ".repeat(20))
        assertTrue(ShortFlowPrompt.audioWarnings(original).isNotEmpty())
        assertTrue(ShortFlowPrompt.build(original, "Locked camera.").contains(original.dialogue))
    }
}
