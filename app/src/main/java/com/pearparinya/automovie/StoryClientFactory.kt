package com.pearparinya.automovie

internal object StoryClientFactory {
    fun create(provider: StoryProvider,key: String,model: String,cancelled: () -> Boolean,
        onWait: (Int)->Unit = {},onRetry: (String)->Unit = {},automaticRetry: Boolean = true, context: android.content.Context? = null): StoryTextClient =
        when(provider) {
            StoryProvider.GROQ -> GroqClient(key,model,cancelled,onWait,onRetry,automaticRetry)
            StoryProvider.GEMINI -> {
                if(context==null) GeminiClient(key,model,cancelled,onRetry,automaticRetry)
                else {
                    val configured=GeminiSettings.configured(context)
                    val slots=configured.map { (number,secret) ->
                        GeminiPoolClient.Slot(number,GeminiClient(secret,model,cancelled,onRetry,false)) { GeminiPacing.keyCooldownMillis(secret) }
                    }
                    GeminiPoolClient(slots,{ GeminiKeyPool.next(context).first },cancelled,
                        { number -> onRetry("กำลังทำต่อด้วย Gemini คีย์ช่องที่ $number") },automaticRetry,onRetry)
                }
            }
            else -> {
                require(provider in StoryProvider.activeProviders) { "API นี้ไม่เปิดใช้งานแล้ว • เลือก API อื่นในการตั้งค่า" }
                CompatibleStoryClient(provider.extra()!!.service,key,model,cancelled,onRetry,automaticRetry)
            }
        }
}
