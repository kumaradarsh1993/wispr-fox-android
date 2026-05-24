package com.wisprfox.android.provider

/**
 * Provider abstractions + error taxonomy, ported from the desktop sibling's
 * `stt/mod.rs` and `llm/mod.rs`. Errors are modelled as a sealed hierarchy so
 * the orchestrator and the queue worker can branch on failure kind (auth vs
 * rate-limit vs network vs timeout) to decide retry vs surface-to-user.
 */

// ─── LLM ──────────────────────────────────────────────────────────────────

sealed class LlmError(message: String) : Exception(message) {
    data class Http(val status: Int, val body: String) : LlmError("HTTP $status: $body")
    data class Network(val detail: String) : LlmError("network: $detail")
    data class Decode(val detail: String) : LlmError("decode: $detail")
    data object Timeout : LlmError("timeout")
}

interface LlmProvider {
    val name: String

    /** One-shot completion. Throws [LlmError] on any failure. */
    suspend fun complete(system: String, user: String, temperature: Float): String
}

// ─── STT ──────────────────────────────────────────────────────────────────

data class Transcript(
    val text: String,
    val language: String?,
    val durationSeconds: Double?,
)

sealed class SttError(message: String) : Exception(message) {
    data class Http(val status: Int, val body: String) : SttError("HTTP $status: $body")
    data class Network(val detail: String) : SttError("network: $detail")
    data class Decode(val detail: String) : SttError("decode: $detail")
    data class FileTooLarge(val bytes: Long, val max: Long) : SttError("file too large: $bytes > $max")
    data class Io(val detail: String) : SttError("io: $detail")
    data object Timeout : SttError("timeout")
}

interface SttProvider {
    val name: String

    /**
     * Transcribe a WAV file. [hintLang] should normally be null so Whisper
     * auto-detects — the user code-switches English ↔ Hindi (locked decision
     * ported from desktop). Throws [SttError] on failure.
     */
    suspend fun transcribe(wavPath: String, hintLang: String?): Transcript
}
