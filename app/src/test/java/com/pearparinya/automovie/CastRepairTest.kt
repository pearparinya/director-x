package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CastRepairTest {
    private val catalog = JSONArray("""[{"id":"lead"}]""")
    private val valid = JSONObject("""{"story_concept":"เรื่อง","cast":[{"library_id":"lead","name":"เปีย","role":"ตัวเอก"}]}""")

    @Test fun repairsMissingConceptWithSameProvider() {
        val prompts = mutableListOf<String>()
        val result = AutoCast.propose("cast", catalog, "lead", { false }) {
            prompts.add(it)
            if (prompts.size == 1) JSONObject() else valid
        }
        assertEquals(valid, result)
        assertEquals(2, prompts.size)
        assertTrue(prompts.last().contains("ขาดแนวคิดเรื่อง"))
    }

    @Test fun reportsFinalValidationErrorAfterOneRepair() {
        var calls = 0
        val error = runCatching {
            AutoCast.propose("cast", catalog, "different", { false }) { calls++; valid }
        }.exceptionOrNull()
        assertTrue(error is AutoCast.ProposalError)
        assertEquals("ต้องมี MASTER ผู้ดำเนินเรื่องที่เลือก", error?.message)
        assertEquals(2, calls)
    }
}
