package com.pearparinya.automovie

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import org.junit.Assert.*
import org.junit.Test

class FlowVoiceXmlUnitTest {
    @Test fun knownEnglishAliasesAreBoundToTheirOwnCharacter() {
        val cast=listOf(FlowPerson("เมย์","short hair","left","blue"),FlowPerson("รินรดา","long hair","right","white"))
        val spec=FlowSceneSpec(cast,"เมย์","สวัสดีค่ะ","","May looks at Rinlada","office","standing","standing",8,"9:16")
        val xml=FlowVoiceXml.build(spec,"Locked camera.")
        assertTrue(xml.contains("Character 1 looks at Character 2"))
        assertFalse(xml.contains("Rinlada"))
    }
    @Test fun orderedTurnsAreBoundAndEscapeText() {
        val cast=listOf(FlowPerson("เมย์","short hair","left","blue"),FlowPerson("รินรดา","long hair","right","white"))
        val turns=listOf(FlowDialogueTurn("เมย์","นี่คือ A & B"),FlowDialogueTurn("รินรดา","เข้าใจแล้วค่ะ"))
        val s=FlowSceneSpec(cast,"","","","May looks at Rinlada","office","standing","standing",8,"9:16",turns=turns)
        val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(FlowVoiceXml.build(s,"Locked camera."))))
        assertEquals(2,doc.getElementsByTagName("dialogue").length)
        assertTrue(doc.getElementsByTagName("prompt").item(0).textContent.contains(ScenePropContinuity.video))
        assertEquals("[Character 1]",doc.getElementsByTagName("speaker").item(0).textContent)
        assertEquals("[Character 2]",doc.getElementsByTagName("speaker").item(1).textContent)
        assertEquals(turns[0].text,doc.getElementsByTagName("text").item(0).textContent)
        assertTrue(FlowScenePrompt.issues(s.copy(turns=listOf(FlowDialogueTurn("คนแปลกหน้า","สวัสดี")))).isNotEmpty())
        assertTrue(FlowScenePrompt.issues(s.copy(dialogue="บทเดิม",speaker="เมย์")).isNotEmpty())
    }
    @Test fun labelsSpeakerByPositionAndPreservesThaiSpeech() {
        val s=FlowSceneSpec(listOf(FlowPerson("ณัฐชา","woman","center","red dress")),"ณัฐชา","ตัวเลข A & B < C","","ณัฐชา checks a folder","office","standing","standing",8,"9:16")
        val command=FlowVoiceXml.build(s,"Locked camera.")
        val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(command)))
        assertEquals("[Character 1]",doc.getElementsByTagName("speaker").item(0).textContent)
        assertEquals(s.dialogue,doc.getElementsByTagName("text").item(0).textContent)
        assertTrue(doc.getElementsByTagName("prompt").item(0).textContent.contains("Character 1: center"))
        assertFalse(doc.getElementsByTagName("prompt").item(0).textContent.contains("ณัฐชา"))
        assertEquals("SLIGHTLY BELOW CENTER",doc.getElementsByTagName("typography_safe_zone").item(0).textContent)
        assertEquals(1,doc.getElementsByTagName("lighting").length)
        assertFalse(command.contains(".jpg",true))
        assertTrue(doc.getElementsByTagName("prompt").item(0).textContent.contains(ScenePropContinuity.video))
        assertTrue(FlowScenePrompt.build(s).contains(ScenePropContinuity.video))
        assertTrue(CompactFlowPrompt.build(s,"Locked camera.",true).contains(ScenePropContinuity.video))
    }
    @Test fun speakerLockKeepsListenerSilentAndUsesCharacterNames() {
        val cast=listOf(
            FlowPerson("แม่จันทร์","worried woman","left","floral blouse"),
            FlowPerson("ลุงเพิ่ม","older man","right","blue shirt")
        )
        val turns=listOf(
            FlowDialogueTurn("แม่จันทร์","ลุงเพิ่ม ฉันกังวลว่าน้ำจะท่วมบ้านเราเลย!"),
            FlowDialogueTurn("ลุงเพิ่ม","อย่ากังวล แม่จันทร์")
        )
        val scene=FlowSceneSpec(cast,"","","","standing by house","home","standing","standing",8,"9:16",turns=turns)
        val xml=FlowVoiceXml.build(scene,"Locked camera.")
        val prompt=DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(InputSource(StringReader(xml))).getElementsByTagName("prompt").item(0).textContent
        assertTrue(prompt.contains("STRICT SPEAKER AND LIP-SYNC LOCK"))
        assertTrue(prompt.contains("TURN 1: ONLY Character 1 (แม่จันทร์)"))
        assertTrue(prompt.contains("TURN 2: ONLY Character 2 (ลุงเพิ่ม)"))
        assertTrue(prompt.contains("Character 2 must remain silent"))
        assertTrue(prompt.contains("Character 1 must remain silent"))
        assertTrue(prompt.contains("Never swap dialogue"))
        assertEquals(2,DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(InputSource(StringReader(xml))).getElementsByTagName("dialogue").length)
    }
}
