package com.wisprfox.android.overlay

import com.wisprfox.android.core.PipelineState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers RC-1.1: the overlay must NOT be pinned always-visible when a11y is off.
 * The old bug was "fox sticks around with no text box in sight"; these lock in
 * the corrected decision table.
 */
class OverlayVisibilityTest {

    private val now = 100_000L

    @Test fun busyAlwaysShowsRegardlessOfA11y() {
        for (p in listOf(
            PipelineState.RECORDING,
            PipelineState.TRANSCRIBING,
            PipelineState.CLEANING,
            PipelineState.INJECTING,
        )) {
            assertTrue(
                "busy state $p should show even with a11y off + no interaction",
                OverlayVisibility.shouldShow(
                    pipeline = p,
                    a11yConnected = false,
                    keyboardVisible = false,
                    nowMs = now,
                    lastInteractionMs = 0L,
                ),
            )
        }
    }

    @Test fun a11yOnTracksKeyboard() {
        assertTrue(
            OverlayVisibility.shouldShow(
                pipeline = PipelineState.IDLE,
                a11yConnected = true,
                keyboardVisible = true,
                nowMs = now,
                lastInteractionMs = 0L,
            ),
        )
        assertFalse(
            OverlayVisibility.shouldShow(
                pipeline = PipelineState.IDLE,
                a11yConnected = true,
                keyboardVisible = false,
                nowMs = now,
                lastInteractionMs = 0L,
            ),
        )
    }

    @Test fun a11yOffNotPinnedWhenIdleAndNoInteraction() {
        // THE regression guard: a11y off + idle + stale interaction → hidden.
        assertFalse(
            OverlayVisibility.shouldShow(
                pipeline = PipelineState.IDLE,
                a11yConnected = false,
                keyboardVisible = false,
                nowMs = now,
                lastInteractionMs = now - OverlayVisibility.INTERACTION_GRACE_MS - 1,
            ),
        )
    }

    @Test fun a11yOffShowsWithinGraceWindow() {
        assertTrue(
            OverlayVisibility.shouldShow(
                pipeline = PipelineState.IDLE,
                a11yConnected = false,
                keyboardVisible = false,
                nowMs = now,
                lastInteractionMs = now - (OverlayVisibility.INTERACTION_GRACE_MS / 2),
            ),
        )
    }

    @Test fun a11yOffGraceExpiresExactlyAtBoundary() {
        // At exactly the boundary it's still within [0..GRACE] → visible.
        assertTrue(
            OverlayVisibility.shouldShow(
                pipeline = PipelineState.IDLE,
                a11yConnected = false,
                keyboardVisible = false,
                nowMs = now,
                lastInteractionMs = now - OverlayVisibility.INTERACTION_GRACE_MS,
            ),
        )
        // One ms past → hidden.
        assertFalse(
            OverlayVisibility.shouldShow(
                pipeline = PipelineState.IDLE,
                a11yConnected = false,
                keyboardVisible = false,
                nowMs = now,
                lastInteractionMs = now - OverlayVisibility.INTERACTION_GRACE_MS - 1,
            ),
        )
    }

    @Test fun a11yOffKeyboardFlagIgnored() {
        // When a11y is off the keyboardVisible flag is meaningless and must not
        // be trusted (it could be stale-true). Only busy/grace matter.
        assertFalse(
            OverlayVisibility.shouldShow(
                pipeline = PipelineState.IDLE,
                a11yConnected = false,
                keyboardVisible = true,
                nowMs = now,
                lastInteractionMs = 0L,
            ),
        )
    }

    @Test fun doneStateWithA11yOffHidesWhenGraceLapsed() {
        // DONE isn't "busy" — after the grace window the fox should hide.
        assertFalse(
            OverlayVisibility.shouldShow(
                pipeline = PipelineState.DONE,
                a11yConnected = false,
                keyboardVisible = false,
                nowMs = now,
                lastInteractionMs = 0L,
            ),
        )
    }
}
