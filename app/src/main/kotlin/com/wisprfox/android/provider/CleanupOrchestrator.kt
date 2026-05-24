package com.wisprfox.android.provider

/**
 * Light/Cleaned/Reformatted cleanup orchestration. Ported from
 * `wispr-fox/src-tauri/src/clippy.rs`.
 *
 * Contract preserved from desktop:
 *  - CLEANED has a length-drift tripwire: if the cleaned output's char count
 *    differs from the input by more than [LIGHT_MAX_DRIFT], treat it as
 *    prompt-injection or hallucination and fall back to the raw transcript
 *    with note `light_length_drift`. (The desktop "pwned" smoke test exercises
 *    this — it is a SECURITY BOUNDARY, not cosmetic.)
 *  - Any provider failure (HTTP / decode / timeout) returns the raw transcript
 *    with a structured note, never an exception. The user always gets *their
 *    words back* even when the LLM is down.
 */
class CleanupOrchestrator {

    data class Result(
        val text: String,
        val usedLlm: Boolean,
        val note: String?,
    )

    /**
     * @param raw the Whisper transcript.
     * @param mode which prompt to apply. [DictationMode.RAW] short-circuits.
     * @param systemOverride optional user-customised system prompt; blank/null
     *        falls back to the baked-in default for [mode].
     * @param contextHint optional register hint prepended to the system prompt
     *        (REFORMATTED only on desktop). null skips augmentation.
     * @param provider the LLM client to call.
     */
    suspend fun clean(
        raw: String,
        mode: DictationMode,
        systemOverride: String?,
        contextHint: String?,
        provider: LlmProvider,
    ): Result {
        val rawTrimmed = raw.trim()
        if (rawTrimmed.isEmpty()) return Result("", usedLlm = false, note = null)
        if (mode == DictationMode.RAW) return Result(rawTrimmed, usedLlm = false, note = null)

        val (defaultSystem, user, temperature) = when (mode) {
            DictationMode.CLEANED ->
                Triple(Prompts.LIGHT_SYSTEM, Prompts.lightUserMessage(rawTrimmed), 0.2f)
            DictationMode.ADVANCED ->
                Triple(Prompts.ADVANCED_SYSTEM, rawTrimmed, 0.4f)
            DictationMode.REFORMATTED ->
                Triple(Prompts.DRAFTING_SYSTEM, rawTrimmed, 0.5f)
            DictationMode.RAW -> error("unreachable")
        }

        val baseSystem = systemOverride?.takeIf { it.trim().isNotEmpty() } ?: defaultSystem
        val system = if (contextHint != null) "$contextHint\n\n$baseSystem" else baseSystem

        return try {
            val out = provider.complete(system, user, temperature).trim()
            if (mode == DictationMode.CLEANED && lengthDrift(rawTrimmed, out) > LIGHT_MAX_DRIFT) {
                Result(rawTrimmed, usedLlm = false, note = "light_length_drift")
            } else {
                Result(out, usedLlm = true, note = null)
            }
        } catch (e: LlmError) {
            Result(rawTrimmed, usedLlm = false, note = noteFor(e))
        }
    }

    private fun noteFor(e: LlmError): String = when (e) {
        is LlmError.Http -> when (e.status) {
            401, 403 -> "clippy_auth"
            429 -> "clippy_rate_limited"
            in 500..599 -> "clippy_upstream"
            else -> "clippy_failed"
        }
        is LlmError.Timeout -> "clippy_timeout"
        else -> "clippy_failed"
    }

    companion object {
        /**
         * Drift threshold for CLEANED mode. The cleaned-raw prompt adds moderate
         * paragraphing/bullets without changing content, so some growth is
         * normal; 0.60 is the empirically-safe headroom from desktop. A 2× output
         * means the model invented content → discard, return raw.
         */
        const val LIGHT_MAX_DRIFT = 0.60f

        fun lengthDrift(a: String, b: String): Float {
            val la = a.length.toFloat()
            val lb = b.length.toFloat()
            if (la == 0f) return if (lb == 0f) 0f else 1f
            return kotlin.math.abs(lb - la) / la
        }
    }
}
