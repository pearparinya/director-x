package com.pearparinya.automovie

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
}
