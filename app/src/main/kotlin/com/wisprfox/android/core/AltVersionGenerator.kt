package com.wisprfox.android.core

import com.wisprfox.android.history.AltKind
import com.wisprfox.android.provider.CleanupOrchestrator
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.provider.Prompts
import com.wisprfox.android.queue.withLlmSelection

/**
 * Generate a Cleaned or Reformatted version of an existing recording on demand
 * (from the History/Library screen), reusing the already-stored raw transcript.
 * Port of the desktop `generate_alt_version`. The result is persisted to the
 * matching column so it shows under the right tab next time too.
 */
class AltVersionGenerator(private val container: AppContainer) {

    /** @param kind which output to (re)generate. Returns the generated text. */
    suspend fun generate(
        recordingId: String,
        kind: AltKind,
        providerOverride: String? = null,
        modelOverride: String? = null,
    ): String {
        val rec = container.recordings.get(recordingId)
            ?: throw IllegalStateException("recording not found")
        val transcript = rec.transcript?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No transcript yet — retry the recording first.")

        val baseSettings = container.currentSettings()
        val settings = if (providerOverride != null && modelOverride != null) {
            baseSettings.withLlmSelection(providerOverride, modelOverride)
        } else if (kind == AltKind.DRAFTED || kind == AltKind.MEETING_NOTES) {
            baseSettings.withLlmSelection(baseSettings.strongProvider, baseSettings.strongModel)
        } else baseSettings
        if (kind == AltKind.MEETING_NOTES) {
            val llm = container.providerFactory.llm(settings)
            val text = llm.complete(
                Prompts.MEETING_NOTES_SYSTEM,
                "<transcript>$transcript</transcript>",
                0.2f,
            ).trim()
            container.recordings.setAlt(recordingId, kind, text, llm.name, true, null)
            return text
        }
        val mode = if (kind == AltKind.DRAFTED) DictationMode.REFORMATTED else DictationMode.CLEANED
        val llm = container.providerFactory.llm(settings)

        val result = CleanupOrchestrator().clean(
            raw = transcript,
            mode = mode,
            systemOverride = null,
            contextHint = null,
            provider = llm,
        )
        container.recordings.setAlt(recordingId, kind, result.text, llm.name, result.usedLlm, result.note)
        return result.text
    }
}
