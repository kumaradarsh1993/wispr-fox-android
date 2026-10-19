package com.wisprfox.android.queue

import com.wisprfox.android.core.MissingKeyException
import com.wisprfox.android.provider.LlmError
import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.provider.SttError

/** Transient (network/timeout/5xx/429) → worth a WorkManager retry; anything else is surfaced. */
internal fun SttError.isTransient(): Boolean = when (this) {
    is SttError.Network, is SttError.Timeout -> true
    is SttError.Http -> status in 500..599 || status == 429
    else -> false
}

internal fun LlmError.isTransient(): Boolean = when (this) {
    is LlmError.Network, is LlmError.Timeout -> true
    is LlmError.Http -> status in 500..599 || status == 429
    else -> false
}

internal fun friendlySttError(e: SttError, provider: String): String {
    val label = ProviderCatalog.label(provider)
    return when (e) {
        is SttError.Http -> when (e.status) {
            401, 403 -> "$label key rejected - check Settings."
            413 -> "Recording too large to transcribe."
            429 -> "$label rate limit - wait a minute and retry from History."
            in 500..599 -> "$label had a server hiccup - retry from History."
            else -> "Transcription failed with $label (HTTP ${e.status})."
        }
        is SttError.FileTooLarge -> "Recording too large to transcribe."
        is SttError.Network -> "Transcription failed with $label - check your connection."
        is SttError.Timeout -> "Transcription took too long with $label - retry from History."
        else -> "Transcription failed with $label."
    }
}

internal fun friendlyLlmError(e: LlmError, provider: String): String {
    val label = ProviderCatalog.label(provider)
    return when (e) {
        is LlmError.Http -> when (e.status) {
            401, 403 -> "$label key rejected - check Settings."
            429 -> "$label rate limit (HTTP 429) - try again in a minute."
            in 500..599 -> "$label had a server hiccup (HTTP ${e.status})."
            else -> "$label returned HTTP ${e.status}."
        }
        is LlmError.Network -> "$label couldn't be reached - check your connection."
        is LlmError.Timeout -> "$label took too long to answer."
        is LlmError.Decode -> "$label returned something unreadable."
    }
}

/** A message plus whether retrying later could help. */
internal data class PassFailure(val message: String, val transient: Boolean)

internal fun describeFailure(e: Throwable, sttProvider: String?, llmProvider: String?): PassFailure = when (e) {
    is MissingKeyException -> PassFailure(e.message ?: "API key missing - open Settings to add one.", false)
    is SttError -> PassFailure(friendlySttError(e, sttProvider.orEmpty()), e.isTransient())
    is LlmError -> PassFailure(friendlyLlmError(e, llmProvider.orEmpty()), e.isTransient())
    is PassException -> PassFailure(e.message ?: "Failed.", false)
    // Unknown failures (an unwrapped I/O error, a provider hiccup) keep the old
    // behaviour: worth a bounded retry.
    else -> PassFailure("Something went wrong - ${e.message ?: e.javaClass.simpleName}", true)
}

/** A pass failed for a reason retrying will not fix (no audio, nothing returned). */
internal class PassException(message: String) : Exception(message)
