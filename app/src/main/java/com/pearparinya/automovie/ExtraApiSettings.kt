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

internal enum class ExtraApiProvider(val id: String, val label: String) {
    CEREBRAS("cerebras", "Cerebras"),
    DEEPSEEK("deepseek", "DeepSeek"),
    OPENROUTER("openrouter", "OpenRouter")
}

/** Keys stay on this device, encrypted by a non-exportable Android Keystore key. */
internal class ExtraApiSettings(context: Context, private val provider: ExtraApiProvider) {
    private val file = File(context.noBackupFilesDir, "${provider.id}/settings.json")
    private val alias = "director_extra_${provider.id}"

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun key(): String {
        if (!file.exists()) return ""
        val obj = GeminiStoryFiles.read(file)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, Base64.decode(obj.getString("iv"), Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(obj.getString("key"), Base64.NO_WRAP)), Charsets.UTF_8)
    }

    fun hasKey(): Boolean = runCatching { key().isNotBlank() }.getOrDefault(false)

    fun save(value: String) {
        val key = value.trim()
        require(key.length in 12..1024 && key.none { it.isWhitespace() }) { "ตรวจ API Key อีกครั้ง" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        GeminiStoryFiles.write(file, JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("key", Base64.encodeToString(cipher.doFinal(key.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)))
    }

    fun clear() { AtomicFile(file).delete() }
}
