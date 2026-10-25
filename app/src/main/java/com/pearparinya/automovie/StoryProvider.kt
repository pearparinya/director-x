package com.pearparinya.automovie

import android.content.Context
import org.json.JSONObject

internal enum class StoryProvider(val id: String,val label: String,val defaultModel: String,val keyUrl: String) {
    GROQ("groq","Groq","openai/gpt-oss-120b","https://console.groq.com/keys"),
    GEMINI("gemini","Gemini",GeminiSettings.DEFAULT_MODEL,"https://aistudio.google.com/apikey");
    companion object {
        fun from(id: String)=entries.firstOrNull { it.id==id } ?: throw IllegalArgumentException("ไม่รู้จักผู้ให้บริการ API")
        fun selected(context: Context)=from(context.getSharedPreferences("story_provider",Context.MODE_PRIVATE).getString("selected","groq")!!)
        fun hybrid(context: Context)=context.getSharedPreferences("story_provider",Context.MODE_PRIVATE).getBoolean("hybrid",false)
        fun enableHybrid(context: Context) { context.getSharedPreferences("story_provider",Context.MODE_PRIVATE).edit().putBoolean("hybrid",true).apply() }
        fun label(context: Context)=if(hybrid(context)) "Groq + Gemini อัตโนมัติ" else selected(context).label
        fun ready(context: Context)=if(hybrid(context)) entries.all { GeminiSettings(context,it).hasKey() } else GeminiSettings(context,selected(context)).hasKey()
        fun configure(context: Context,spec: JSONObject): JSONObject {
            val chosen=selected(context)
            spec.put("provider",chosen.id).put("model",GeminiSettings(context,chosen).model()).put("hybrid",hybrid(context))
            if(hybrid(context)) entries.forEach { spec.put("${it.id}_model",GeminiSettings(context,it).model()) }
            return spec
        }
        fun select(context: Context,provider: StoryProvider) { context.getSharedPreferences("story_provider",Context.MODE_PRIVATE).edit().putString("selected",provider.id).putBoolean("hybrid",false).apply() }
    }
}
internal interface StoryTextClient {
    fun generate(prompt: String,schema: JSONObject): JSONObject
    fun cancel()
}
