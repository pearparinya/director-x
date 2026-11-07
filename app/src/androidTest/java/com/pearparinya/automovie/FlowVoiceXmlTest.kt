package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlowVoiceXmlTest {
    private fun spec(dialogue: String="", narration: String="", speaker: String="", offscreen: List<String> = emptyList()) =
        FlowSceneSpec(listOf(FlowPerson("ภูวดล","adult man","center","shirt")),speaker,dialogue,narration,
            "ภูวดล checks A & B","office","standing","reading",8,"9:16","",offscreen)
    private fun xml(text: String)=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(text)))
    @Test fun narrationUsesOffscreenAndEscapesText() {
        val doc=xml(FlowVoiceXml.build(spec(narration="ตัวเลข A & B < C"),"Slow pan."))
        val d=doc.getElementsByTagName("dialogue").item(0) as org.w3c.dom.Element
        assertEquals("true",d.getAttribute("off_screen"))
        assertEquals("ตัวเลข A & B < C",doc.getElementsByTagName("voice").item(0).textContent)
        assertTrue(doc.getElementsByTagName("prompt").item(0).textContent.contains("A & B"))
    }
    @Test fun visibleAndOffscreenDialogueKeepSpeakerAndLines() {
        val visible=xml(FlowVoiceXml.build(spec(dialogue="ตรวจสอบด้วยครับ",speaker="ภูวดล"),"Slow pan."))
        assertEquals("[ภูวดล]",visible.getElementsByTagName("speaker").item(0).textContent)
        assertEquals("ตรวจสอบด้วยครับ",visible.getElementsByTagName("text").item(0).textContent)
        val silent=xml(FlowVoiceXml.build(spec(),"Locked camera."))
        assertEquals(0,silent.getElementsByTagName("dialogue").length)
    }
}
