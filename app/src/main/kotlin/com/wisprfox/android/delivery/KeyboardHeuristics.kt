package com.wisprfox.android.delivery

/**
 * Pure heuristics for deciding whether the soft keyboard is actually on screen,
 * extracted from [WisprFoxAccessibilityService] so the (Samsung-quirk-driven)
 * logic can be unit-tested without a device. See RC-1.2 in
 * `docs/AUDIT_2026-07-06_FABLE.md`.
 *
 * The naive rule "an IME window exists in the window list" over-reports on
 * OneUI: Samsung's keyboard can keep a zero/minimal-height IME window listed
 * even when the keyboard is dismissed, which left the fox lingering. So we also
 * require the IME window's on-screen bounds to have real height.
 */
object KeyboardHeuristics {

    /**
     * Minimum on-screen height (px) for an IME window to count as "visible".
     * A dismissed-but-listed OneUI IME window reports ~0 height; a real
     * keyboard is hundreds of px tall even on dense displays, so ~100px is a
     * safe floor that holds across API 31–35 densities.
     */
    const val MIN_IME_HEIGHT_PX = 100

    /**
     * @param imeWindowHeights on-screen bounds heights (px) of every window
     *        reported with type == TYPE_INPUT_METHOD. Empty when no IME window
     *        is listed at all.
     */
    fun isKeyboardVisible(imeWindowHeights: List<Int>): Boolean =
        imeWindowHeights.any { it > MIN_IME_HEIGHT_PX }
}
