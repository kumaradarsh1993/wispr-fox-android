package com.wisprfox.android.provider

/**
 * Shared provider/model catalogue for Settings, Home quick-pickers, and the
 * provider factory. Keep this small and conservative; the app should only list
 * models we know how to call from the Android client.
 */
object ProviderCatalog {
    const val STT_GROQ = "groq"
    const val STT_OPENAI = "openai"
    const val STT_DEEPGRAM = "deepgram"
    const val STT_ELEVENLABS = "elevenlabs"

    const val LLM_GROQ = "groq"
    const val LLM_OPENAI = "openai"
    const val LLM_GEMINI = "gemini"

    data class ProviderOption(val id: String, val label: String, val summary: String)
    data class ModelOption(val id: String, val label: String, val summary: String)

    val sttProviders = listOf(
        ProviderOption(STT_GROQ, "Groq", "Whisper transcription"),
        ProviderOption(STT_OPENAI, "OpenAI", "GPT transcription"),
        ProviderOption(STT_DEEPGRAM, "Deepgram", "Nova transcription"),
        ProviderOption(STT_ELEVENLABS, "ElevenLabs", "Scribe transcription"),
    )

    val llmProviders = listOf(
        ProviderOption(LLM_GROQ, "Groq", "GPT-OSS / Qwen cleanup"),
        ProviderOption(LLM_OPENAI, "OpenAI", "GPT cleanup"),
        ProviderOption(LLM_GEMINI, "Gemini", "Google cleanup"),
    )

    // Model lists verified live against provider docs on 2026-07-07 (see
    // docs/AUDIT_2026-07-06_FABLE.md Task 1). The first entry in each list is
    // the provider default; sanitize*Model() coerces removed/stale ids to it,
    // so old saved prefs self-heal after a model is retired.
    val sttModels: Map<String, List<ModelOption>> = mapOf(
        STT_GROQ to listOf(
            ModelOption("whisper-large-v3-turbo", "Whisper Turbo", "Fast default"),
            ModelOption("whisper-large-v3", "Whisper Large v3", "Higher accuracy"),
            // distil-whisper-large-v3-en removed 2026-07-07 — Groq no longer lists it.
        ),
        STT_OPENAI to listOf(
            ModelOption("gpt-transcribe", "GPT Transcribe", "Recommended for ordinary recordings"),
            ModelOption("gpt-4o-transcribe-diarize", "GPT-4o Diarize", "Speaker labels for meetings"),
            ModelOption("gpt-4o-transcribe", "GPT-4o Transcribe", "Best OpenAI STT quality"),
            ModelOption("gpt-4o-mini-transcribe", "GPT-4o mini", "Faster, lower-cost STT"),
            ModelOption("whisper-1", "Whisper API", "Legacy fallback"),
        ),
        STT_DEEPGRAM to listOf(
            ModelOption("nova-3", "Nova-3", "Recommended Deepgram model"),
            ModelOption("nova-2", "Nova-2", "Stable fallback"),
        ),
        STT_ELEVENLABS to listOf(
            // scribe_v1 removed — ElevenLabs deletes it 2026-07-09. scribe_v2 only.
            ModelOption("scribe_v2", "Scribe v2", "Recommended ElevenLabs model"),
        ),
    )

    val llmModels: Map<String, List<ModelOption>> = mapOf(
        LLM_GROQ to listOf(
            ModelOption("openai/gpt-oss-20b", "GPT-OSS 20B", "Fast cleanup and titles"),
            ModelOption("openai/gpt-oss-120b", "GPT-OSS 120B", "Stronger drafts and meeting notes"),
            ModelOption("qwen/qwen3.6-27b", "Qwen 3.6 27B", "Strong multilingual alternative"),
        ),
        LLM_OPENAI to listOf(
            ModelOption("gpt-5.4-mini", "GPT-5.4 mini", "Fast OpenAI cleanup"),
            ModelOption("gpt-5.4", "GPT-5.4", "Higher-quality cleanup"),
            ModelOption("gpt-5.5", "GPT-5.5", "Frontier cleanup"),
        ),
        LLM_GEMINI to listOf(
            ModelOption("gemini-3.6-flash", "Gemini 3.6 Flash", "Latest stable balance model"),
            ModelOption("gemini-3.5-flash", "Gemini 3.5 Flash", "Stable balance fallback"),
            ModelOption("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Fast current stable model"),
            ModelOption("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite", "Stable low-cost fallback"),
            ModelOption("gemini-2.5-flash", "Gemini 2.5 Flash", "Fast Gemini"),
            ModelOption("gemini-2.5-flash-lite", "Gemini Flash-Lite", "Lowest latency"),
            ModelOption("gemini-2.5-pro", "Gemini 2.5 Pro", "Paid-tier quality"),
            ModelOption("gemini-3.1-pro-preview", "Gemini 3.1 Pro", "Preview"),
        ),
    )

    fun sttModelsFor(provider: String): List<ModelOption> =
        sttModels[provider] ?: sttModels.getValue(STT_GROQ)

    fun llmModelsFor(provider: String): List<ModelOption> =
        llmModels[provider] ?: llmModels.getValue(LLM_GROQ)

    fun defaultSttModel(provider: String): String = sttModelsFor(provider).first().id
    fun defaultLlmModel(provider: String): String = llmModelsFor(provider).first().id

    /** A deliberately stronger default for Draft and Meeting Notes. */
    fun defaultStrongLlmModel(provider: String): String = when (provider) {
        LLM_GROQ -> "openai/gpt-oss-120b"
        LLM_OPENAI -> "gpt-5.5"
        LLM_GEMINI -> "gemini-3.6-flash"
        else -> defaultLlmModel(provider)
    }

    // Android currently normalises diarized output through Deepgram's utterance
    // response. Other diarization-capable catalog entries remain selectable for
    // ordinary STT until their response adapters preserve stable speaker ids.
    fun supportsDiarization(provider: String, model: String): Boolean =
        provider == STT_DEEPGRAM && model in setOf("nova-3", "nova-2")

    /** Android's safest long-meeting fallback; Deepgram can diarize long files. */
    fun compatibleDiarizationSelection(provider: String, model: String): Pair<String, String> =
        if (supportsDiarization(provider, model)) provider to model
        else STT_DEEPGRAM to "nova-3"

    fun sanitizeSttProvider(provider: String): String =
        if (sttProviders.any { it.id == provider }) provider else STT_GROQ

    fun sanitizeLlmProvider(provider: String): String =
        if (llmProviders.any { it.id == provider }) provider else LLM_GROQ

    fun sanitizeSttModel(provider: String, model: String): String {
        val options = sttModelsFor(provider)
        return options.firstOrNull { it.id == model }?.id ?: options.first().id
    }

    fun sanitizeLlmModel(provider: String, model: String): String {
        val options = llmModelsFor(provider)
        if (provider == LLM_GEMINI && model in deprecatedGeminiModels) return options.first().id
        return options.firstOrNull { it.id == model }?.id ?: options.first().id
    }

    fun label(provider: String): String =
        (sttProviders + llmProviders).firstOrNull { it.id == provider }?.label ?: provider

    fun shortModel(model: String): String = when {
        model.startsWith("whisper-large-v3-turbo") -> "Whisper Turbo"
        model.startsWith("whisper-large-v3") -> "Whisper v3"
        model == "gpt-transcribe" -> "GPT Transcribe"
        model.startsWith("gpt-4o-transcribe-diarize") -> "4o Diarize"
        model.startsWith("gpt-4o-mini-transcribe") -> "4o mini STT"
        model.startsWith("gpt-4o-transcribe") -> "4o STT"
        model.startsWith("whisper-1") -> "Whisper API"
        model.startsWith("nova-3") -> "Nova-3"
        model.startsWith("nova-2") -> "Nova-2"
        model.startsWith("scribe_v2") -> "Scribe v2"
        model == "openai/gpt-oss-20b" -> "GPT-OSS 20B"
        model == "openai/gpt-oss-120b" -> "GPT-OSS 120B"
        model == "qwen/qwen3.6-27b" -> "Qwen 3.6 27B"
        model.startsWith("gpt-5.4-mini") -> "GPT-5.4 mini"
        model.startsWith("gpt-5.4") -> "GPT-5.4"
        model.startsWith("gpt-5.5") -> "GPT-5.5"
        model.startsWith("gemini-3.6-flash") -> "Gemini 3.6 Flash"
        model.startsWith("gemini-3.5-flash-lite") -> "Gemini 3.5 Flash-Lite"
        model.startsWith("gemini-3.5-flash") -> "Gemini 3.5 Flash"
        model.startsWith("gemini-3.1-pro") -> "Gemini 3.1 Pro"
        model.startsWith("gemini-2.5-flash-lite") -> "Gemini Flash-Lite"
        model.startsWith("gemini-2.5-flash") -> "Gemini Flash"
        model.startsWith("gemini-2.5-pro") -> "Gemini Pro"
        else -> model.replace('-', ' ').replace('_', ' ')
    }

    private val deprecatedGeminiModels = setOf(
        "gemini-2.0-flash",
        "gemini-2.0-flash-001",
        "gemini-2.0-flash-lite",
        "gemini-2.0-flash-lite-001",
    )
}
