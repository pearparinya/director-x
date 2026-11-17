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

    @Test fun videoCardExposesVisualWarningWithoutChangingGeneratedPrompt() {
        val spec = scene().copy(
            action = "Character 1 pulls out the letter",
            startState = "Character 1 stands with empty hands"
        )
        val card = SceneCommandCards.videoCard(7, spec)
        assertTrue(card.title.contains("ซีน 7"))
        assertTrue(card.title.contains("⚠"))
        assertTrue(card.visualWarnings.any { it.contains("prop", ignoreCase = true) })
        assertTrue(card.command.contains("Use only the props and objects already visible"))
        assertFalse(card.command.contains("ตรวจสอบภาพเริ่มต้น"))
    }

    @Test fun videoCardWithoutVisualWarningsKeepsCleanTitleAndEmptyWarningList() {
        val card = SceneCommandCards.videoCard(2, scene())
        assertTrue(card.title.contains("ซีน 2"))
        assertFalse(card.title.contains("⚠"))
        assertTrue(card.visualWarnings.isEmpty())
        assertTrue(card.command.contains("สวัสดีครับ"))
    }

    @Test fun episodeWarningSummaryCountsOnlyFlaggedScenesAndPreservesOrder() {
        val cards = listOf(
            SceneCommandCards.Card("ซีน 1", "prompt one", listOf("Missing prop", "Camera jump")),
            SceneCommandCards.Card("ซีน 2", "prompt two"),
            SceneCommandCards.Card("ซีน 3", "prompt three", listOf("Daylight shift"))
        )
        val summary = SceneCommandCards.episodeWarningSummary(cards)
        assertTrue(summary.flaggedScenes == 2)
        assertTrue(summary.warningCount == 3)
        assertTrue(summary.details.contains("1. Missing prop"))
        assertTrue(summary.details.contains("2. Camera jump"))
        assertTrue(summary.details.contains("1. Daylight shift"))
        assertFalse(summary.details.contains("ซีน 2"))
        assertTrue(summary.details.indexOf("ซีน 1") < summary.details.indexOf("ซีน 3"))
    }

    @Test fun episodeWarningSummaryIsEmptyForCleanEpisode() {
        val summary = SceneCommandCards.episodeWarningSummary(
            listOf(SceneCommandCards.Card("ซีน 1", "prompt"))
        )
        assertTrue(summary.flaggedScenes == 0)
        assertTrue(summary.warningCount == 0)
        assertTrue(summary.details.isEmpty())
    }

    @Test fun videoEpisodeExportIncludesWarningReportBeforeUnmodifiedCommands() {
        val cards = listOf(
            SceneCommandCards.Card("ซีน 1", "<prompt>first</prompt>", listOf("Missing first-frame prop")),
            SceneCommandCards.Card("ซีน 2", "<prompt>second</prompt>")
        )
        val exported = SceneCommandCards.exportVideoEpisode(cards)
        assertTrue(exported.startsWith("=== FLOW VISUAL PREFLIGHT REPORT (NOT PART OF VIDEO PROMPTS) ===\nFLOW VISUAL PREFLIGHT:"))
        assertTrue(exported.contains("1 scene(s), 1 warning(s)"))
        assertTrue(exported.contains("Missing first-frame prop"))
        assertTrue(exported.indexOf("Missing first-frame prop") < exported.indexOf("<prompt>first</prompt>"))
        assertTrue(exported.endsWith(SceneCommandCards.join(cards)))
    }

    @Test fun cleanVideoEpisodeExportStillRequiresManualImageVerification() {
        val cards = listOf(SceneCommandCards.Card("ซีน 1", "<prompt>first</prompt>"))
        val exported = SceneCommandCards.exportVideoEpisode(cards)
        assertTrue(exported.contains("No text-based continuity warnings detected"))
        assertTrue(exported.contains("Verify every first-frame image manually"))
        assertTrue(exported.endsWith(SceneCommandCards.join(cards)))
    }

    @Test fun exportedEpisodeSeparatesPreflightMetadataFromEveryScenePrompt() {
        val cards = listOf(
            SceneCommandCards.Card("ซีน 1", "<prompt>first</prompt>", listOf("Missing prop")),
            SceneCommandCards.Card("ซีน 2", "<prompt>second</prompt>"),
            SceneCommandCards.Card("ซีน 3", "<prompt>third</prompt>", listOf("Lighting mismatch"))
        )
        val exported = SceneCommandCards.exportVideoEpisode(cards)
        assertTrue(exported.startsWith("=== FLOW VISUAL PREFLIGHT REPORT (NOT PART OF VIDEO PROMPTS) ==="))
        assertTrue(exported.contains("=== END PREFLIGHT REPORT ==="))
        assertTrue(exported.contains("=== VIDEO COMMANDS: COPY EACH SCENE BLOCK SEPARATELY ==="))
        assertTrue(exported.indexOf("=== END PREFLIGHT REPORT ===") < exported.indexOf("<prompt>first</prompt>"))
        assertTrue(exported.indexOf("<prompt>first</prompt>") < exported.indexOf("<prompt>second</prompt>"))
        assertTrue(exported.indexOf("<prompt>second</prompt>") < exported.indexOf("<prompt>third</prompt>"))
        assertTrue(exported.endsWith(SceneCommandCards.join(cards)))
    }

    @Test fun malformedScriptDialogueTurnReportsItsExactPosition() {
        val issues = ProductionCommandCheck.malformedDialogueTurnIssues(listOf(true, false, true))
        assertTrue(issues.size == 1)
        assertTrue(issues.single().contains("บทสนทนาลำดับ 2 ต้องเป็นข้อมูลผู้พูดและข้อความ"))
    }

    @Test fun validScriptDialogueTurnDoesNotTriggerMalformedEntryWarning() {
        val issues = ProductionCommandCheck.malformedDialogueTurnIssues(listOf(true, true))
        assertTrue(issues.isEmpty())
    }

    @Test fun dialogueTurnContentIdentifiesMissingSpeakerAndTextSeparately() {
        val issues = ProductionCommandCheck.dialogueTurnContentIssues(listOf(
            FlowDialogueTurn("", "สวัสดีครับ"),
            FlowDialogueTurn("สมชาย", "ไม่มี"),
            FlowDialogueTurn("สมชาย", "ข้อความจริง")
        ))
        assertTrue(issues.size == 2)
        assertTrue(issues.any { it.contains("บทสนทนาลำดับ 1 ขาดชื่อผู้พูด") })
        assertTrue(issues.any { it.contains("บทสนทนาลำดับ 2 ขาดข้อความ") })
    }

    @Test fun completeDialogueTurnsPreserveValidThaiSpeech() {
        val issues = ProductionCommandCheck.dialogueTurnContentIssues(listOf(
            FlowDialogueTurn("สมชาย", "สวัสดีครับ"),
            FlowDialogueTurn("สมหญิง", "สวัสดีค่ะ")
        ))
        assertTrue(issues.isEmpty())
    }

    @Test fun repeatedAdjacentDialogueIsFlaggedWithoutChangingWords() {
        val turns = listOf(
            FlowDialogueTurn("สมชาย", "สวัสดีครับ"),
            FlowDialogueTurn("สมชาย", "สวัสดีครับ"),
            FlowDialogueTurn("สมหญิง", "สวัสดีค่ะ")
        )
        val issues = ProductionCommandCheck.repeatedDialogueTurnIssues(turns)
        assertTrue(issues.size == 1)
        assertTrue(issues.single().contains("บทสนทนาลำดับ 2 ซ้ำกับลำดับ 1"))
        assertTrue(turns[1].text == "สวัสดีครับ")
    }

    @Test fun deliberateSameSpeakerWithDifferentLinesIsAllowed() {
        val turns = listOf(
            FlowDialogueTurn("สมชาย", "ประโยคแรก"),
            FlowDialogueTurn("สมชาย", "ประโยคถัดไป"),
            FlowDialogueTurn("สมหญิง", "ประโยคถัดไป")
        )
        assertTrue(ProductionCommandCheck.repeatedDialogueTurnIssues(turns).isEmpty())
    }

    @Test fun speakerCannotBeVisibleAndOffscreenInSameTurn() {
        val issues = ProductionCommandCheck.dialogueSpeakerPlacementIssues(
            listOf(FlowDialogueTurn("สมชาย", "สวัสดีครับ")),
            listOf("สมชาย"), listOf("สมชาย")
        )
        assertTrue(issues.single().contains("อยู่ทั้งในภาพและนอกภาพ"))
    }

    @Test fun missingDialogueSpeakerIsReportedWithTurnNumber() {
        val issues = ProductionCommandCheck.dialogueSpeakerPlacementIssues(
            listOf(FlowDialogueTurn("สมชาย", "สวัสดี"), FlowDialogueTurn("สมหญิง", "ตอบกลับ")),
            listOf("สมชาย"), emptyList()
        )
        assertTrue(issues.size == 1)
        assertTrue(issues.single().contains("บทสนทนาลำดับ 2 ไม่พบผู้พูด สมหญิง"))
    }

    @Test fun validOffscreenDialogueSpeakerIsAllowed() {
        val issues = ProductionCommandCheck.dialogueSpeakerPlacementIssues(
            listOf(FlowDialogueTurn("สมหญิง", "ได้ยินเสียงจากนอกภาพ")),
            listOf("สมชาย"), listOf("สมหญิง")
        )
        assertTrue(issues.isEmpty())
    }

    @Test fun continuityGroupLocationChangeRequiresReview() {
        val warning = ContinuityReview.continuityGroupLocationIssue(
            "DAY1_HOME", "Living room", "Hospital corridor"
        )
        assertTrue(warning?.contains("สถานที่ต่างกัน") == true)
        assertTrue(warning?.contains("DAY1_HOME") == true)
    }

    @Test fun continuityGroupLocationMatchingIgnoresCaseAndWhitespace() {
        assertTrue(ContinuityReview.continuityGroupLocationIssue(
            "DAY1_HOME", " Living Room ", "living room"
        ) == null)
        assertTrue(ContinuityReview.continuityGroupLocationIssue(
            "", "Living room", "Hospital"
        ) == null)
    }

    @Test fun continuityGroupDayToNightShiftIsFlagged() {
        val issue = ContinuityReview.continuityGroupTimeIssue(
            "DAY1_HOME", "กลางวัน", "กลางคืน"
        )
        assertTrue(issue?.contains("เปลี่ยนช่วงเวลากลางวัน/กลางคืน") == true)
        assertTrue(issue?.contains("DAY1_HOME") == true)
    }

    @Test fun continuityGroupMatchingOrUnknownTimeDoesNotRaiseFalseAlarm() {
        assertTrue(ContinuityReview.continuityGroupTimeIssue(
            "DAY1_HOME", "daytime", "กลางวัน"
        ) == null)
        assertTrue(ContinuityReview.continuityGroupTimeIssue(
            "DAY1_HOME", "morning", "evening"
        ) == null)
        assertTrue(ContinuityReview.continuityGroupTimeIssue(
            "", "daytime", "nighttime"
        ) == null)
    }

    @Test fun adjacentSceneStateMismatchPromptsHumanReview() {
        val issue = ContinuityReview.adjacentSceneStateIssue(
            "HOME_DAY1", "Man holds a letter", "Man stands with empty hands"
        )
        assertTrue(issue?.contains("สถานะจบฉากก่อนหน้าไม่ตรง") == true)
    }

    @Test fun matchingAdjacentSceneStatesDoNotTriggerWarning() {
        assertTrue(ContinuityReview.adjacentSceneStateIssue(
            "HOME_DAY1", "Man holds a letter", "  man   holds a letter "
        ) == null)
        assertTrue(ContinuityReview.adjacentSceneStateIssue(
            "", "Man holds a letter", "Man stands"
        ) == null)
    }

    @Test fun documentedTransitionAllowsIntentionalStateChange() {
        val issue = ContinuityReview.adjacentSceneStateIssue(
            "HOME_DAY1", "Man holds a letter", "Man has empty hands",
            "He places the letter on the table between scenes"
        )
        assertTrue(issue == null)
    }

    @Test fun blankTransitionDoesNotHideStateMismatch() {
        val issue = ContinuityReview.adjacentSceneStateIssue(
            "HOME_DAY1", "Man holds a letter", "Man has empty hands", "   "
        )
        assertTrue(issue?.contains("สถานะจบฉากก่อนหน้าไม่ตรง") == true)
    }

    @Test fun placeholderOrShortTransitionDoesNotSuppressWarning() {
        for(note in listOf("TODO", "ไม่มี", "ok", "123456789012", "aaaaaaaaaaaa", "รอแก้ไข")) {
            assertTrue(!ContinuityReview.meaningfulTransitionNote(note))
            val issue = ContinuityReview.adjacentSceneStateIssue(
                "HOME_DAY1", "Man holds a letter", "Man has empty hands", note
            )
            assertTrue(issue?.contains("สถานะจบฉากก่อนหน้าไม่ตรง") == true)
        }
    }

    @Test fun descriptiveThaiTransitionCanExplainStateChange() {
        val note = "ตัวละครวางจดหมายไว้บนโต๊ะก่อนเดินออกจากห้อง"
        assertTrue(ContinuityReview.meaningfulTransitionNote(note))
        assertTrue(ContinuityReview.adjacentSceneStateIssue(
            "HOME_DAY1", "ถือจดหมาย", "มือว่าง", note
        ) == null)
    }

    @Test fun unexplainedPropStateChangeRequiresReview() {
        val issue = ContinuityReview.propContinuityIssue(
            "จดหมาย", "อยู่ในมือ", "อยู่บนโต๊ะ", ""
        )
        assertTrue(issue?.contains("สิ่งของ จดหมาย เปลี่ยนสถานะ") == true)
    }

    @Test fun documentedOrUnchangedPropStateDoesNotWarn() {
        assertTrue(ContinuityReview.propContinuityIssue(
            "โทรศัพท์", "อยู่บนโต๊ะ", " อยู่บนโต๊ะ ", ""
        ) == null)
        assertTrue(ContinuityReview.propContinuityIssue(
            "จดหมาย", "อยู่ในมือ", "อยู่บนโต๊ะ",
            "ตัวละครวางจดหมายลงบนโต๊ะก่อนออกจากห้อง"
        ) == null)
    }

    @Test fun incompleteAndDuplicatePropsAreReportedByPosition() {
        val issues = ContinuityReview.propEntryIssues(listOf(
            "" to "อยู่บนโต๊ะ",
            "จดหมาย" to "",
            "จดหมาย" to "อยู่ในมือ"
        ))
        assertTrue(issues.size == 3)
        assertTrue(issues.any { it.contains("ลำดับ 1 ขาดชื่อ") })
        assertTrue(issues.any { it.contains("ลำดับ 2 ขาดสถานะ") })
        assertTrue(issues.any { it.contains("ลำดับ 3 ชื่อ จดหมาย ซ้ำ") })
    }

    @Test fun distinctCompletePropsAreAllowed() {
        assertTrue(ContinuityReview.propEntryIssues(listOf(
            "โทรศัพท์" to "อยู่บนโต๊ะ",
            "จดหมาย" to "อยู่ในมือ"
        )).isEmpty())
    }

    @Test fun omittedPropBetweenTrackedScenesIsFlagged() {
        val issues = ContinuityReview.missingPropIssues(
            setOf("จดหมาย", "โทรศัพท์"), setOf("โทรศัพท์"), emptySet()
        )
        assertTrue(issues.size == 1)
        assertTrue(issues.single().contains("จดหมาย"))
    }

    @Test fun explicitlyRemovedPropIsNotFlaggedAsMissing() {
        assertTrue(ContinuityReview.missingPropIssues(
            setOf("จดหมาย"), emptySet(), setOf(" จดหมาย ")
        ).isEmpty())
        assertTrue(ContinuityReview.missingPropIssues(
            setOf("Phone"), setOf(" phone "), emptySet()
        ).isEmpty())
    }

    @Test fun removedPropsMustExistPreviouslyAndNotCurrently() {
        val issues = ContinuityReview.removedPropIssues(
            setOf("จดหมาย"), setOf("โทรศัพท์"),
            listOf("จดหมาย", "จดหมาย", "กระเป๋า", "")
        )
        assertTrue(issues.size == 3)
        assertTrue(issues.any { it.contains("ระบุสิ่งของ จดหมาย ซ้ำ") })
        assertTrue(issues.any { it.contains("สิ่งของ กระเป๋า ไม่อยู่ในฉากก่อนหน้า") })
        assertTrue(issues.any { it.contains("ลำดับ 4 ขาดชื่อสิ่งของ") })
    }

    @Test fun validRemovedPropPassesAndPresentPropIsFlagged() {
        assertTrue(ContinuityReview.removedPropIssues(
            setOf("จดหมาย"), emptySet(), listOf("จดหมาย")
        ).isEmpty())
        val issues = ContinuityReview.removedPropIssues(
            setOf("จดหมาย"), setOf("จดหมาย"), listOf("จดหมาย")
        )
        assertTrue(issues.single().contains("ยังอยู่ในฉากปัจจุบัน"))
    }

    @Test fun returnedPropsRequirePriorRemovalAndCurrentPresence() {
        val issues = ContinuityReview.returnedPropIssues(
            setOf("จดหมาย"), setOf("โทรศัพท์"), listOf("จดหมาย", "กระเป๋า")
        )
        assertTrue(issues.size == 2)
        assertTrue(issues.any { it.contains("จดหมาย ไม่ปรากฏใน props") })
        assertTrue(issues.any { it.contains("กระเป๋า ไม่เคยถูกนำออก") })
    }

    @Test fun validReturnedPropsPassAndDuplicatesWarn() {
        assertTrue(ContinuityReview.returnedPropIssues(
            setOf("จดหมาย"), setOf("จดหมาย"), listOf("จดหมาย")
        ).isEmpty())
        val issues = ContinuityReview.returnedPropIssues(
            setOf("จดหมาย"), setOf("จดหมาย"), listOf("จดหมาย", " จดหมาย ", "")
        )
        assertTrue(issues.size == 2)
        assertTrue(issues.any { it.contains("ซ้ำ") })
        assertTrue(issues.any { it.contains("ขาดชื่อสิ่งของ") })
    }

    @Test fun undocumentedReturnOfRemovedPropWarns() {
        val issues = ContinuityReview.undocumentedPropReturns(
            setOf("จดหมาย"), setOf("จดหมาย", "โทรศัพท์"), emptyList()
        )
        assertTrue(issues.size == 1)
        assertTrue(issues.single().contains("จดหมาย กลับมาปรากฏ"))
    }

    @Test fun documentedReturnDoesNotWarnAndRepeatedCyclesCanBeChecked() {
        val removed = setOf("จดหมาย")
        assertTrue(ContinuityReview.undocumentedPropReturns(
            removed, setOf("จดหมาย"), listOf("จดหมาย")
        ).isEmpty())
        assertTrue(ContinuityReview.undocumentedPropReturns(
            removed, emptySet(), emptyList()
        ).isEmpty())
        assertTrue(ContinuityReview.undocumentedPropReturns(
            removed, setOf("จดหมาย"), emptyList()
        ).isNotEmpty())
    }

    @Test fun continuityReportPrioritizesMissingPropData() {
        val result = ContinuityReview.formatFinding("EP1 ซีน 2: สิ่งของลำดับ 1 ขาดชื่อ")
        assertTrue(result.startsWith("[ควรแก้ไข] EP1 ซีน 2:"))
        assertTrue(result.contains("แนวทาง: ตรวจ props"))
    }

    @Test fun continuityReportDistinguishesWarningsAndManualChecks() {
        val warning = ContinuityReview.formatFinding("EP1 ซีน 3: สิ่งของ จดหมาย กลับมาปรากฏในฉากโดยไม่ระบุ returned_props")
        assertTrue(warning.startsWith("[ควรตรวจสอบ]"))
        assertTrue(warning.contains("แนวทาง: ตรวจประวัติการนำสิ่งของออก"))
        val manual = ContinuityReview.formatFinding("ตรวจด้วยตนเอง: ตรวจความหมายและภาพจริง")
        assertTrue(manual.startsWith("[ข้อมูลเพิ่มเติม]"))
    }

    @Test fun malformedSceneArrayFieldsAreReportedWithoutDroppingValidFields() {
        val issues = ContinuityReview.malformedSceneFields(mapOf(
            "props" to "invalid", "characters" to "array",
            "removed_props" to "missing", "returned_props" to "invalid"
        ))
        assertTrue(issues.size == 2)
        assertTrue(issues.any { it.contains("props ต้องเป็นรายการ array") })
        assertTrue(issues.any { it.contains("returned_props ต้องเป็นรายการ array") })
    }

    @Test fun missingOptionalSceneArraysDoNotCreateMalformedWarnings() {
        assertTrue(ContinuityReview.malformedSceneFields(mapOf(
            "props" to "missing", "characters" to "array",
            "removed_props" to "missing", "returned_props" to "array"
        )).isEmpty())
    }

    @Test fun propLifecycleAcrossEpisodesSupportsRepeatedRemovalAndReturn() {
        val history = mutableSetOf<String>()
        // EP1: letter exists, then is removed.
        ContinuityReview.advanceRemovedPropHistory(
            history, setOf("จดหมาย"), emptySet(), listOf("จดหมาย"), emptyList()
        )
        assertTrue("จดหมาย" in history)
        // EP2: letter reappears with an explicit return.
        assertTrue(ContinuityReview.undocumentedPropReturns(
            history, setOf("จดหมาย"), listOf("จดหมาย")
        ).isEmpty())
        ContinuityReview.advanceRemovedPropHistory(
            history, emptySet(), setOf("จดหมาย"), emptyList(), listOf("จดหมาย")
        )
        assertTrue(history.isEmpty())
        // EP3: same prop may be removed again.
        ContinuityReview.advanceRemovedPropHistory(
            history, setOf("จดหมาย"), emptySet(), listOf("จดหมาย"), emptyList()
        )
        assertTrue("จดหมาย" in history)
        assertTrue(ContinuityReview.undocumentedPropReturns(
            history, setOf("จดหมาย"), emptyList()
        ).isNotEmpty())
    }

    @Test fun malformedOrUnjustifiedPropEventsDoNotCorruptHistory() {
        val history = mutableSetOf<String>()
        ContinuityReview.advanceRemovedPropHistory(
            history, setOf("โทรศัพท์"), setOf("โทรศัพท์"),
            listOf("โทรศัพท์", "กระเป๋า"), listOf("กระเป๋า")
        )
        assertTrue(history.isEmpty())
        ContinuityReview.advanceRemovedPropHistory(
            history, null, emptySet(), listOf("โทรศัพท์"), emptyList()
        )
        assertTrue(history.isEmpty())
    }

    @Test fun propEventsWithoutPropsCannotBeSilentlyValidated() {
        val issues = ContinuityReview.propEventStructureIssues(
            false, listOf("string"), null
        )
        assertTrue(issues.single().contains("ไม่มี props"))
    }

    @Test fun malformedPropEventElementsAreIdentifiedByFieldAndPosition() {
        val issues = ContinuityReview.propEventStructureIssues(
            true, listOf("string", "invalid"), listOf("invalid", "string")
        )
        assertTrue(issues.size == 2)
        assertTrue(issues.any { it.contains("removed_props ลำดับ 2") })
        assertTrue(issues.any { it.contains("returned_props ลำดับ 1") })
        assertTrue(ContinuityReview.propEventStructureIssues(
            true, emptyList(), listOf("string")
        ).isEmpty())
    }

    @Test fun contradictoryPropEventsAreReportedOncePerProp() {
        val issues = ContinuityReview.conflictingPropEvents(
            listOf("จดหมาย", "โทรศัพท์"), listOf(" จดหมาย ", "จดหมาย", "กระเป๋า")
        )
        assertTrue(issues.size == 1)
        assertTrue(issues.single().contains("จดหมาย"))
        assertTrue(issues.single().contains("removed_props"))
        assertTrue(issues.single().contains("returned_props"))
    }

    @Test fun independentPropEventsDoNotConflict() {
        assertTrue(ContinuityReview.conflictingPropEvents(
            listOf("จดหมาย"), listOf("โทรศัพท์")
        ).isEmpty())
        assertTrue(ContinuityReview.conflictingPropEvents(
            listOf(""), listOf(" ")
        ).isEmpty())
    }

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

    @Test fun contradictorySameScenePropEventsDoNotErasePriorRemovalHistory() {
        val history = mutableSetOf("จดหมาย")
        ContinuityReview.advanceRemovedPropHistory(
            history, emptySet(), setOf("จดหมาย"),
            listOf("จดหมาย"), listOf("จดหมาย")
        )
        assertTrue("จดหมาย" in history)
        assertTrue(ContinuityReview.undocumentedPropReturns(
            history, setOf("จดหมาย"), emptyList()
        ).isNotEmpty())
    }

    @Test fun incompleteSceneEndClearsPriorGroupState() {
        val history = mutableMapOf<String, String>()
        ContinuityReview.advanceEndState(history, "HOME", "ถือจดหมาย")
        assertTrue(history["HOME"] == "ถือจดหมาย")
        ContinuityReview.advanceEndState(history, "HOME", " ")
        assertFalse(history.containsKey("HOME"))
    }

    @Test fun missingEndStateOnlyClearsItsOwnContinuityGroup() {
        val history = mutableMapOf("HOME" to "ถือจดหมาย", "OFFICE" to "นั่งโต๊ะ")
        ContinuityReview.advanceEndState(history, "HOME", "")
        assertTrue(history["OFFICE"] == "นั่งโต๊ะ")
        assertFalse(history.containsKey("HOME"))
        ContinuityReview.advanceEndState(history, "", "ignored")
        assertFalse(history.containsKey(""))
    }
}
