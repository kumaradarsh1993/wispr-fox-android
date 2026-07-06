package com.wisprfox.android.delivery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers RC-1.2: an IME window merely being listed isn't enough — OneUI keeps a
 * zero/minimal-height IME window around after dismissal, which left the fox
 * lingering. Real height is required.
 */
class KeyboardHeuristicsTest {

    @Test fun noImeWindowMeansHidden() {
        assertFalse(KeyboardHeuristics.isKeyboardVisible(emptyList()))
    }

    @Test fun zeroHeightImeMeansHidden() {
        // The Samsung ghost-IME case.
        assertFalse(KeyboardHeuristics.isKeyboardVisible(listOf(0)))
    }

    @Test fun minimalHeightBelowThresholdMeansHidden() {
        assertFalse(KeyboardHeuristics.isKeyboardVisible(listOf(KeyboardHeuristics.MIN_IME_HEIGHT_PX)))
        assertFalse(KeyboardHeuristics.isKeyboardVisible(listOf(KeyboardHeuristics.MIN_IME_HEIGHT_PX - 1)))
    }

    @Test fun realKeyboardHeightMeansVisible() {
        assertTrue(KeyboardHeuristics.isKeyboardVisible(listOf(900)))
        assertTrue(KeyboardHeuristics.isKeyboardVisible(listOf(KeyboardHeuristics.MIN_IME_HEIGHT_PX + 1)))
    }

    @Test fun anyRealHeightAmongGhostsCounts() {
        assertTrue(KeyboardHeuristics.isKeyboardVisible(listOf(0, 5, 720)))
    }

    @Test fun allGhostsMeansHidden() {
        assertFalse(KeyboardHeuristics.isKeyboardVisible(listOf(0, 12, 40)))
    }
}
