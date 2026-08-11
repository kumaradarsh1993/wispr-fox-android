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
    PipelineState.IMPORTING -> R.drawable.fox_curious
    PipelineState.TRANSCRIBING -> R.drawable.fox_curious
    PipelineState.DIARIZING -> R.drawable.fox_curious
    PipelineState.CLEANING -> R.drawable.fox_curious
    PipelineState.DRAFTING -> R.drawable.fox_curious
    PipelineState.MEETING_NOTES -> R.drawable.fox_curious
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
    PipelineState.IMPORTING -> R.drawable.oru_gujia_thinking
    PipelineState.TRANSCRIBING -> R.drawable.oru_gujia_thinking
    PipelineState.DIARIZING -> R.drawable.oru_gujia_thinking
    PipelineState.CLEANING -> R.drawable.oru_gujia_writing
    PipelineState.DRAFTING -> R.drawable.oru_gujia_writing
    PipelineState.MEETING_NOTES -> R.drawable.oru_gujia_writing
    PipelineState.INJECTING -> R.drawable.oru_gujia_pasting
    PipelineState.DONE -> R.drawable.oru_gujia_excited
    PipelineState.ERROR -> R.drawable.oru_gujia_error
}

@DrawableRes
fun codexFoxFor(state: PipelineState): Int = when (state) {
    PipelineState.IDLE -> R.drawable.codex_fox_idle
    PipelineState.RECORDING -> R.drawable.codex_fox_listening
    PipelineState.IMPORTING -> R.drawable.codex_fox_thinking
    PipelineState.TRANSCRIBING -> R.drawable.codex_fox_thinking
    PipelineState.DIARIZING -> R.drawable.codex_fox_thinking
    PipelineState.CLEANING -> R.drawable.codex_fox_writing
    PipelineState.DRAFTING -> R.drawable.codex_fox_writing
    PipelineState.MEETING_NOTES -> R.drawable.codex_fox_writing
    PipelineState.INJECTING -> R.drawable.codex_fox_pasting
    PipelineState.DONE -> R.drawable.codex_fox_excited
    PipelineState.ERROR -> R.drawable.codex_fox_error
}

@DrawableRes
fun sparkBuddyFor(state: PipelineState): Int = when (state) {
    PipelineState.IDLE -> R.drawable.spark_buddy_idle
    PipelineState.RECORDING -> R.drawable.spark_buddy_listening
    PipelineState.IMPORTING -> R.drawable.spark_buddy_thinking
    PipelineState.TRANSCRIBING -> R.drawable.spark_buddy_thinking
    PipelineState.DIARIZING -> R.drawable.spark_buddy_thinking
    PipelineState.CLEANING -> R.drawable.spark_buddy_writing
    PipelineState.DRAFTING -> R.drawable.spark_buddy_writing
    PipelineState.MEETING_NOTES -> R.drawable.spark_buddy_writing
    PipelineState.INJECTING -> R.drawable.spark_buddy_pasting
    PipelineState.DONE -> R.drawable.spark_buddy_excited
    PipelineState.ERROR -> R.drawable.spark_buddy_error
}

/**
 * Short status label for non-recording stages. The RECORDING bubble is handled
 * in [AvatarOverlay] with a live ticker + cheeky commentary, so it's null here.
 */
fun bubbleLabel(state: PipelineState): String? = when (state) {
    PipelineState.IMPORTING -> "preparing audio…"
    PipelineState.DIARIZING -> "labeling speakers…"
    PipelineState.DRAFTING -> "drafting…"
    PipelineState.MEETING_NOTES -> "meeting notes…"
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
