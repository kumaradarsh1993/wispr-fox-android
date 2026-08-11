package com.wisprfox.android.core

import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.settings.AppSettings
import com.wisprfox.android.settings.SecureKeyStore

/**
 * Single source of truth for BYOK fallback rules. UI readiness checks and
 * [ProviderFactory] must resolve credentials through this class so a request
 * that the UI approves cannot later fail merely because the two disagreed.
 */
class ProviderKeyReadiness internal constructor(
    private val read: (SecureKeyStore.Key) -> String?,
) {
    constructor(secrets: SecureKeyStore) : this(secrets::get)

    data class Resolution(
        val providerLabel: String,
        val value: String?,
    ) {
        val ready: Boolean get() = !value.isNullOrBlank()
    }

    fun stt(provider: String): Resolution = when (provider) {
        ProviderCatalog.STT_OPENAI -> resolve(
            "OpenAI (speech-to-text)",
            SecureKeyStore.Key.OpenAiStt,
            SecureKeyStore.Key.OpenAiLlm,
        )
        ProviderCatalog.STT_DEEPGRAM -> resolve("Deepgram", SecureKeyStore.Key.DeepgramStt)
        ProviderCatalog.STT_ELEVENLABS -> resolve("ElevenLabs", SecureKeyStore.Key.ElevenLabsStt)
        else -> resolve("Groq (speech-to-text)", SecureKeyStore.Key.GroqStt)
    }

    fun llm(provider: String): Resolution = when (provider) {
        AppSettings.PROVIDER_GEMINI -> resolve("Gemini", SecureKeyStore.Key.GeminiLlm)
        AppSettings.PROVIDER_OPENAI -> resolve(
            "OpenAI",
            SecureKeyStore.Key.OpenAiLlm,
            SecureKeyStore.Key.OpenAiStt,
        )
        else -> resolve(
            "Groq (LLM)",
            SecureKeyStore.Key.GroqLlm,
            SecureKeyStore.Key.GroqStt,
        )
    }

    private fun resolve(label: String, vararg candidates: SecureKeyStore.Key): Resolution =
        Resolution(label, candidates.firstNotNullOfOrNull { read(it)?.takeIf(String::isNotBlank) })
}
