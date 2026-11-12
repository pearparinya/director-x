package com.pearparinya.automovie

internal object StoryClientFactory {
    fun create(provider: StoryProvider,key: String,model: String,cancelled: () -> Boolean,
        onWait: (Int)->Unit = {},onRetry: (String)->Unit = {},automaticRetry: Boolean = true): StoryTextClient =
        when(provider) {
            StoryProvider.GROQ -> GroqClient(key,model,cancelled,onWait,onRetry,automaticRetry)
            StoryProvider.GEMINI -> GeminiClient(key,model,cancelled,onRetry,automaticRetry)
            else -> CompatibleStoryClient(provider.extra()!!.service,key,model,cancelled,onRetry,automaticRetry)
        }
}
