package com.wisprfox.android.overlay

import androidx.annotation.DrawableRes
import com.wisprfox.android.R
import com.wisprfox.android.core.PipelineState

/**
 * Maps a pipeline stage to the watercolor-fox frame, mirroring the desktop
 * floater's state→art mapping (`clippy/+page.svelte`):
 *   idle → sitting · recording → recording · transcribing/cleaning → curious ·
 *   injecting/done → success · error → error.
 */
@DrawableRes
fun avatarFor(state: PipelineState): Int = when (state) {
    PipelineState.IDLE -> R.drawable.fox_sitting
    PipelineState.RECORDING -> R.drawable.fox_recording
    PipelineState.TRANSCRIBING -> R.drawable.fox_curious
    PipelineState.CLEANING -> R.drawable.fox_curious
    PipelineState.INJECTING -> R.drawable.fox_success
    PipelineState.DONE -> R.drawable.fox_success
    PipelineState.ERROR -> R.drawable.fox_error
}

/**
 * Short status label for non-recording stages. The RECORDING bubble is handled
 * in [AvatarOverlay] with a live ticker + cheeky commentary, so it's null here.
 */
fun bubbleLabel(state: PipelineState): String? = when (state) {
    PipelineState.TRANSCRIBING -> "transcribing…"
    PipelineState.CLEANING -> "polishing…"
    PipelineState.INJECTING -> "delivering…"
    PipelineState.DONE -> "done!"
    PipelineState.ERROR -> "tap to retry"
    else -> null
}

/** Desktop-style cheeky commentary while listening, keyed on seconds elapsed. */
fun listeningLabel(seconds: Int): String = when {
    seconds < 12 -> "listening…"
    seconds < 25 -> "still listening…"
    seconds < 45 -> "wow, lots to say"
    seconds < 70 -> "how long is this?"
    seconds < 110 -> "okay, still here…"
    else -> "marathon mode"
}
