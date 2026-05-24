package com.wisprfox.android.core

import com.wisprfox.android.provider.LlmProvider
import com.wisprfox.android.provider.SttProvider
import com.wisprfox.android.provider.gemini.GeminiClient
import com.wisprfox.android.provider.groq.GroqChatClient
import com.wisprfox.android.provider.groq.GroqWhisperClient
import com.wisprfox.android.settings.AppSettings
import com.wisprfox.android.settings.SecureKeyStore
import okhttp3.OkHttpClient

/** Thrown when a required BYOK key is absent. Surfaced as a friendly message. */
class MissingKeyException(val providerLabel: String) :
    Exception("API key missing for $providerLabel — open Settings to add one.")

/**
 * Builds STT/LLM provider clients from current settings + stored keys. Mirrors
 * the desktop `build_llm_provider` fallback: Gemini when selected (needs its
 * own key), otherwise Groq LLM (falling back to the Groq STT key if a separate
 * LLM key wasn't entered — they're often the same Groq key).
 */
class ProviderFactory(
    private val http: OkHttpClient,
    private val secrets: SecureKeyStore,
) {
    fun stt(model: String): SttProvider {
        val key = secrets.get(SecureKeyStore.Key.GroqStt)?.takeIf { it.isNotBlank() }
            ?: throw MissingKeyException("Groq (speech-to-text)")
        return GroqWhisperClient(http, key, model)
    }

    fun llm(settings: AppSettings): LlmProvider = when (settings.llmProvider) {
        AppSettings.PROVIDER_GEMINI -> {
            val key = secrets.get(SecureKeyStore.Key.GeminiLlm)?.takeIf { it.isNotBlank() }
                ?: throw MissingKeyException("Gemini")
            GeminiClient(http, key, settings.geminiModel)
        }
        else -> {
            val key = secrets.get(SecureKeyStore.Key.GroqLlm)?.takeIf { it.isNotBlank() }
                ?: secrets.get(SecureKeyStore.Key.GroqStt)?.takeIf { it.isNotBlank() }
                ?: throw MissingKeyException("Groq (LLM)")
            GroqChatClient(http, key, settings.groqLlmModel)
        }
    }
}
