package com.pearparinya.automovie

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Nine unbound image API slots. No network calls are enabled until providers are integrated. */
internal class ImageApiKeySettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("director_image_api_slots", Context.MODE_PRIVATE)
    private fun checkSlot(slot: Int) { require(slot in 1..9) { "Invalid image API slot" } }
    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("director_image_api_slots_aes", null) as? SecretKey)
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(
                    "director_image_api_slots_aes",
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
    }
    fun hasKey(slot: Int): Boolean = key(slot).isNotBlank()
    fun key(slot: Int): String {
        checkSlot(slot)
        return runCatching {
            val data = prefs.getString("slot_${slot}_key", null) ?: return ""
            val iv = prefs.getString("slot_${slot}_iv", null) ?: return ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrDefault("")
    }
    fun save(slot: Int, value: String) {
        checkSlot(slot)
        val key = value.trim()
        require(key.length in 8..4096 && key.none { it.isWhitespace() }) { "Invalid API key format" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        val encrypted = Base64.encodeToString(cipher.doFinal(key.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        check(prefs.edit().putString("slot_${slot}_key", encrypted)
            .putString("slot_${slot}_iv", iv).commit()) { "Could not save API key" }
    }
    fun clear(slot: Int) {
        checkSlot(slot)
        check(prefs.edit().remove("slot_${slot}_key").remove("slot_${slot}_iv").commit())
    }
}
