package com.pearparinya.automovie

/** Provider-independent orchestration. No API key is stored in a job or passed to another provider. */
internal enum class StoryPipelineMode { GROQ_GEMINI, CEREBRAS_DEEPSEEK, OPENROUTER }

internal interface StoryPipelineWriter {
    fun write(brief: String): String
}

internal interface StoryPipelineFormatter {
    fun format(draft: String): String
}

internal interface StoryPipelineSinglePass {
    fun writeAndFormat(brief: String): String
}

internal class StoryPipeline(
    private val groq: StoryPipelineWriter,
    private val gemini: StoryPipelineFormatter,
    private val cerebras: StoryPipelineWriter,
    private val deepSeek: StoryPipelineFormatter,
    private val openRouter: StoryPipelineSinglePass,
    private val validate: (String) -> Unit
) {
    fun generate(mode: StoryPipelineMode, brief: String): String {
        require(brief.isNotBlank()) { "กรุณาระบุเรื่องย่อ" }
        val result = when (mode) {
            StoryPipelineMode.GROQ_GEMINI -> gemini.format(nonEmpty(groq.write(brief)))
            StoryPipelineMode.CEREBRAS_DEEPSEEK -> deepSeek.format(nonEmpty(cerebras.write(brief)))
            StoryPipelineMode.OPENROUTER -> openRouter.writeAndFormat(brief)
        }
        nonEmpty(result)
        validate(result)
        return result
    }

    private fun nonEmpty(value: String): String {
        require(value.isNotBlank()) { "ผู้ให้บริการส่งคำตอบว่างกลับมา" }
        return value
    }
}
