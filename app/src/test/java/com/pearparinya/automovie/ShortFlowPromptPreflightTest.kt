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
        val sample = scene().copy(speaker = "", dialogue = "", narration = "เรื่องราวเริ่มขึ้น")
        val xml = ShortFlowPrompt.build(sample, "Locked camera")
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(xml)))
        val dialogues = doc.getElementsByTagName("dialogue")
        assertTrue(dialogues.length == 1)
        assertTrue(dialogues.item(0).attributes.getNamedItem("off_screen").nodeValue == "true")
        assertTrue(doc.getElementsByTagName("speaker").item(0).textContent == "Off-screen narrator")
        assertTrue(doc.getElementsByTagName("text").item(0).textContent == "เรื่องราวเริ่มขึ้น")
    }

    @Test fun orderedDialogueAndNarrationAreRejectedToAvoidOverlappingVoices() {
        val sample = scene().copy(
            speaker = "",
            dialogue = "",
            narration = "เสียงบรรยาย",
            turns = listOf(FlowDialogueTurn("สมชาย", "หนึ่ง & สอง"), FlowDialogueTurn("สมชาย", "สาม"))
        )
        assertTrue(FlowScenePrompt.issues(sample).any { it.contains("บทสนทนาหลายคน") })
        assertTrue(runCatching { ShortFlowPrompt.build(sample, "Locked camera") }.isFailure)
    }

    @Test fun validOrderedTurnsPreserveSpeakerAndExactThaiXml() {
        val sample = scene().copy(
            speaker = "", dialogue = "", narration = "",
            turns = listOf(FlowDialogueTurn("สมชาย", "หนึ่ง & สอง"), FlowDialogueTurn("สมชาย", "สาม <สี่>"))
        )
        assertTrue(FlowScenePrompt.issues(sample).isEmpty())
        val xml = ShortFlowPrompt.build(sample, "Locked camera")
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(xml)))
        val texts = doc.getElementsByTagName("text")
        assertTrue(texts.length == 2)
        assertTrue(texts.item(0).textContent == "หนึ่ง & สอง")
        assertTrue(texts.item(1).textContent == "สาม <สี่>")
        assertTrue(doc.getElementsByTagName("speaker").item(0).textContent == "The Man 1")
        assertTrue(doc.getElementsByTagName("speaker").item(1).textContent == "The Man 1")
    }

    @Test fun offscreenSpeakerDoesNotAnimateVisibleCharacter() {
        val sample = scene().copy(
            speaker = "ผู้บรรยาย", dialogue = "ได้ยินไหม", narration = "",
            offscreen = listOf("ผู้บรรยาย")
        )
        assertTrue(FlowScenePrompt.issues(sample).isEmpty())
        val xml = ShortFlowPrompt.build(sample, "Locked camera")
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(xml)))
        assertTrue(doc.getElementsByTagName("dialogue").item(0).attributes.getNamedItem("off_screen").nodeValue == "true")
        assertTrue(doc.getElementsByTagName("prompt").item(0).textContent.contains("visible characters stay silent"))
        assertTrue(doc.getElementsByTagName("text").item(0).textContent == "ได้ยินไหม")
    }

    @Test fun visualPreflightFlagsPropAppearingMidShot() {
        val sample = scene().copy(action = "Character 1 suddenly appears with a letter")
        assertTrue(ShortFlowPrompt.visualWarnings(sample, "Locked camera").any { it.contains("prop") })
    }

    @Test fun visualPreflightFlagsThaiCutsWithoutChangingDialogue() {
        val original = "สวัสดีครับ"
        for (action in listOf("ตัดภาพไปยังประตู", "ตัดฉากไปอีกห้อง", "เปลี่ยนมุมกล้องไปด้านหลัง")) {
            val sample = scene(dialogue = original).copy(action = action)
            assertTrue(ShortFlowPrompt.visualWarnings(sample, "Locked camera").any { it.contains("continuous shot") })
            assertTrue(sample.dialogue == original)
        }
    }

    @Test fun visualPreflightFlagsConjuredAndProducedProps() {
        for (action in listOf("Character 1 materializes a key", "Character 1 produces a letter", "Character 1 conjures a book", "Character 1 takes out an envelope", "Character 1 pulls out an object")) {
            assertTrue(ShortFlowPrompt.visualWarnings(scene().copy(action = action), "Locked camera").any { it.contains("prop") })
        }
    }

    @Test fun visualPreflightDoesNotFlagOrdinaryExistingPropHandling() {
        val sample = scene().copy(action = "Character 1 picks up the letter already visible on the desk")
        assertFalse(ShortFlowPrompt.visualWarnings(sample, "Locked camera").any { it.contains("prop") })
    }

    @Test fun visualPreflightRespectsFirstFramePropEvidence() {
        val sample = scene().copy(action = "Character 1 takes out a letter", startState = "Letter already visible in the first frame")
        assertFalse(ShortFlowPrompt.visualWarnings(sample, "Locked camera").any { it.contains("prop") })
    }

    @Test fun visualPreflightDoesNotConfuseDifferentVisibleProps() {
        val sample = scene().copy(action = "Character 1 takes out a letter", startState = "A key is already visible in the first frame")
        assertTrue(ShortFlowPrompt.visualWarnings(sample, "Locked camera").any { it.contains("prop") })
    }

    @Test fun visualPreflightDoesNotMistakeOtherVisiblePropForRequestedObject() {
        val sample = scene().copy(
            action = "Character 1 takes out a letter",
            startState = "A key is already visible in the first frame; a letter is hidden offscreen"
        )
        assertTrue(ShortFlowPrompt.visualWarnings(sample, "Locked camera").any { it.contains("prop") })
    }

    @Test fun visualPreflightSeparatesContrastingPropClauses() {
        val sample = scene().copy(
            action = "Character 1 takes out a letter",
            startState = "A key is already visible in the first frame but a letter is hidden offscreen"
        )
        assertTrue(ShortFlowPrompt.visualWarnings(sample, "Locked camera").any { it.contains("prop") })
    }

    @Test fun visualPreflightKeepsConfirmedObjectVisibleDespiteUnrelatedHiddenObject() {
        val sample = scene().copy(
            action = "Character 1 takes out a letter",
            startState = "A letter is already visible in the first frame while a key is hidden offscreen"
        )
        assertFalse(ShortFlowPrompt.visualWarnings(sample, "Locked camera").any { it.contains("prop") })
    }

    @Test fun visualPreflightSeparatesAndJoinedProps() {
        val sample = scene().copy(
            action = "Character 1 takes out a letter",
            startState = "A key is already visible in the first frame and a letter is hidden offscreen"
        )
        assertTrue(ShortFlowPrompt.visualWarnings(sample, "Locked camera").any { it.contains("prop") })
    }

    @Test fun visualPreflightSeparatesCommaJoinedProps() {
        val sample = scene().copy(
            action = "Character 1 takes out a letter",
            startState = "A key is already visible in the first frame, a letter is hidden offscreen"
        )
        assertTrue(ShortFlowPrompt.visualWarnings(sample, "Locked camera").any { it.contains("prop") })
    }

    @Test fun visualPreflightFlagsConcealedProps() {
        for (state in listOf(
            "A letter is already present but covered",
            "A letter is already visible behind the wall",
            "A letter is already present inside a drawer",
            "A letter is already visible but obscured"
        )) {
            val sample = scene().copy(action = "Character 1 takes out a letter", startState = state)
            assertTrue(ShortFlowPrompt.visualWarnings(sample, "Locked camera").any { it.contains("prop") })
        }
    }

    @Test fun visualPreflightAllowsVisiblePropWithUnrelatedConcealedProp() {
        for (state in listOf(
            "A letter is already visible in the first frame while a key is covered",
            "A letter is already visible in the first frame and a key is hidden offscreen",
            "A letter is already visible in the first frame, a key is inside a drawer"
        )) {
            val sample = scene().copy(action = "Character 1 takes out a letter", startState = state)
            assertFalse(ShortFlowPrompt.visualWarnings(sample, "Locked camera").any { it.contains("prop") })
        }
    }

    @Test fun visualPreflightChecksDefiniteArticleProps() {
        for (action in listOf(
            "Character 1 pulls out the letter",
            "Character 1 takes out the letter",
            "Character 1 produces the letter",
            "Character 1 conjures the letter"
        )) {
            val missing = scene().copy(action = action, startState = "A key is already visible in the first frame")
            assertTrue(ShortFlowPrompt.visualWarnings(missing, "Locked camera").any { it.contains("prop") })
            val present = scene().copy(action = action, startState = "The letter is already visible in the first frame")
            assertFalse(ShortFlowPrompt.visualWarnings(present, "Locked camera").any { it.contains("prop") })
        }
    }

    @Test fun visualPreflightDoesNotTreatInvisibleObjectAsPresent() {
        val sample = scene().copy(
            action = "Character 1 takes out a letter",
            startState = "A letter is not visible in the first frame"
        )
        assertTrue(ShortFlowPrompt.visualWarnings(sample, "Locked camera").any { it.contains("prop") })
    }

    @Test fun visualPreflightStillWarnsWhenFirstFrameEvidenceIsMissing() {
        val sample = scene().copy(action = "Character 1 takes out a letter", startState = "Empty desk")
        assertTrue(ShortFlowPrompt.visualWarnings(sample, "Locked camera").any { it.contains("prop") })
    }

    @Test fun visualPreflightFlagsTimeJumpAndSceneCut() {
        val sample = scene().copy(action = "Cut to a new angle", startState = "daylight", endState = "nighttime")
        val warnings = ShortFlowPrompt.visualWarnings(sample, "Locked camera")
        assertTrue(warnings.any { it.contains("continuous shot") })
        assertTrue(warnings.any { it.contains("day to night") })
    }

    @Test fun visualPreflightFlagsNightToDayInThai() {
        val sample = scene().copy(startState = "กลางคืน", endState = "กลางวัน")
        assertTrue(ShortFlowPrompt.visualWarnings(sample, "Locked camera").any { it.contains("night to day") })
    }

    @Test fun visualPreflightDoesNotAlterValidScene() {
        val sample = scene()
        assertTrue(ShortFlowPrompt.visualWarnings(sample, "Locked camera").isEmpty())
        assertTrue(ShortFlowPrompt.build(sample, "Locked camera").contains(sample.dialogue))
    }

    @Test fun generatedPromptSurfacesVisualWarningsWithoutChangingAuthoredAction() {
        val sample = scene().copy(action = "Character 1 suddenly appears with a letter")
        val output = ShortFlowPrompt.build(sample, "Locked camera")
        assertTrue(output.contains("Visual continuity preflight:"))
        assertTrue(output.contains("verify prop continuity"))
        assertTrue(output.contains("suddenly appears with a letter"))
    }

    @Test fun cleanPromptHasNoVisualWarningBanner() {
        val output = ShortFlowPrompt.build(scene(), "Locked camera")
        assertFalse(output.contains("Visual continuity preflight:"))
    }

    @Test fun speechWarningsNeverRewriteText() {
        val original = scene(dialogue = "สวัสดีครับ".repeat(20))
        assertTrue(ShortFlowPrompt.audioWarnings(original).isNotEmpty())
        assertTrue(ShortFlowPrompt.build(original, "Locked camera.").contains(original.dialogue))
    }
}
