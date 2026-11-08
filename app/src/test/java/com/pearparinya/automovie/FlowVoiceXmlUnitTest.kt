package com.pearparinya.automovie

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import org.junit.Assert.*
import org.junit.Test

class FlowVoiceXmlUnitTest {
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
    }
}
