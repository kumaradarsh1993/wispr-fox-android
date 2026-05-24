package com.wisprfox.android.provider

/**
 * The three gestures the user picks between (single-tap default + long-press
 * menu). Maps onto the desktop sibling's prompt set:
 *
 *  - [RAW]         → no LLM at all; the Whisper transcript is delivered as-is.
 *  - [CLEANED]     → desktop F8 "Light" cleaned-raw formatter (LIGHT_SYSTEM):
 *                    punctuation, sentence boundaries, light paragraphing,
 *                    voice-preserving. Output stored in `cleaned_text`.
 *  - [REFORMATTED] → desktop F10 "Drafting" (DRAFTING_SYSTEM): the spoken
 *                    words are a *brief*, and the LLM returns a polished
 *                    draft. Output stored in `drafted_text`.
 *
 * [ADVANCED] (desktop F9 strict copy-edit) is not on the Android menu but the
 * prompt is ported and selectable from Settings if the user ever wants it.
 */
enum class DictationMode {
    RAW,
    CLEANED,
    REFORMATTED,
    ADVANCED;

    /** Whether this mode runs the transcript through the cleanup LLM at all. */
    val usesLlm: Boolean get() = this != RAW

    /** User-facing label. Single source of truth: Raw / Clean / Draft. */
    val label: String
        get() = when (this) {
            RAW -> "Raw"
            CLEANED -> "Clean"
            ADVANCED -> "Advanced"
            REFORMATTED -> "Draft"
        }
}
