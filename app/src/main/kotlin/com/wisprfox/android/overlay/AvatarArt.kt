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
 * Oru & Gujia raster pack (P-3) — same 8-state assets as the desktop sibling,
 * mapped per the manifest → PipelineState table in the audit:
 *   idle→IDLE, listening→RECORDING, thinking→TRANSCRIBING, writing→CLEANING,
 *   pasting→INJECTING, excited→DONE, error→ERROR. (sleeping is unused for now.)
 */
@DrawableRes
fun oruGujiaFor(state: PipelineState): Int = when (state) {
    PipelineState.IDLE -> R.drawable.oru_gujia_idle
    PipelineState.RECORDING -> R.drawable.oru_gujia_listening
    PipelineState.TRANSCRIBING -> R.drawable.oru_gujia_thinking
    PipelineState.CLEANING -> R.drawable.oru_gujia_writing
    PipelineState.INJECTING -> R.drawable.oru_gujia_pasting
    PipelineState.DONE -> R.drawable.oru_gujia_excited
    PipelineState.ERROR -> R.drawable.oru_gujia_error
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

/** Desktop-style cheeky commentary while listening, keyed on seconds elapsed.
 *  Thresholds kept generous — the owner dictates long, so the jokes don't fire
 *  until you've genuinely been going a while. */
fun listeningLabel(seconds: Int): String = when {
    seconds < 35 -> "listening…"
    seconds < 75 -> "still with you…"
    seconds < 130 -> "wow, lots to say"
    seconds < 210 -> "how long is this going to go?"
    seconds < 300 -> "okay, I'll keep waiting…"
    else -> "marathon mode 🏃"
}
