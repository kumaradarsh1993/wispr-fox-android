package com.wisprfox.android.settings

/**
 * Floating-avatar size preset (P-3), ported from the desktop S/M/L scale
 * (`lib/floater-scale.svelte.ts`). The [multiplier] scales the overlay avatar's
 * footprint; MEDIUM (1.0) is the current default size.
 */
enum class AvatarScale(val multiplier: Float, val label: String) {
    SMALL(0.8f, "S"),
    MEDIUM(1.0f, "M"),
    LARGE(1.25f, "L"),
}
