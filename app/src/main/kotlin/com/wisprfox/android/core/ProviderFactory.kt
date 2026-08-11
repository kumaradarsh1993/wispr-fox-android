package com.wisprfox.android.core

import com.wisprfox.android.provider.LlmProvider
import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.provider.SttProvider
import com.wisprfox.android.provider.deepgram.DeepgramSttClient
import com.wisprfox.android.provider.elevenlabs.ElevenLabsSttClient
import com.wisprfox.android.provider.gemini.GeminiClient
import com.wisprfox.android.provider.groq.GroqChatClient
import com.wisprfox.android.provider.groq.GroqWhisperClient
import com.wisprfox.android.provider.openai.OpenAiLlmClient
import com.wisprfox.android.provider.openai.OpenAiSttClient
import com.wisprfox.android.settings.AppSettings
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
    private val keys: ProviderKeyReadiness,
) {
    fun stt(settings: AppSettings): SttProvider = when (settings.sttProvider) {
        ProviderCatalog.STT_OPENAI -> {
            val resolution = keys.stt(settings.sttProvider)
            val key = resolution.value ?: throw MissingKeyException(resolution.providerLabel)
            OpenAiSttClient(http, key, ProviderCatalog.sanitizeSttModel(settings.sttProvider, settings.sttModel))
        }
        ProviderCatalog.STT_DEEPGRAM -> {
            val resolution = keys.stt(settings.sttProvider)
            val key = resolution.value ?: throw MissingKeyException(resolution.providerLabel)
            DeepgramSttClient(http, key, ProviderCatalog.sanitizeSttModel(settings.sttProvider, settings.sttModel))
        }
        ProviderCatalog.STT_ELEVENLABS -> {
            val resolution = keys.stt(settings.sttProvider)
            val key = resolution.value ?: throw MissingKeyException(resolution.providerLabel)
            ElevenLabsSttClient(http, key, ProviderCatalog.sanitizeSttModel(settings.sttProvider, settings.sttModel))
        }
        else -> {
            val resolution = keys.stt(settings.sttProvider)
            val key = resolution.value ?: throw MissingKeyException(resolution.providerLabel)
            GroqWhisperClient(http, key, ProviderCatalog.sanitizeSttModel(ProviderCatalog.STT_GROQ, settings.sttModel))
        }
    }

    fun llm(settings: AppSettings): LlmProvider = when (settings.llmProvider) {
        AppSettings.PROVIDER_GEMINI -> {
            val resolution = keys.llm(settings.llmProvider)
            val key = resolution.value ?: throw MissingKeyException(resolution.providerLabel)
            GeminiClient(http, key, ProviderCatalog.sanitizeLlmModel(settings.llmProvider, settings.geminiModel))
        }
        AppSettings.PROVIDER_OPENAI -> {
            val resolution = keys.llm(settings.llmProvider)
            val key = resolution.value ?: throw MissingKeyException(resolution.providerLabel)
            OpenAiLlmClient(http, key, ProviderCatalog.sanitizeLlmModel(settings.llmProvider, settings.openAiLlmModel))
        }
        else -> {
            val resolution = keys.llm(settings.llmProvider)
            val key = resolution.value ?: throw MissingKeyException(resolution.providerLabel)
            GroqChatClient(http, key, ProviderCatalog.sanitizeLlmModel(ProviderCatalog.LLM_GROQ, settings.groqLlmModel))
        }
    }
}
