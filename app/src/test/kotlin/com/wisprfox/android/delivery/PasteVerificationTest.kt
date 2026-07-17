package com.wisprfox.android.delivery

import com.wisprfox.android.delivery.PasteVerification.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers P0-4 #1: performAction() returning true is not proof the text landed. The
 * headline case is [acceptedButNoOpIsNotLanded] — the field is readable, unchanged,
 * and the old code reported "Pasted" over it.
 */
class PasteVerificationTest {

    @Test fun emptyFieldTakesTheText() {
        assertEquals(Outcome.LANDED, PasteVerification.verify("", "hello world", "hello world"))
    }

    @Test fun textInsertedAtTheCursorOfAHalfTypedField() {
        assertEquals(Outcome.LANDED, PasteVerification.verify("hey ", "hey hello world", "hello world"))
    }

    @Test fun acceptedButNoOpIsNotLanded() {
        // The WebView / custom-InputConnection case: action accepted, nothing changed.
        assertEquals(Outcome.NOT_LANDED, PasteVerification.verify("hey ", "hey ", "hello world"))
    }

    @Test fun emptyFieldStillEmptyIsNotLanded() {
        assertEquals(Outcome.NOT_LANDED, PasteVerification.verify("", "", "hello world"))
    }

    @Test fun unreadableNodeIsUnknownNotFailure() {
        // Treating this as NOT_LANDED would retry and double-paste into a field we
        // simply can't read back.
        assertEquals(Outcome.UNKNOWN, PasteVerification.verify("", null, "hello world"))
        assertEquals(Outcome.UNKNOWN, PasteVerification.verify("hey ", null, "hello world"))
    }

    @Test fun appReformattedOurTextButItGrewByOurPayload() {
        // Some fields re-wrap or autocorrect what they take, so `contains` misses.
        // Growth of at least the payload is still evidence it went in.
        // before=4, inserted=11, after=21 >= 4+11 → LANDED.
        assertEquals(Outcome.LANDED, PasteVerification.verify("hey ", "hey Hello, world!!!!!", "hello world"))
    }

    @Test fun fieldChangedButNotByEnoughToBeOurTextIsUnknown() {
        // Something changed, but not into what we sent and not by our payload's worth.
        // Someone else is editing this field. Don't guess either way.
        assertEquals(Outcome.UNKNOWN, PasteVerification.verify("hey ", "yo", "hello world"))
    }

    @Test fun shrinkingFieldIsUnknown() {
        assertEquals(Outcome.UNKNOWN, PasteVerification.verify("hey there", "hey", "hello world"))
    }

    @Test fun emptyInsertIsVacuouslyLanded() {
        assertEquals(Outcome.LANDED, PasteVerification.verify("hey ", "hey ", ""))
    }

    @Test fun aShortMismatchIsNotMistakenForSuccess() {
        // Guards the `contains` from being loosened (e.g. into a case-insensitive
        // match): it would then pass on text the field never received verbatim.
        assertEquals(Outcome.UNKNOWN, PasteVerification.verify("", "HI", "hello"))
    }
}
