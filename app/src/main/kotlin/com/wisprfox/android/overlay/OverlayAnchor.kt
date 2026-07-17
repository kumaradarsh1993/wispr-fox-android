package com.wisprfox.android.overlay

/**
 * Pure anchor maths for the floating fox (P0-2 in `docs/AUDIT_2026-07-17_ANDROID.md`).
 * Extracted from [OverlayService] for the same reason [OverlayVisibility] was: the
 * rule is fiddly, easy to regress, and impossible to eyeball from a screenshot.
 *
 * The overlay window is WRAP_CONTENT and anchored `Gravity.BOTTOM or Gravity.START`,
 * so `params.x` pins the window's LEFT EDGE while the window's width is that of its
 * widest child. The fox is centre-aligned in that column, so anything wider than the
 * fox — the long-press menu, the status bubble, and the bubble's live label as its
 * text grows — used to shove the fox rightward. That is why the anchor persisted here
 * is the fox's CENTRE, not the window's left edge: the left edge is derived from it on
 * every size change, which is what the avatar manifests' `"anchor": "bottom-center"`
 * always claimed to do.
 *
 * Widening the column to a fixed width would also "fix" the jump, but a permanently
 * wider overlay window swallows touches in its transparent region (Android delivers a
 * touch to the topmost window whose bounds contain it), breaking taps on the app
 * underneath. Hence: keep WRAP_CONTENT, move the window instead.
 */
object OverlayAnchor {

    /**
     * One-time migration of the legacy `bx` pref (distance from the left screen edge
     * to the window, i.e. to the fox's left edge when only the fox was showing) into
     * a fox-centre anchor. Keeps the fox exactly where the user last left it.
     */
    fun migrateCenterX(legacyLeftX: Int, foxWidthPx: Int): Int = legacyLeftX + foxWidthPx / 2

    /**
     * Clamp the anchor — not the window — so the FOX stays fully on screen. Clamping
     * the window's left edge instead would drag the fox sideways exactly when a wide
     * bubble appears near a screen edge, reintroducing the jump we're fixing. The
     * window itself is free to overhang; FLAG_LAYOUT_NO_LIMITS allows it and only the
     * bubble's far edge gets clipped.
     */
    fun clampCenterX(foxCenterX: Int, foxWidthPx: Int, screenWidthPx: Int): Int {
        val half = foxWidthPx / 2
        val max = screenWidthPx - half
        // Degenerate (fox wider than the screen): centre it rather than throw.
        if (max <= half) return screenWidthPx / 2
        return foxCenterX.coerceIn(half, max)
    }

    /**
     * The window's left edge for a given content width, such that the centre-aligned
     * fox lands on [foxCenterX]. Independent of the fox's own width: Compose centres
     * the fox in the column, so the fox's centre IS the column's centre.
     */
    fun windowLeftX(foxCenterX: Int, contentWidthPx: Int): Int = foxCenterX - contentWidthPx / 2
}
