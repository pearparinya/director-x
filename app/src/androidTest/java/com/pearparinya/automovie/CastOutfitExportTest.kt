package com.pearparinya.automovie

import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CastOutfitExportTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun coverAndSceneShareMatchingWardrobeWhileNextOutfitKeepsIdentity() {
        val project = folder.newFolder()
        fun scene(n: Int, outfit: String): JSONObject {
            val actor = JSONObject().put("name","Pia").put("master","PIA_MASTER.png")
                .put("outfit",outfit).put("screen_position","center").put("visual_description","photographer")
            return JSONObject().put("ep",1).put("scene",n).put("story_day","1").put("location","house")
                .put("start_state","standing").put("action","looks up").put("characters",JSONArray().put(actor))
        }
        val ep = JSONObject().put("ep",1).put("title","House")
            .put("scenes",JSONArray().put(scene(1,"white shirt")).put(scene(2,"jacket")))
        val story = JSONObject().put("channel","Drama").put("title","House").put("tagline","Night")
            .put("episodes",JSONArray().put(ep))
        val tasks = SimpleProductionCommands.imageTasks(story,1)
        fun streams(id: String): List<Uri> {
            val task = tasks.single { it.id == id }
            val person = task.references.single()
            val wardrobe = CastOutfit(person.getString("master").substringBeforeLast('.'),person.getString("outfit"))
            val approved = wardrobe.attachment(project)!!
            val images = arrayListOf(Uri.parse("content://test/" + person.getString("master")),
                Uri.parse("content://test/" + approved.second.name))
            return buildGeminiImageShareIntent(task.command,images)
                .getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!
        }
        for (description in listOf("white shirt","jacket")) {
            val file = CastOutfit("PIA_MASTER",description).file(project)
            file.parentFile!!.mkdirs()
            file.writeBytes(description.toByteArray())
        }
        val cover = streams("STORY_COVER_9x16")
        assertEquals(cover, streams("EP01_SC001"))
        val change = streams("EP01_SC002")
        assertEquals(cover.first(), change.first())
        assertNotEquals(cover.last(), change.last())
    }
}
