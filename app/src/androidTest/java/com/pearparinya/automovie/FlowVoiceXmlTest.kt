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
    @Test fun concisePromptRestoresPreviouslySuccessfulWording() {
        val line="ผลงานของคุณน่าสนใจมากค่ะ"
        val doc=xml(FlowVoiceXml.build(spec(dialogue=line,speaker="ภูวดล"),"Slow pan."))
        val prompt=doc.getElementsByTagName("prompt").item(0).textContent
        assertEquals("ภูวดล checks A & B. Only [ภูวดล] speaks with natural lip movement; other visible characters stay silent. Preserve the attached first frame, faces, hair, body proportions, positions, clothes, shoes, props, lighting and location. Slow pan.",prompt)
        assertEquals(line,doc.getElementsByTagName("text").item(0).textContent)
        assertFalse(prompt.contains("throat clearing"))
        assertFalse(prompt.contains("Central Thai"))
        val primary=FlowScenePrompt.build(spec(dialogue=line,speaker="ภูวดล"))
        assertTrue(primary.contains(line))
        assertFalse(primary.contains("throat clearing"))
        assertFalse(primary.contains("Central Thai"))
    }
    @Test fun galleryPatronMurmursDoNotConflictWithNoExtraVoices() {
        val sound="Ambience: soft murmur of art gallery patrons; SFX: gentle footsteps on polished floor; Music: subtle Thai classical strings with soft piano."
        val line="ผลงานของคุณน่าสนใจมากค่ะ"
        val command=FlowScenePrompt.build(spec(dialogue=line,speaker="ภูวดล").copy(sound=sound))
        val doc=xml(command)
        val ambient=doc.getElementsByTagName("ambient").item(0).textContent
        assertTrue(ambient.contains("quiet room tone"))
        assertFalse(ambient.contains("murmur of art gallery patrons"))
        assertTrue(ambient.contains("gentle footsteps on polished floor"))
        assertTrue(ambient.contains("subtle Thai classical strings with soft piano"))
        assertEquals(line,doc.getElementsByTagName("text").item(0).textContent)
        assertTrue(FlowScenePrompt.build(spec().copy(sound="murmur of wind")).contains("murmur of wind"))
    }
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
