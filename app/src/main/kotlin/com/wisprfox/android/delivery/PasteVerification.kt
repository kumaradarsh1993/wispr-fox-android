package com.wisprfox.android.delivery

/**
 * Pure "did the text actually land?" rule (P0-4 #1 in
 * `docs/AUDIT_2026-07-17_ANDROID.md`), extracted from
 * [WisprFoxAccessibilityService] so it can be unit-tested without a live
 * accessibility service — same pattern as [DeliveryDecision].
 *
 * Why this exists: `AccessibilityNodeInfo.performAction` returning `true` only means
 * the target view *handled* the action, never that the result is what we wanted. A
 * WebView-backed field, a custom InputConnection, or a Compose BasicTextField in
 * certain states can accept a paste and then no-op. The old code treated that `true`
 * as proof, which stopped the retry loop, skipped the clipboard notification, and told
 * the user "Pasted" over an empty field — the exact "sometimes it pastes, sometimes it
 * doesn't, with no error" signature.
 *
 * [Outcome.UNKNOWN] is deliberately NOT folded into either certainty. Treating it as
 * failure and retrying would paste twice into fields we simply can't read back;
 * treating it as success would resurrect the lie we're fixing. The caller stops trying
 * and falls back to clipboard-plus-notification, which is wrong-but-harmless.
 */
object PasteVerification {

    enum class Outcome {
        /** The field demonstrably contains our text now. */
        LANDED,

        /** The field is readable and our text is demonstrably absent — the action no-opped. */
        NOT_LANDED,

        /** The field can't be read back, or changed into something we didn't ask for. */
        UNKNOWN,
    }

    /**
     * @param before the field's real text (hint stripped) immediately before the action.
     * @param after the field's real text after refresh(), or null when the node
     *        refused to refresh or exposes no text at all.
     * @param inserted the text we asked the field to take.
     */
    fun verify(before: String, after: String?, inserted: String): Outcome = when {
        // Nothing to look for; nothing could have gone wrong.
        inserted.isEmpty() -> Outcome.LANDED

        // Node is gone, detached, or simply doesn't publish text (some WebView and
        // password-ish fields). We genuinely cannot tell.
        after == null -> Outcome.UNKNOWN

        after.contains(inserted) -> Outcome.LANDED

        // Byte-for-byte unchanged after the settle window → the action was accepted
        // and did nothing. This is the case worth retrying.
        after == before -> Outcome.NOT_LANDED

        // It changed, and it grew by at least our payload: some apps re-wrap, trim, or
        // autocorrect what they take, so an exact `contains` can miss. The growth is
        // still evidence that our text is what went in.
        after.length >= before.length + inserted.length -> Outcome.LANDED

        // Changed into something shorter/other than what we sent — someone else is
        // editing this field, or the app rewrote our text. Don't guess.
        else -> Outcome.UNKNOWN
    }
}
