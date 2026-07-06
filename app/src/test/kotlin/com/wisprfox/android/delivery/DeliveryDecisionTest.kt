package com.wisprfox.android.delivery

import com.wisprfox.android.delivery.DeliveryDecision.Action
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers RC-2.3: the cross-app paste guard. The key behaviour is that a NULL
 * current-focus read is NOT a mismatch (focus is often transitioning at
 * delivery time — the retry loop resolves it), while a KNOWN-DIFFERENT package
 * always falls back to clipboard.
 */
class DeliveryDecisionTest {

    private fun decide(
        autoPaste: Boolean = true,
        a11y: Boolean = true,
        expected: String? = "com.whatsapp",
        current: String? = "com.whatsapp",
    ) = DeliveryDecision.decide(autoPaste, a11y, expected, current)

    @Test fun autoPasteOffIsClipboardOnly() {
        assertEquals(Action.CLIPBOARD_ONLY, decide(autoPaste = false))
    }

    @Test fun a11yOffIsClipboardOnly() {
        assertEquals(Action.CLIPBOARD_ONLY, decide(a11y = false))
    }

    @Test fun samePackageAttemptsPaste() {
        assertEquals(Action.ATTEMPT_PASTE, decide(expected = "com.whatsapp", current = "com.whatsapp"))
    }

    @Test fun differentPackageIsClipboardOnly() {
        assertEquals(Action.CLIPBOARD_ONLY, decide(expected = "com.whatsapp", current = "com.chrome"))
    }

    @Test fun nullCurrentIsNotAMismatch() {
        // Focus transitioning at delivery time — let the retry loop try.
        assertEquals(Action.ATTEMPT_PASTE, decide(expected = "com.whatsapp", current = null))
    }

    @Test fun nullExpectedDoesNotBlock() {
        // Dictation started with nothing focused (e.g. from the app itself).
        // We don't have a package to guard against, so paste is allowed.
        assertEquals(Action.ATTEMPT_PASTE, decide(expected = null, current = "com.whatsapp"))
    }

    @Test fun bothNullAttemptsPaste() {
        assertEquals(Action.ATTEMPT_PASTE, decide(expected = null, current = null))
    }
}
