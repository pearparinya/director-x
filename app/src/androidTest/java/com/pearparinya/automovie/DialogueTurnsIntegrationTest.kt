package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DialogueTurnsIntegrationTest {
    private fun person(name: String, appearance: String, position: String) = JSONObject()
        .put("name",name).put("visual_description",appearance).put("screen_position",position).put("outfit","shirt")
    private fun scene(): JSONObject = JSONObject()
        .put("characters",JSONArray().put(person("เมย์","short hair","left")).put(person("รินรดา","long hair","right")))
        .put("speaker","").put("dialogue","").put("narration","")
        .put("action","คุยกัน").put("flow_action","May looks at Rinlada").put("location","office")
        .put("start_state","standing").put("end_state","standing")
        .put("dialogue_turns",JSONArray().put(JSONObject().put("speaker","เมย์").put("text","สวัสดีค่ะ"))
            .put(JSONObject().put("speaker","รินรดา").put("text","สวัสดีจ้ะ")))
    @Test fun sceneDataFlowsToOrderedXmlAndOverview() {
        val scene=scene()
        val spec=ProductionCommandCheck.spec(scene)
        assertTrue(FlowScenePrompt.issues(spec).toString(),FlowScenePrompt.issues(spec).isEmpty())
        val xml=FlowVoiceXml.build(spec,"Locked camera.")
        assertTrue(xml.indexOf("สวัสดีค่ะ") < xml.indexOf("สวัสดีจ้ะ"))
        assertTrue(xml.contains("[Character 1]")); assertTrue(xml.contains("[Character 2]"))
        assertTrue(SceneOverview.detail(scene,"EP01_SC001").contains("รินรดา: สวัสดีจ้ะ"))
        assertTrue(CompactFlowPrompt.voiceText(spec).contains("เมย์: สวัสดีค่ะ"))
    }
    @Test fun legacySingleSpeakerAndInvalidMixedMode() {
        val source=scene().apply { remove("dialogue_turns"); put("speaker","เมย์"); put("dialogue","สวัสดีค่ะ") }
        assertTrue(FlowScenePrompt.issues(ProductionCommandCheck.spec(source)).isEmpty())
        val invalid=scene().put("speaker","เมย์").put("dialogue","ข้อความเดิม")
        assertTrue(FlowScenePrompt.issues(ProductionCommandCheck.spec(invalid)).any { it.contains("บทสนทนาหลายคน") })
    }
}
