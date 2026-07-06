package com.wisprfox.android.overlay

import com.wisprfox.android.core.PipelineState

/**
 * Pure decision logic for when the floating fox should be on screen. Extracted
 * from [OverlayService] so the (fiddly, easy-to-regress) visibility rule can be
 * unit-tested without Robolectric or a live WindowManager. See RC-1.1 in
 * `docs/AUDIT_2026-07-06_FABLE.md`.
 *
 * The old rule pinned the fox always-visible whenever the AccessibilityService
 * was off — which reads to the user as the "fox sticks around with no text box
 * in sight" bug (it happens on every reinstall until a11y is re-enabled). The
 * corrected rule:
 *
 *  - While the pipeline is BUSY (recording / transcribing / cleaning /
 *    injecting) the fox is always shown so you can drag/scroll mid-dictation.
 *  - When a11y is ON, the fox tracks keyboard visibility (appear-with-keyboard,
 *    hide-on-dismiss) — the intended, desktop-like behaviour.
 *  - When a11y is OFF, we cannot see the keyboard, so we DON'T pin it forever.
 *    Instead we show it only for a short grace window after the user interacts
 *    with it (tap/drag), then let it hide again. The in-app Home screen already
 *    nudges the user to enable accessibility; the overlay must not turn into a
 *    permanent sticker.
 */
object OverlayVisibility {

    /** Grace window (ms) the fox stays visible after user interaction when a11y is off. */
    const val INTERACTION_GRACE_MS = 4_000L

    fun isBusy(pipeline: PipelineState): Boolean =
        pipeline == PipelineState.RECORDING ||
            pipeline == PipelineState.TRANSCRIBING ||
            pipeline == PipelineState.CLEANING ||
            pipeline == PipelineState.INJECTING

    /**
     * Decide whether the overlay should be visible right now.
     *
     * @param pipeline current pipeline state.
     * @param a11yConnected whether the AccessibilityService is connected.
     * @param keyboardVisible last-known keyboard visibility (only meaningful when a11y is on).
     * @param nowMs current monotonic time (SystemClock.elapsedRealtime in prod).
     * @param lastInteractionMs monotonic time of the last user interaction with the fox,
     *        or a value far in the past when there was none.
     */
    fun shouldShow(
        pipeline: PipelineState,
        a11yConnected: Boolean,
        keyboardVisible: Boolean,
        nowMs: Long,
        lastInteractionMs: Long,
    ): Boolean {
        if (isBusy(pipeline)) return true
        return if (a11yConnected) {
            keyboardVisible
        } else {
            // No keyboard signal available — only linger briefly after interaction.
            nowMs - lastInteractionMs in 0..INTERACTION_GRACE_MS
        }
    }
}
