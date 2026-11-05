package com.pearparinya.automovie

import android.content.Context
import org.json.JSONObject

internal enum class StoryProvider(val id: String,val label: String,val defaultModel: String,val keyUrl: String) {
    GROQ("groq","Groq","openai/gpt-oss-120b","https://console.groq.com/keys"),
    GEMINI("gemini","Gemini",GeminiSettings.DEFAULT_MODEL,"https://aistudio.google.com/apikey"),
    CEREBRAS("cerebras","Cerebras",CompatibleService.CEREBRAS.defaultModel,"https://cloud.cerebras.ai/"),
    DEEPSEEK("deepseek","DeepSeek",CompatibleService.DEEPSEEK.defaultModel,"https://platform.deepseek.com/"),
    OPENROUTER("openrouter","OpenRouter",CompatibleService.OPENROUTER.defaultModel,"https://openrouter.ai/keys");
    fun extra(): ExtraApiProvider? = when(this) {
        CEREBRAS -> ExtraApiProvider.CEREBRAS
        DEEPSEEK -> ExtraApiProvider.DEEPSEEK
        OPENROUTER -> ExtraApiProvider.OPENROUTER
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
        fun label(context: Context)=if(hybrid(context)) "Groq + Gemini อัตโนมัติ" else selected(context).label
        val hybridProviders=listOf(GROQ,GEMINI)
        fun ready(context: Context)=if(hybrid(context)) hybridProviders.all { it.hasKey(context) } else selected(context).hasKey(context)
        fun configure(context: Context,spec: JSONObject): JSONObject {
            val chosen=selected(context)
            spec.put("provider",chosen.id).put("model",chosen.model(context)).put("hybrid",hybrid(context))
            if(hybrid(context)) hybridProviders.forEach { spec.put("${it.id}_model",it.model(context)) }
            return spec
        }
        fun select(context: Context,provider: StoryProvider) { context.getSharedPreferences("story_provider",Context.MODE_PRIVATE).edit().putString("selected",provider.id).putBoolean("hybrid",false).apply() }
    }
}
internal interface StoryTextClient {
    fun generate(prompt: String,schema: JSONObject): JSONObject
    fun cancel()
}
