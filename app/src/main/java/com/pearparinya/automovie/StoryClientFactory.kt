package com.pearparinya.automovie

internal object StoryClientFactory {
    fun create(provider: StoryProvider,key: String,model: String,cancelled: () -> Boolean,
        onWait: (Int)->Unit = {},onRetry: (String)->Unit = {},automaticRetry: Boolean = true, context: android.content.Context? = null): StoryTextClient =
        when(provider) {
            StoryProvider.GROQ -> GroqClient(key,model,cancelled,onWait,onRetry,automaticRetry)
            StoryProvider.GEMINI -> {
                val selected=context?.let { GeminiKeyPool.next(it) }
                if(selected!=null) onRetry("กำลังทำต่อด้วย Gemini คีย์ช่องที่ ${selected.first}")
                GeminiClient(selected?.second ?: key,model,cancelled,onRetry,automaticRetry)
            }
            else -> {
                require(provider in StoryProvider.activeProviders) { "API นี้ไม่เปิดใช้งานแล้ว • เลือก API อื่นในการตั้งค่า" }
                CompatibleStoryClient(provider.extra()!!.service,key,model,cancelled,onRetry,automaticRetry)
            }
        }
}
