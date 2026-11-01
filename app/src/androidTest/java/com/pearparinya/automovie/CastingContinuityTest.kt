package com.pearparinya.automovie
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class CastingContinuityTest {
    private val din=JSONObject().put("name","ดิน").put("master","SUPPORT_00000016_MASTER.png").put("character_age",28)
    @Test fun absentReferencesAreScopedAndPhysicalInteractionsStillFail() {
        val s=JSONObject().put("characters",JSONArray()).put("action","เธอมองพื้นดิน")
        assertTrue(ProductionCommandCheck.namedIssues(s,listOf(din)).isEmpty())
        s.put("action","เธอพูดถึง ดิน")
        assertTrue(ProductionCommandCheck.namedIssues(s,listOf(din)).isEmpty())
        s.put("action","ดิน เดินเข้ามา")
        assertTrue(ProductionCommandCheck.namedIssues(s,listOf(din)).isNotEmpty())
        s.put("action","ดิน waits outside the frame").put("offscreen_characters",JSONArray().put("ดิน"))
        assertTrue(ProductionCommandCheck.namedIssues(s,listOf(din)).isEmpty())
        s.put("action","ดิน hugs her")
        assertTrue(ProductionCommandCheck.namedIssues(s,listOf(din)).isNotEmpty())
        s.put("action","เธอพูดถึงข่าว; ดิน เดินเข้ามา").remove("offscreen_characters")
        assertTrue(ProductionCommandCheck.namedIssues(s,listOf(din)).isNotEmpty())
    }
    @Test fun offscreenSpeechNeverAnimatesVisibleMouths() {
        val spec=FlowSceneSpec(listOf(FlowPerson("นลิน","black hair","center","white shirt")),"ดิน","รอผมก่อนครับ","","she listens","office","waiting","nods",8,"9:16",offscreen=listOf("ดิน"))
        assertTrue(FlowScenePrompt.issues(spec).isEmpty())
        val prompt=FlowScenePrompt.build(spec)
        assertTrue(prompt.contains("OFF-SCREEN CHARACTER SPEAKER ONLY: ดิน"))
        assertTrue(prompt.contains("All visible mouths stay closed"))
        assertTrue(FlowScenePrompt.issues(spec.copy(offscreen=emptyList())).isNotEmpty())
    }
    @Test fun frozenAgeRejectsNumericAndDescriptionDrift() {
        val p=JSONObject(din.toString()).put("character_age",45)
        val s=JSONObject().put("characters",JSONArray().put(p))
        assertTrue(CharacterAgeCheck.issues(s,listOf(din)).isNotEmpty())
        p.put("character_age",28).put("visual_description","a 45-year-old man")
        assertTrue(CharacterAgeCheck.issues(s,listOf(din)).isNotEmpty())
        p.put("visual_description","a 28-year-old man")
        assertTrue(CharacterAgeCheck.issues(s,listOf(din)).isEmpty())
    }
    @Test fun ageGapGenreRequiresCorrectLeadsRatherThanAnyOlderExtra() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val catalog=JSONObject(TestMasterAssets.open("master_library/catalog.json").bufferedReader().use { it.readText() }).getJSONArray("records")
        val cat=ChannelCatalog.mainCategories.first { it.id=="younger_man_older_woman" }.label
        fun actor(id: String,role: String)=JSONObject().put("library_id",id).put("romance_role",role)
        val cast=JSONArray().put(actor("CHAR_022","younger_man")).put(actor("CHAR_017","older_woman"))
        val value=JSONObject().put("cast",cast)
        AutoCast.validateGenre(value,catalog,cat)
        cast.getJSONObject(1).put("library_id","CHAR_002")
        assertTrue(runCatching { AutoCast.validateGenre(value,catalog,cat) }.isFailure)
        cast.getJSONObject(1).put("library_id","CHAR_017");cast.getJSONObject(0).put("library_id","CHAR_023")
        assertTrue(runCatching { AutoCast.validateGenre(value,catalog,cat) }.isFailure)
    }
}
