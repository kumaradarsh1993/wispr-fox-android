package com.wisprfox.android.core

import com.wisprfox.android.provider.DictationMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The pipeline stages — the Android analog of the desktop `wispr:state` events
 * that drive the Clippy floater (`clippy/+page.svelte` `mapFlow`). The avatar
 * maps these to its visual states:
 *   RECORDING → listening · TRANSCRIBING/CLEANING → thinking/writing ·
 *   INJECTING/DONE → success · ERROR → error art.
 */
enum class PipelineState { IDLE, RECORDING, TRANSCRIBING, CLEANING, INJECTING, DONE, ERROR }

/**
 * Process-wide UI state shared by the overlay avatar, the Quick Settings tile,
 * the in-app screens, and the background [com.wisprfox.android.queue.TranscribeWorker]
 * (which runs in the same process and updates this so the avatar animates
 * through the pipeline). The durable source of truth is still the Room row's
 * `status`; this is the live/animation channel.
 */
object AppState {
    data class Snapshot(
        val pipeline: PipelineState = PipelineState.IDLE,
        val mode: DictationMode = DictationMode.CLEANED,
        val elapsedMs: Long = 0,
        val totalBytes: Long = 0,
        val activeRecordingId: String? = null,
        /** Transient bubble/toast text (e.g. "Copied to clipboard"). null = none. */
        val message: String? = null,
        val messageIsError: Boolean = false,
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun update(block: Snapshot.() -> Snapshot) = _state.update { it.block() }

    fun setPipeline(p: PipelineState) = update { copy(pipeline = p) }

    fun setMode(m: DictationMode) = update { copy(mode = m) }

    fun setMetrics(elapsedMs: Long, totalBytes: Long) =
        update { copy(elapsedMs = elapsedMs, totalBytes = totalBytes) }

    fun toast(text: String, isError: Boolean = false) =
        update { copy(message = text, messageIsError = isError) }

    fun clearMessage() = update { copy(message = null) }

    fun reset() = update { Snapshot() }
}
