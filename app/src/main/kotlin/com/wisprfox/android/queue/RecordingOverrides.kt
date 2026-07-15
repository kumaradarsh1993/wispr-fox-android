package com.wisprfox.android.queue

import com.wisprfox.android.history.Recording
import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.settings.AppSettings

/**
 * Fold a recording's per-recording provider/model overrides onto the global
 * [AppSettings] the pipeline would otherwise use. Live dictation stores no
 * overrides, so this returns the receiver unchanged; an imported file stores the
 * models chosen on the import sheet, so its transcription/cleanup uses those
 * without ever touching the user's saved live-dictation defaults.
 *
 * Every override is passed through the [ProviderCatalog] sanitizers so a model
 * that's since been retired self-heals to the provider default (same guarantee
 * the settings layer gives).
 */
fun AppSettings.withRecordingOverrides(rec: Recording): AppSettings {
    var s = this
    rec.sttProviderOverride?.let { rawProvider ->
        val provider = ProviderCatalog.sanitizeSttProvider(rawProvider)
        val model = ProviderCatalog.sanitizeSttModel(
            provider,
            rec.sttModelOverride ?: ProviderCatalog.defaultSttModel(provider),
        )
        s = s.copy(sttProvider = provider, sttModel = model)
    }
    rec.llmProviderOverride?.let { rawProvider ->
        val provider = ProviderCatalog.sanitizeLlmProvider(rawProvider)
        val model = ProviderCatalog.sanitizeLlmModel(
            provider,
            rec.llmModelOverride ?: ProviderCatalog.defaultLlmModel(provider),
        )
        // AppSettings keys the active LLM model by provider (activeLlmModel), so
        // set the field matching the chosen provider and leave the others intact.
        s = s.copy(
            llmProvider = provider,
            groqLlmModel = if (provider == ProviderCatalog.LLM_GROQ) model else s.groqLlmModel,
            openAiLlmModel = if (provider == ProviderCatalog.LLM_OPENAI) model else s.openAiLlmModel,
            geminiModel = if (provider == ProviderCatalog.LLM_GEMINI) model else s.geminiModel,
        )
    }
    return s
}
