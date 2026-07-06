package com.wisprfox.android.delivery

/**
 * Pure decision table for the auto-paste-vs-clipboard branch, extracted from
 * [DeliveryManager] so the cross-app safety rule (RC-2.3) is unit-testable
 * without an accessibility service. See `docs/AUDIT_2026-07-06_FABLE.md`.
 *
 * The rule mirrors the desktop inject dispatcher: text always lands on the
 * clipboard first; auto-paste is only attempted when it's both enabled and
 * safe. "Safe" means we're confident we're pasting into the SAME app the user
 * dictated into — pasting a private message into whatever happens to be focused
 * now (a different chat, a search box) is the failure mode the package guard
 * exists to prevent.
 */
object DeliveryDecision {

    enum class Action {
        /** Don't try to paste; leave it on the clipboard only. */
        CLIPBOARD_ONLY,

        /** Attempt the accessibility paste retry loop. */
        ATTEMPT_PASTE,
    }

    /**
     * @param autoPasteEnabled user setting.
     * @param a11yConnected whether the AccessibilityService is connected.
     * @param expectedPackage editable package captured at recording start
     *        (null if we couldn't read it then — e.g. dictation started from
     *        the app itself with nothing focused).
     * @param currentPackage editable package focused right now at delivery time
     *        (null when focus is momentarily lost/transitioning).
     */
    fun decide(
        autoPasteEnabled: Boolean,
        a11yConnected: Boolean,
        expectedPackage: String?,
        currentPackage: String?,
    ): Action {
        if (!autoPasteEnabled || !a11yConnected) return Action.CLIPBOARD_ONLY

        // Cross-app mismatch → never paste (desktop-aligned safety rule). A null
        // current read is NOT treated as a mismatch here: focus is frequently
        // transitioning at delivery time, so we let the retry loop resolve it
        // (RC-2.3). Only a *known-different* package trips the guard.
        if (expectedPackage != null && currentPackage != null && currentPackage != expectedPackage) {
            return Action.CLIPBOARD_ONLY
        }
        return Action.ATTEMPT_PASTE
    }
}
