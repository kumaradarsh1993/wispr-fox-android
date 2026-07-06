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
        /** Editable target package captured at recording start, for paste safety. */
        val targetPackage: String? = null,
        /** Transient bubble/toast text (e.g. "Copied to clipboard"). null = none. */
        val message: String? = null,
        val messageIsError: Boolean = false,
        /**
         * True when the soft keyboard / an editable field is in focus, as
         * reported by the AccessibilityService. Drives the avatar's
         * appear-with-keyboard / hide-on-dismiss behaviour. Only meaningful when
         * [a11yConnected] is true — when accessibility is OFF we can't detect
         * the keyboard, so the overlay uses an interaction grace window instead
         * of pinning the fox always-visible (RC-1.1).
         */
        val keyboardVisible: Boolean = false,
        /**
         * True while the AccessibilityService is connected. Exposed so the
         * overlay and in-app screens can decide behaviour (keyboard-driven vs
         * grace-window) and surface a passive "enable accessibility" nudge
         * without hard-coupling to the service singleton.
         */
        val a11yConnected: Boolean = false,
        /**
         * Monotonic (SystemClock.elapsedRealtime) timestamp of the last user
         * interaction with the floating fox. Drives the short grace window that
         * keeps the fox on screen just after a tap/drag when a11y is OFF, so it
         * doesn't vanish instantly yet doesn't linger forever (RC-1.1).
         */
        val lastInteractionMs: Long = 0L,
        /**
         * True while a MainActivity is in the foreground. Lets delivery decide
         * whether a clipboard-only outcome needs a notification (RC-2.4): if the
         * app is already on screen, the in-app UI shows the result and a
         * notification would be noise.
         */
        val appForeground: Boolean = false,
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun update(block: Snapshot.() -> Snapshot) = _state.update { it.block() }

    fun setPipeline(p: PipelineState) = update { copy(pipeline = p) }

    fun setMode(m: DictationMode) = update { copy(mode = m) }

    fun setMetrics(elapsedMs: Long, totalBytes: Long) =
        update { copy(elapsedMs = elapsedMs, totalBytes = totalBytes) }

    fun setKeyboardVisible(visible: Boolean) = update { copy(keyboardVisible = visible) }

    fun setA11yConnected(connected: Boolean) = update { copy(a11yConnected = connected) }

    fun setAppForeground(foreground: Boolean) = update { copy(appForeground = foreground) }

    /** Record a user tap/drag on the fox (monotonic ms) for the grace window. */
    fun markInteraction(nowMs: Long) = update { copy(lastInteractionMs = nowMs) }

    fun toast(text: String, isError: Boolean = false) =
        update { copy(message = text, messageIsError = isError) }

    fun clearMessage() = update { copy(message = null) }

    fun reset() = update { Snapshot() }
}
