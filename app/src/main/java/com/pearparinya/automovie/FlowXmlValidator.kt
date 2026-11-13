package com.pearparinya.automovie

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

/** Check model-produced XML before it can be used; never resolve external entities. */
internal object FlowXmlValidator {
    fun validate(xml: String, expectedScenes: Int) {
        require(expectedScenes > 0) { "จำนวนซีนไม่ถูกต้อง" }
        require(xml.length in 1..2_000_000) { "ขนาด XML ไม่ถูกต้อง" }
        val parser = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            isXIncludeAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }.newSAXParser().xmlReader
        val handler = SceneHandler()
        parser.contentHandler = handler
        parser.entityResolver = org.xml.sax.EntityResolver { _, _ -> throw IllegalArgumentException("ไม่อนุญาต external entity") }
        try { parser.parse(InputSource(StringReader(xml))) }
        catch (e: Exception) { throw IllegalArgumentException("XML ไม่ถูกต้อง: ${e.message.orEmpty().take(160)}", e) }
        require(handler.count == expectedScenes) { "จำนวนซีนใน XML ไม่ตรง: ${handler.count}/$expectedScenes" }
    }

    private class SceneHandler : DefaultHandler() {
        var count = 0
        private var depth = 0
        private var sceneDepth = -1
        private var promptDepth = -1
        private var hasDialogue = false
        private var prompt = StringBuilder()
        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
            depth++
            when (qName) {
                "scene" -> { require(sceneDepth == -1) { "ซีนซ้อนกัน" }; sceneDepth = depth; hasDialogue = false; prompt = StringBuilder() }
                "prompt" -> if (sceneDepth != -1 && depth == sceneDepth + 1) { require(promptDepth == -1); promptDepth = depth }
                "dialogue" -> if (sceneDepth != -1 && depth == sceneDepth + 1) hasDialogue = true
            }
        }
        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (promptDepth != -1) prompt.append(ch, start, length)
        }
        override fun endElement(uri: String?, localName: String?, qName: String) {
            if (depth == promptDepth && qName == "prompt") promptDepth = -1
            if (depth == sceneDepth && qName == "scene") {
                require(prompt.isNotBlank()) { "ซีนไม่มี prompt" }
                require(hasDialogue) { "ซีนไม่มี dialogue" }
                count++; sceneDepth = -1
            }
            depth--
        }
    }
}
