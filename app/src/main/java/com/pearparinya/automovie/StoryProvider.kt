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
    fun key(context: Context): String = if(this==GEMINI) GeminiSettings.configured(context).firstOrNull()?.second.orEmpty() else extra()?.let { ExtraApiSettings(context,it).key() } ?: GeminiSettings(context,this).key()
    fun model(context: Context): String = if(this==GEMINI) GeminiSettings.firstModel(context) else extra()?.let { ExtraApiSettings(context,it).model() } ?: GeminiSettings(context,this).model()
    fun hasKey(context: Context): Boolean = runCatching { key(context).isNotBlank() }.getOrDefault(false)
    companion object {
        fun from(id: String)=entries.firstOrNull { it.id==id } ?: throw IllegalArgumentException("ไม่รู้จักผู้ให้บริการ API")
        fun selected(context: Context)=from(context.getSharedPreferences("story_provider",Context.MODE_PRIVATE).getString("selected","groq")!!)
        fun hybrid(context: Context)=context.getSharedPreferences("story_provider",Context.MODE_PRIVATE).getBoolean("hybrid",false)
        fun enableHybrid(context: Context) { context.getSharedPreferences("story_provider",Context.MODE_PRIVATE).edit().putBoolean("hybrid",true).apply() }
        fun label(context: Context)="API อัตโนมัติ"
        val hybridProviders=listOf(GROQ,GEMINI)
        val activeProviders=listOf(GROQ,GEMINI,OPENROUTER,COHERE)
        fun freeOnly(context: Context)=false
        fun setFreeOnly(context: Context,enabled: Boolean) { context.getSharedPreferences("story_provider",Context.MODE_PRIVATE).edit().putBoolean("free_only",enabled).apply() }
        fun allowedIds(context: Context): Set<String> = context.getSharedPreferences("story_provider",Context.MODE_PRIVATE)
            .getStringSet("allowed_providers",activeProviders.map { it.id }.toSet())?.toSet().orEmpty()
        fun setAllowed(context: Context,providers: Set<String>) {
            require(providers.isNotEmpty() && providers.all { id -> activeProviders.any { it.id==id } }) { "เลือก API อย่างน้อยหนึ่งเจ้า" }
            context.getSharedPreferences("story_provider",Context.MODE_PRIVATE).edit().putStringSet("allowed_providers",providers.toSet()).apply()
        }
        fun eligible(context: Context)=activeProviders.filter { it.id in allowedIds(context) && (!freeOnly(context) || it==OPENROUTER) }
        fun viable(providers: Collection<StoryProvider>): Boolean {
            val available=providers.filter { it in activeProviders }.toSet()
            return available.any { it in setOf(OPENROUTER,COHERE) } ||
                (available.size>=2 && (GROQ in available || GEMINI in available))
        }
        fun ready(context: Context)=viable(eligible(context).filter { it.hasKey(context) })
        private fun testFingerprint(context: Context,provider: StoryProvider): String {
            val data=provider.key(context)+"\n"+provider.model(context)
            return java.security.MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
        fun recordTest(context: Context,provider: StoryProvider,passed: Boolean,reason: String="") {
            if(!provider.hasKey(context)) return
            val prefs=context.getSharedPreferences("story_api_test",Context.MODE_PRIVATE)
            prefs.edit().putString(provider.id+"_fingerprint",testFingerprint(context,provider))
                .putString(provider.id+"_result",if(passed) "ผ่าน" else "ไม่ผ่าน")
                .putString(provider.id+"_reason",if(passed) "" else reason.take(500))
                .putLong(provider.id+"_time",System.currentTimeMillis()).apply()
        }
        fun lastTest(context: Context,provider: StoryProvider): String {
            if(!provider.hasKey(context)) return "ยังไม่มีคีย์"
            val prefs=context.getSharedPreferences("story_api_test",Context.MODE_PRIVATE)
            if(runCatching { testFingerprint(context,provider) }.getOrNull()!=prefs.getString(provider.id+"_fingerprint",null)) return "ยังไม่ได้ทดสอบคีย์/โมเดลนี้"
            val time=prefs.getLong(provider.id+"_time",0L)
            if(time<=0L) return "ยังไม่ได้ทดสอบคีย์/โมเดลนี้"
            val stamp=java.text.SimpleDateFormat("d MMM yyyy 'เวลา' HH.mm 'น.'",java.util.Locale("th","TH")).format(java.util.Date(time))
            return "[$stamp] ${prefs.getString(provider.id+"_result", "ไม่ทราบ")}"
        }
        fun configure(context: Context,spec: JSONObject): JSONObject {
            spec.put("provider",GROQ.id).put("model",GROQ.model(context)).put("hybrid",false).put("auto_pipeline",true)
            spec.put("free_only",freeOnly(context))
            entries.forEach { spec.remove("${it.id}_model") }
            eligible(context).filter { it.hasKey(context) }.forEach { spec.put("${it.id}_model",it.model(context)) }
            return spec
        }
        fun select(context: Context,provider: StoryProvider) { require(provider in activeProviders) { "API นี้ไม่เปิดใช้งานแล้ว" }; context.getSharedPreferences("story_provider",Context.MODE_PRIVATE).edit().putString("selected",provider.id).putBoolean("hybrid",false).apply() }
    }
}
internal interface StoryTextClient {
    fun generate(prompt: String,schema: JSONObject): JSONObject
    fun cancel()
    fun useProposalTimeouts() {}
    fun availableDelayMillis(): Long = 0L
}
