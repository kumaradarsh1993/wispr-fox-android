package com.wisprfox.android.settings

import android.util.Base64
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * "Setup code" unlock for friends & family builds. The app ships an encrypted
 * blob (BuildConfig.FAMILY_BLOB) containing the owner's API keys. The blob is
 * AES-GCM-256, with the key derived from a passphrase via PBKDF2 — so the keys
 * are unreadable without the passphrase the owner shares out-of-band.
 *
 * Blob layout (base64): salt[16] || iv[12] || ciphertext+GCMtag.
 * Must stay in lockstep with tools/make_family_blob.py.
 */
object FamilyUnlock {

    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val GCM_TAG_BITS = 128

    data class Keys(val groq: String?, val gemini: String?)

    /** Returns the decrypted keys, or null if the passphrase/blob is wrong. */
    fun decrypt(blobBase64: String, passphrase: String): Keys? {
        if (blobBase64.isBlank() || passphrase.isBlank()) return null
        return try {
            val blob = Base64.decode(blobBase64, Base64.DEFAULT)
            if (blob.size < SALT_LEN + IV_LEN + 16) return null
            val salt = blob.copyOfRange(0, SALT_LEN)
            val iv = blob.copyOfRange(SALT_LEN, SALT_LEN + IV_LEN)
            val ct = blob.copyOfRange(SALT_LEN + IV_LEN, blob.size)

            val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS))
                .encoded
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            val json = JSONObject(String(cipher.doFinal(ct), Charsets.UTF_8))
            Keys(
                groq = json.optString("groq").ifBlank { null },
                gemini = json.optString("gemini").ifBlank { null },
            )
        } catch (e: Exception) {
            null // wrong passphrase → GCM tag mismatch → AEADBadTagException
        }
    }

    /** Apply unlocked keys to secure storage. Returns true if at least Groq landed. */
    fun apply(keys: Keys, secrets: SecureKeyStore): Boolean {
        keys.groq?.let { secrets.put(SecureKeyStore.Key.GroqStt, it) }
        keys.gemini?.let { secrets.put(SecureKeyStore.Key.GeminiLlm, it) }
        return keys.groq != null
    }
}
