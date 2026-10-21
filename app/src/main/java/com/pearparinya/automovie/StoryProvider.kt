package com.pearparinya.automovie

import android.content.Context
import org.json.JSONObject

internal enum class StoryProvider(val id: String,val label: String,val defaultModel: String,val keyUrl: String) {
    GROQ("groq","Groq","openai/gpt-oss-120b","https://console.groq.com/keys"),
    GEMINI("gemini","Gemini",GeminiSettings.DEFAULT_MODEL,"https://aistudio.google.com/apikey");
    companion object {
        fun from(id: String)=entries.firstOrNull { it.id==id } ?: throw IllegalArgumentException("ไม่รู้จักผู้ให้บริการ API")
        fun selected(context: Context)=from(context.getSharedPreferences("story_provider",Context.MODE_PRIVATE).getString("selected","groq")!!)
        fun select(context: Context,provider: StoryProvider) { context.getSharedPreferences("story_provider",Context.MODE_PRIVATE).edit().putString("selected",provider.id).apply() }
    }
}
internal interface StoryTextClient {
    fun generate(prompt: String,schema: JSONObject): JSONObject
    fun cancel()
}
