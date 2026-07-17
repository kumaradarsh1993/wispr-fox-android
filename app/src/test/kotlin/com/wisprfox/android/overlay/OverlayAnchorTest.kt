package com.wisprfox.android.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers P0-2: the fox must not move horizontally when the overlay window widens
 * around it. The window is WRAP_CONTENT with Gravity.START, so its left edge is
 * pinned at `x` and the centred fox slid right by half of any growth — +11dp for the
 * menu at M, up to +62dp for a long bubble, and continuously as the live label grew.
 *
 * Densities: S23 Ultra is ~3.0x at its default DPI, so 1dp ≈ 3px below.
 */
class OverlayAnchorTest {

    private companion object {
        const val DENSITY = 3
        fun dp(v: Int) = v * DENSITY

        // AvatarOverlay: FOX_SIZE = 70dp * the AvatarScale multiplier.
        val FOX_S = dp(56)      // 70 * 0.8
        val FOX_M = dp(70)      // 70 * 1.0
        val FOX_L = (70 * 1.25 * DENSITY).toInt()  // 262px
        val MENU = dp(92)       // MENU_WIDTH, wider than the fox at every scale
        val SCREEN = 1440       // S23 Ultra portrait
    }

    /** The fox's centre, given where the window's left edge ended up. */
    private fun foxCentre(windowX: Int, contentWidth: Int) = windowX + contentWidth / 2

    // ── The bug, restated as a test of the fix ─────────────────────────────────

    @Test fun foxDoesNotMoveWhenTheMenuOpens() {
        val centre = 500
        val closed = OverlayAnchor.windowLeftX(centre, FOX_M)
        val open = OverlayAnchor.windowLeftX(centre, MENU)
        assertEquals(centre, foxCentre(closed, FOX_M))
        assertEquals(centre, foxCentre(open, MENU))
        // The window itself must move left to compensate — that's the whole mechanism.
        assertEquals((MENU - FOX_M) / 2, closed - open)
    }

    @Test fun foxDoesNotDriftAsTheLiveLabelGrows() {
        val centre = 500
        // listeningLabel() steps through progressively longer strings mid-dictation;
        // the bubble Surface wraps its Row with no width constraint, so the column
        // width tracks the text. Each of these is a distinct measured width.
        val bubbleWidths = listOf(dp(102), dp(140), dp(194), dp(160), dp(102))
        bubbleWidths.forEach { w ->
            assertEquals(centre, foxCentre(OverlayAnchor.windowLeftX(centre, w), w))
        }
    }

    @Test fun foxIsPinnedAtEveryAvatarScale() {
        // Scale-dependence was the confirming signal for the diagnosis: +18dp at S,
        // +11dp at M, +2.25dp at L. All three must now be zero.
        listOf(FOX_S, FOX_M, FOX_L).forEach { fox ->
            val centre = 500
            val alone = OverlayAnchor.windowLeftX(centre, fox)
            val withMenu = OverlayAnchor.windowLeftX(centre, MENU)
            assertEquals(centre, foxCentre(alone, fox))
            assertEquals(centre, foxCentre(withMenu, MENU))
        }
    }

    @Test fun windowMayOverhangTheLeftEdgeSoTheFoxStaysStill() {
        // Fox parked near the left edge, wide bubble appears. Clamping the WINDOW to
        // x >= 0 here would shove the fox right — the exact bug. The window is allowed
        // to go negative (FLAG_LAYOUT_NO_LIMITS); only the bubble clips.
        val centre = OverlayAnchor.clampCenterX(FOX_M / 2, FOX_M, SCREEN)
        val x = OverlayAnchor.windowLeftX(centre, dp(194))
        assert(x < 0) { "expected the window to overhang, got x=$x" }
        assertEquals(centre, foxCentre(x, dp(194)))
    }

    // ── Anchor clamping ───────────────────────────────────────────────────────

    @Test fun clampKeepsTheFoxFullyOnScreen() {
        assertEquals(FOX_M / 2, OverlayAnchor.clampCenterX(-999, FOX_M, SCREEN))
        assertEquals(SCREEN - FOX_M / 2, OverlayAnchor.clampCenterX(9999, FOX_M, SCREEN))
    }

    @Test fun clampLeavesAnInBoundsAnchorAlone() {
        assertEquals(700, OverlayAnchor.clampCenterX(700, FOX_M, SCREEN))
    }

    @Test fun clampDoesNotThrowWhenTheFoxIsWiderThanTheScreen() {
        // coerceIn(min, max) throws when min > max; a silly-narrow screen must not
        // crash the overlay service.
        assertEquals(50, OverlayAnchor.clampCenterX(9999, 400, 100))
    }

    // ── Legacy pref migration ─────────────────────────────────────────────────

    @Test fun migrationKeepsTheFoxWhereTheUserLeftIt() {
        // Pre-fix, `bx` was the window's left edge, which with a fox-only column was
        // the fox's left edge. So the fox's centre was bx + foxWidth/2, and re-deriving
        // the window x from it must reproduce the original bx exactly.
        val legacyBx = 24
        val centre = OverlayAnchor.migrateCenterX(legacyBx, FOX_M)
        assertEquals(legacyBx + FOX_M / 2, centre)
        assertEquals(legacyBx, OverlayAnchor.windowLeftX(centre, FOX_M))
    }
}
