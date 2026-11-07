package com.pearparinya.automovie

import android.content.Context
import org.json.JSONObject

internal enum class StoryProvider(val id: String,val label: String,val defaultModel: String,val keyUrl: String) {
    GROQ("groq","Groq","openai/gpt-oss-120b","https://console.groq.com/keys"),
    GEMINI("gemini","Gemini",GeminiSettings.DEFAULT_MODEL,"https://aistudio.google.com/apikey"),
    CEREBRAS("cerebras","Cerebras",CompatibleService.CEREBRAS.defaultModel,"https://cloud.cerebras.ai/"),
    DEEPSEEK("deepseek","DeepSeek",CompatibleService.DEEPSEEK.defaultModel,"https://platform.deepseek.com/"),
    OPENROUTER("openrouter","OpenRouter",CompatibleService.OPENROUTER.defaultModel,"https://openrouter.ai/keys"),
    SAMBANOVA("sambanova","SambaNova",CompatibleService.SAMBANOVA.defaultModel,"https://cloud.sambanova.ai/"),
    MISTRAL("mistral","Mistral",CompatibleService.MISTRAL.defaultModel,"https://console.mistral.ai/api-keys"),
    COHERE("cohere","Cohere",CompatibleService.COHERE.defaultModel,"https://dashboard.cohere.com/api-keys");
    fun extra(): ExtraApiProvider? = when(this) {
        CEREBRAS -> ExtraApiProvider.CEREBRAS
        DEEPSEEK -> ExtraApiProvider.DEEPSEEK
        OPENROUTER -> ExtraApiProvider.OPENROUTER
        SAMBANOVA -> ExtraApiProvider.SAMBANOVA
        MISTRAL -> ExtraApiProvider.MISTRAL
        COHERE -> ExtraApiProvider.COHERE
        else -> null
    }
    fun key(context: Context): String = extra()?.let { ExtraApiSettings(context,it).key() } ?: GeminiSettings(context,this).key()
    fun model(context: Context): String = extra()?.let { ExtraApiSettings(context,it).model() } ?: GeminiSettings(context,this).model()
    fun hasKey(context: Context): Boolean = runCatching { key(context).isNotBlank() }.getOrDefault(false)
    companion object {
        fun from(id: String)=entries.firstOrNull { it.id==id } ?: throw IllegalArgumentException("ไม่รู้จักผู้ให้บริการ API")
        fun selected(context: Context)=from(context.getSharedPreferences("story_provider",Context.MODE_PRIVATE).getString("selected","groq")!!)
        fun hybrid(context: Context)=context.getSharedPreferences("story_provider",Context.MODE_PRIVATE).getBoolean("hybrid",false)
        fun enableHybrid(context: Context) { context.getSharedPreferences("story_provider",Context.MODE_PRIVATE).edit().putBoolean("hybrid",true).apply() }
        fun label(context: Context)="API อัตโนมัติ"
        val hybridProviders=listOf(GROQ,GEMINI)
        val activeProviders=listOf(GROQ,GEMINI,DEEPSEEK,OPENROUTER,MISTRAL,SAMBANOVA,COHERE)
        fun ready(context: Context)=(GROQ.hasKey(context) && GEMINI.hasKey(context)) || (GEMINI.hasKey(context) && DEEPSEEK.hasKey(context)) || OPENROUTER.hasKey(context) || COHERE.hasKey(context) || MISTRAL.hasKey(context) || SAMBANOVA.hasKey(context)
        fun configure(context: Context,spec: JSONObject): JSONObject {
            spec.put("provider",GROQ.id).put("model",GROQ.model(context)).put("hybrid",false).put("auto_pipeline",true)
            activeProviders.filter { it.hasKey(context) }.forEach { spec.put("${it.id}_model",it.model(context)) }
            return spec
        }
        fun select(context: Context,provider: StoryProvider) { context.getSharedPreferences("story_provider",Context.MODE_PRIVATE).edit().putString("selected",provider.id).putBoolean("hybrid",false).apply() }
    }
}
internal interface StoryTextClient {
    fun generate(prompt: String,schema: JSONObject): JSONObject
    fun cancel()
    fun availableDelayMillis(): Long = 0L
}
