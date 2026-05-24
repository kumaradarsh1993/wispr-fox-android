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

/** Short status label shown in the avatar's speech bubble. */
fun bubbleLabel(state: PipelineState, elapsedMs: Long): String? = when (state) {
    PipelineState.IDLE -> null
    PipelineState.RECORDING -> {
        val s = elapsedMs / 1000
        "listening…  %d:%02d".format(s / 60, s % 60)
    }
    PipelineState.TRANSCRIBING -> "transcribing…"
    PipelineState.CLEANING -> "polishing…"
    PipelineState.INJECTING -> "delivering…"
    PipelineState.DONE -> "done!"
    PipelineState.ERROR -> "tap to retry"
}
