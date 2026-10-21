package com.pearparinya.automovie

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Personal BYOK: key is entered on-device, encrypted by Android Keystore, excluded from backups. */
internal class GeminiSettings(context: Context,private val provider: StoryProvider=StoryProvider.GEMINI) {
    private val keyAlias="director_${provider.id}"
    private val folder=File(context.noBackupFilesDir,provider.id).apply { mkdirs() }
    private val file=File(folder,"settings.json")
    private fun secret(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(keyAlias,null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun model(): String = runCatching { JSONObject(file.readText()).optString("model",provider.defaultModel) }.getOrDefault(provider.defaultModel)
    fun key(): String {
        if(!file.exists()) return ""
        val obj=JSONObject(file.readText()); val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,secret(),GCMParameterSpec(128,Base64.decode(obj.getString("iv"),Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(obj.getString("key"),Base64.NO_WRAP)),Charsets.UTF_8)
    }
    fun hasKey(): Boolean = runCatching { key().isNotBlank() }.getOrDefault(false)
    fun save(key: String,model: String) {
        require(key.trim().length>=20 && key.none { it.isWhitespace() }) { "ตรวจ API Key อีกครั้ง" }
        require(if(provider==StoryProvider.GEMINI) Regex("gemini-[a-zA-Z0-9.-]+").matches(model) else model in GroqClient.MODELS) { "ชื่อโมเดลไม่ถูกต้อง" }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,secret())
        val obj=JSONObject().put("model",model).put("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
            .put("key",Base64.encodeToString(cipher.doFinal(key.trim().toByteArray(Charsets.UTF_8)),Base64.NO_WRAP))
        GeminiStoryFiles.write(file,obj)
    }
    fun clear() { AtomicFile(file).delete() }
    companion object { const val DEFAULT_MODEL="gemini-3.5-flash-lite" }
}

internal object GeminiStoryFiles {
    fun write(file: File,obj: JSONObject) {
        file.parentFile!!.mkdirs(); val atomic=AtomicFile(file); val stream=atomic.startWrite()
        try { stream.write(obj.toString(2).toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch(e: Exception) { atomic.failWrite(stream); throw e }
    }
    fun read(file: File): JSONObject = JSONObject(AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
}
