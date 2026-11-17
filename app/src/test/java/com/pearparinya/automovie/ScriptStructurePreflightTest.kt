package com.pearparinya.automovie

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptStructurePreflightTest {
    private fun validScene() = ScriptStructurePreflight.Scene(
        number = 1, day = "DAY01", location = "Living room",
        time = "day", startState = "Standing", endState = "Sitting",
        durationSeconds = 8, characterNames = listOf("นาย เปีย"),
        dialogueSpeakers = listOf(" นาย   เปีย ")
    )

    @Test fun validSceneDoesNotChangeThaiNamesOrRaiseIssues() {
        assertTrue(ScriptStructurePreflight.issues(
            listOf(ScriptStructurePreflight.Episode(1, listOf(validScene())))
        ).isEmpty())
    }

    @Test fun duplicateEpisodeAndSceneNumbersAreFlagged() {
        val episode = ScriptStructurePreflight.Episode(1, listOf(validScene(), validScene()))
        val issues = ScriptStructurePreflight.issues(listOf(episode, episode))
        assertTrue(issues.any { it.contains("หมายเลข EP ซ้ำ") })
        assertTrue(issues.any { it.contains("หมายเลขฉากซ้ำ") })
    }

    @Test fun missingSceneMetadataAndIncorrectDurationAreFlagged() {
        val scene = validScene().copy(day = "", location = "", time = "",
            startState = "", endState = "", durationSeconds = 12)
        val issues = ScriptStructurePreflight.issues(
            listOf(ScriptStructurePreflight.Episode(1, listOf(scene))))
        listOf("story_day", "location", "story_time", "start_state",
            "end_state", "8 วินาที").forEach { expected ->
            assertTrue(issues.any { it.contains(expected) })
        }
    }

    @Test fun offscreenSpeakerIsAllowedButUnknownSpeakerIsFlagged() {
        val scene = validScene().copy(
            dialogueSpeakers = listOf("ณัฐชา", "บุคคลที่ไม่รู้จัก"),
            offscreenNames = listOf("ณัฐชา"))
        val issues = ScriptStructurePreflight.issues(
            listOf(ScriptStructurePreflight.Episode(1, listOf(scene))))
        assertTrue(issues.size == 1)
        assertTrue(issues.single().contains("บุคคลที่ไม่รู้จัก"))
    }

    @Test fun duplicateCastAndVisibleOffscreenConflictAreFlagged() {
        val scene = validScene().copy(characterNames = listOf("นาย เปีย", " นาย   เปีย "),
            offscreenNames = listOf("นาย เปีย"))
        val issues = ScriptStructurePreflight.issues(
            listOf(ScriptStructurePreflight.Episode(1, listOf(scene))))
        assertTrue(issues.any { it.contains("ชื่อตัวละครซ้ำ") })
        assertTrue(issues.any { it.contains("ทั้งในภาพและนอกภาพ") })
    }

    @Test fun emptyEpisodesAndEmptyScenesAreFlagged() {
        assertTrue(ScriptStructurePreflight.issues(emptyList()).any { it.contains("ไม่มีข้อมูล EP") })
        assertTrue(ScriptStructurePreflight.issues(
            listOf(ScriptStructurePreflight.Episode(1, emptyList()))
        ).any { it.contains("ไม่มีฉาก") })
    }
    @Test fun jsonAdapterReadsExistingStorySchemaWithoutChangingThaiDialogue() {
        val story = JSONObject("""{"episodes":[{"ep":1,"scenes":[{
            "scene":1,"story_day":"DAY01","story_time":"เช้า",
            "location":"บ้าน","start_state":"ยืน","end_state":"นั่ง",
            "duration_seconds":8,"characters":[{"name":"นายเปีย"}],
            "speaker":"นายเปีย","dialogue":"สวัสดีครับ"
        }]}]}""")
        val original = story.toString()
        assertTrue(ScriptStructurePreflight.storyIssues(story).isEmpty())
        assertTrue(story.toString() == original)
        assertTrue(story.getJSONArray("episodes").getJSONObject(0)
            .getJSONArray("scenes").getJSONObject(0).getString("dialogue") == "สวัสดีครับ")
    }

    @Test fun malformedEpisodeAndSceneJsonAreReportedInsteadOfCrashing() {
        val story = JSONObject("""{"episodes":[null,{"ep":2,"scenes":[null]}]}""")
        val issues = ScriptStructurePreflight.storyIssues(story)
        assertTrue(issues.any { it.contains("EP ลำดับ 1") })
        assertTrue(issues.any { it.contains("SC ลำดับ 1") })
    }

    @Test fun nonArrayEpisodesAreReported() {
        assertTrue(ScriptStructurePreflight.storyIssues(
            JSONObject("""{"episodes":"invalid"}""")
        ).any { it.contains("episodes ต้องเป็น array") })
    }

    @Test fun explicitInvalidNumbersAndDurationCannotFallBackToDefaults() {
        val story = JSONObject("""{"episodes":[{"ep":"bad","scenes":[{
            "scene":"bad","story_day":"DAY01","story_time":"เช้า",
            "location":"บ้าน","start_state":"ยืน","end_state":"นั่ง",
            "duration_seconds":"bad","characters":[{"name":"นายเปีย"}]
        }]}]}""")
        val issues = ScriptStructurePreflight.storyIssues(story)
        assertTrue(issues.any { it.contains("หมายเลข EP ต้องมากกว่า 0") })
        assertTrue(issues.any { it.contains("หมายเลขฉากต้องมากกว่า 0") })
        assertTrue(issues.any { it.contains("8 วินาที") })
    }

    @Test fun invalidArraysAndDialogueTurnsAreReported() {
        val story = JSONObject("""{"episodes":[{"ep":1,"scenes":[{
            "scene":1,"story_day":"DAY01","story_time":"เช้า",
            "location":"บ้าน","start_state":"ยืน","end_state":"นั่ง",
            "characters":"bad","dialogue_turns":[null]
        }]}]}""")
        val issues = ScriptStructurePreflight.storyIssues(story)
        assertTrue(issues.any { it.contains("characters ต้องเป็น array") })
        assertTrue(issues.any { it.contains("dialogue_turns ลำดับ 1 ต้องเป็น object") })
    }

    @Test fun malformedCastAndOffscreenItemsAreReportedWithoutCrashing() {
        val story = JSONObject("""{"episodes":[{"ep":1,"scenes":[{
            "scene":1,"story_day":"DAY01","story_time":"morning",
            "location":"home","start_state":"standing","end_state":"sitting",
            "characters":[null,"wrong",{"name":"นายเปีย"}],
            "offscreen_characters":[42,"ณัฐชา"]
        }]}]}""")
        val issues = ScriptStructurePreflight.storyIssues(story)
        assertTrue(issues.any { it.contains("characters ลำดับ 1 ต้องเป็น object") })
        assertTrue(issues.any { it.contains("characters ลำดับ 2 ต้องเป็น object") })
        assertTrue(issues.any { it.contains("offscreen_characters ลำดับ 1 ต้องเป็นข้อความ") })
    }

    @Test fun spokenDialogueWithoutSpeakerIsRejectedButNarrationOnlyIsAllowed() {
        val story = JSONObject("""{"episodes":[{"ep":1,"scenes":[{
            "scene":1,"story_day":"DAY01","story_time":"morning",
            "location":"home","start_state":"standing","end_state":"sitting",
            "characters":[{"name":"นายเปีย"}],"dialogue":"สวัสดีครับ"
        }]}]}""")
        assertTrue(ScriptStructurePreflight.storyIssues(story).any { it.contains("ขาด speaker") })
        val scene = story.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
        scene.remove("dialogue")
        scene.put("narration", "สวัสดีครับ")
        assertTrue(ScriptStructurePreflight.storyIssues(story).isEmpty())
    }

    @Test fun emptyOrderedTurnsPreserveLegacySingleSpeaker() {
        val story = JSONObject("""{"episodes":[{"ep":1,"scenes":[{
            "scene":1,"story_day":"DAY01","story_time":"morning",
            "location":"home","start_state":"standing","end_state":"sitting",
            "characters":[{"name":"นายเปีย"}],"speaker":"นายเปีย",
            "dialogue":"สวัสดีครับ","dialogue_turns":[]
        }]}]}""")
        assertTrue(ScriptStructurePreflight.storyIssues(story).isEmpty())
        val scene = story.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
        scene.remove("speaker")
        assertTrue(ScriptStructurePreflight.storyIssues(story).any { it.contains("ขาด speaker") })
    }

    @Test fun orderedDialogueTurnWithoutTextIsRejected() {
        val story = JSONObject("""{"episodes":[{"ep":1,"scenes":[{
            "scene":1,"story_day":"DAY01","story_time":"morning",
            "location":"home","start_state":"standing","end_state":"sitting",
            "characters":[{"name":"นายเปีย"}],
            "dialogue_turns":[{"speaker":"นายเปีย","text":""}]
        }]}]}""")
        assertTrue(ScriptStructurePreflight.storyIssues(story).any { it.contains("ขาดข้อความบทพูด") })
    }

    @Test fun mixingSingleAndMultiSpeakerDialogueIsRejectedWithoutEditingThaiText() {
        val story = JSONObject("""{"episodes":[{"ep":1,"scenes":[{
            "scene":1,"story_day":"DAY01","story_time":"morning",
            "location":"home","start_state":"standing","end_state":"sitting",
            "characters":[{"name":"นายเปีย"}],
            "speaker":"นายเปีย","dialogue":"สวัสดีครับ",
            "dialogue_turns":[{"speaker":"นายเปีย","text":"ไปกันเถอะ"}]
        }]}]}""")
        val original = story.toString()
        assertTrue(ScriptStructurePreflight.storyIssues(story).any { it.contains("ขัดแย้งกับ speaker/dialogue") })
        assertTrue(story.toString() == original)
    }

    @Test fun narrationCannotOverlapSpokenDialogueButNarrationOnlyIsValid() {
        val story = JSONObject("""{"episodes":[{"ep":1,"scenes":[{
            "scene":1,"story_day":"DAY01","story_time":"morning",
            "location":"home","start_state":"standing","end_state":"sitting",
            "characters":[{"name":"นายเปีย"}],
            "narration":"ผู้บรรยายกล่าว","dialogue_turns":[{"speaker":"นายเปีย","text":"ครับ"}]
        }]}]}""")
        assertTrue(ScriptStructurePreflight.storyIssues(story).any { it.contains("narration และบทพูด") })
        val scene = story.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
        scene.remove("dialogue_turns")
        assertTrue(ScriptStructurePreflight.storyIssues(story).isEmpty())
    }

    @Test fun numericDialogueFieldsAreRejectedInsteadOfSilentlyCoerced() {
        val story = JSONObject("""{"episodes":[{"ep":1,"scenes":[{
            "scene":1,"story_day":"DAY01","story_time":"morning",
            "location":"home","start_state":"standing","end_state":"sitting",
            "characters":[{"name":"นายเปีย"}],
            "dialogue_turns":[{"speaker":123,"text":456}]
        }]}]}""")
        val original = story.toString()
        val issues = ScriptStructurePreflight.storyIssues(story)
        assertTrue(issues.any { it.contains("speaker ต้องเป็นข้อความ") })
        assertTrue(issues.any { it.contains("text ต้องเป็นข้อความ") })
        assertTrue(story.toString() == original)
    }

    @Test fun numericCharacterNameIsRejectedWithoutMutatingStory() {
        val story = JSONObject("""{"episodes":[{"ep":1,"scenes":[{
            "scene":1,"story_day":"DAY01","story_time":"morning",
            "location":"home","start_state":"standing","end_state":"sitting",
            "characters":[{"name":123},{"name":"นายเปีย"}],
            "speaker":"นายเปีย","dialogue":"สวัสดีครับ"
        }]}]}""")
        val original = story.toString()
        assertTrue(ScriptStructurePreflight.storyIssues(story).any { it.contains("name ต้องเป็นข้อความ") })
        assertTrue(story.toString() == original)
    }

    @Test fun legacyDialogueFieldsMustBeStringsAndSourceRemainsUnchanged() {
        val story = JSONObject("""{"episodes":[{"ep":1,"scenes":[{
            "scene":1,"story_day":"DAY01","story_time":"morning",
            "location":"home","start_state":"standing","end_state":"sitting",
            "characters":[{"name":"นายเปีย"}],
            "speaker":123,"dialogue":456
        }]}]}""")
        val original = story.toString()
        val issues = ScriptStructurePreflight.storyIssues(story)
        assertTrue(issues.any { it.contains("speaker ต้องเป็นข้อความ") })
        assertTrue(issues.any { it.contains("dialogue ต้องเป็นข้อความ") })
        assertTrue(story.toString() == original)
    }

    @Test fun duplicateNamesAfterWhitespaceNormalizationAreReported() {
        val scene = validScene().copy(
            characterNames = listOf("นาย เปีย", "  นาย   เปีย  ", "ณัฐชา"),
            offscreenNames = listOf("ผู้บรรยาย", " ผู้บรรยาย "),
            dialogueSpeakers = listOf("ณัฐชา")
        )
        val issues = ScriptStructurePreflight.issues(
            listOf(ScriptStructurePreflight.Episode(1, listOf(scene))))
        assertTrue(issues.any { it.contains("ชื่อตัวละครซ้ำ") })
        assertTrue(issues.any { it.contains("ชื่อตัวละครนอกภาพซ้ำ") })
        assertTrue(issues.none { it.contains("ไม่พบผู้พูด") })
    }

}
