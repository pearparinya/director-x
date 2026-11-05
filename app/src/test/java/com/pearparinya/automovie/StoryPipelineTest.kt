package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class StoryPipelineTest {
    @Test fun selectedModeUsesOnlyItsProvidersInOrder() {
        for ((mode, expected) in listOf(
            StoryPipelineMode.GROQ_GEMINI to listOf("groq", "gemini", "validate"),
            StoryPipelineMode.GEMINI_DEEPSEEK to listOf("gemini-writer", "deepseek", "validate"),
            StoryPipelineMode.OPENROUTER to listOf("openrouter", "validate")
        )) {
            val calls = mutableListOf<String>()
            val pipeline = StoryPipeline(
                object : StoryPipelineWriter { override fun write(brief: String) = "draft".also { calls.add("groq") } },
                object : StoryPipelineFormatter { override fun format(draft: String) = "xml".also { calls.add("gemini"); assertEquals("draft", draft) } },
                object : StoryPipelineWriter { override fun write(brief: String) = "draft".also { calls.add("gemini-writer") } },
                object : StoryPipelineFormatter { override fun format(draft: String) = "xml".also { calls.add("deepseek"); assertEquals("draft", draft) } },
                object : StoryPipelineSinglePass { override fun writeAndFormat(brief: String) = "xml".also { calls.add("openrouter") } },
                { calls.add("validate"); assertEquals("xml", it) }
            )
            assertEquals("xml", pipeline.generate(mode, "เรื่องย่อ"))
            assertEquals(expected, calls)
        }
    }

    @Test fun failedWriterDoesNotCallFormatterOrChangeProvider() {
        var formatterCalled = false
        val pipeline = StoryPipeline(
            object : StoryPipelineWriter { override fun write(brief: String): String = error("unavailable") },
            object : StoryPipelineFormatter { override fun format(draft: String): String { formatterCalled = true; return "xml" } },
            object : StoryPipelineWriter { override fun write(brief: String): String = error("wrong provider") },
            object : StoryPipelineFormatter { override fun format(draft: String): String = error("wrong provider") },
            object : StoryPipelineSinglePass { override fun writeAndFormat(brief: String): String = error("wrong provider") },
            { error("unexpected validation") }
        )
        assertTrue(runCatching { pipeline.generate(StoryPipelineMode.GROQ_GEMINI, "brief") }.isFailure)
        assertFalse(formatterCalled)
    }
}
