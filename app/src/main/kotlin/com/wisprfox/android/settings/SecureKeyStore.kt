package com.wisprfox.android.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed storage for the three BYOK API keys. Mirrors the desktop
 * sibling's keyring approach (separate entry per provider: groq_stt, groq_llm,
 * gemini_llm). The AES key lives in the AndroidKeyStore (never extractable);
 * ciphertext + IV are kept in a private SharedPreferences. Keys are only ever
 * decrypted in-process and sent to the provider whose key it is — never logged,
 * never to disk in plaintext.
 */
class SecureKeyStore(context: Context) {

    enum class Key(val pref: String) {
        GroqStt("groq_stt"),
        GroqLlm("groq_llm"),
        OpenAiStt("openai_stt"),
        OpenAiLlm("openai_llm"),
        DeepgramStt("deepgram_stt"),
        ElevenLabsStt("elevenlabs_stt"),
        GeminiLlm("gemini_llm"),
    }

    private val prefs = context.applicationContext
        .getSharedPreferences("wisprfox_secrets", Context.MODE_PRIVATE)

    fun get(key: Key): String? {
        val stored = prefs.getString(key.pref, null) ?: return null
        return runCatching { decrypt(stored) }.getOrNull()
    }

    fun put(key: Key, value: String) {
        prefs.edit().putString(key.pref, encrypt(value)).apply()
    }

    fun clear(key: Key) {
        prefs.edit().remove(key.pref).apply()
    }

    fun has(key: Key): Boolean = !get(key).isNullOrBlank()

    // ─── crypto ──────────────────────────────────────────────────────────

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val (ivB64, ctB64) = stored.split(":", limit = 2)
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val ct = Base64.decode(ctB64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "wisprfox_secrets_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
