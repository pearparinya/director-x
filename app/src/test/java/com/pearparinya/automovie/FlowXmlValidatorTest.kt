package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class FlowXmlValidatorTest {
    @Test fun acceptsThirtyEightCompleteScenes() {
        val xml = (1..38).joinToString("", "<flow>", "</flow>") { "<scene><prompt>Act $it</prompt><dialogue>สวัสดี</dialogue></scene>" }
        FlowXmlValidator.validate(xml, 38)
        assertTrue(runCatching { FlowXmlValidator.validate(xml, 37) }.isFailure)
    }

    @Test fun rejectsIncompleteAndDangerousXml() {
        listOf("<flow><scene><prompt>Act</prompt></scene></flow>",
            "<flow><scene><prompt></prompt><dialogue/></scene></flow>",
            "<!DOCTYPE flow [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><flow><scene><prompt>&x;</prompt><dialogue/></scene></flow>")
            .forEach { assertTrue(runCatching { FlowXmlValidator.validate(it, 1) }.isFailure) }
    }
}
