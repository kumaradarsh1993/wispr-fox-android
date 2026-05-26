package com.wisprfox.android.delivery

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.wisprfox.android.core.AppState

/**
 * Opt-in (default-on, since we sideload) auto-paste. When connected, it can
 * drop the final text straight into whatever editable field currently has
 * input focus — this is what makes "tap, speak, the words appear in the box"
 * literally true rather than "the words are on your clipboard".
 *
 * The service does nothing autonomous: it only acts when [pasteText] is called
 * by our own pipeline. It reads the focused node, sets its text (or appends at
 * the cursor), and falls back to ACTION_PASTE from the clipboard if direct set
 * isn't supported by the target app.
 */
class WisprFoxAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * We never act autonomously on content. The only thing we observe is
     * whether the soft keyboard is currently on screen, so the floating avatar
     * can appear with the keyboard and vanish when it's dismissed. We detect
     * the keyboard by looking for an input-method window in the window list
     * (requires flagRetrieveInteractiveWindows, set in the service config).
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        AppState.setKeyboardVisible(isKeyboardVisible())
    }

    override fun onInterrupt() {}

    private fun isKeyboardVisible(): Boolean =
        runCatching {
            windows?.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } == true
        }.getOrDefault(false)

    private fun focusedEditable(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        return if (focused != null && focused.isEditable) focused else null
    }

    /**
     * Try to insert [text] into the focused editable field. Returns true on
     * success.
     *
     * Key subtlety: an EMPTY field reports its placeholder/hint (e.g. WhatsApp's
     * "Message") as `node.text`. If we naively appended to that, the hint string
     * leaked into the output ("Messagehello…"). So we detect the hint via
     * [AccessibilityNodeInfo.isShowingHintText] / a text==hint match and treat
     * the field as empty.
     *
     * Empty field → ACTION_SET_TEXT with just our text. Non-empty → ACTION_PASTE
     * so we insert at the cursor without clobbering or reordering existing text.
     */
    private fun pasteInternal(text: String, clipboardFallbackReady: Boolean): Boolean {
        val node = focusedEditable() ?: return false

        val rawText = node.text?.toString()
        val hint = node.hintText?.toString()
        val showingHint = node.isShowingHintText || (hint != null && rawText == hint)
        val existing = if (showingHint || rawText == null) "" else rawText

        if (existing.isEmpty()) {
            val setArgs = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)) {
                setCursorEnd(node, text.length)
                return true
            }
        } else {
            // Insert at the cursor, preserving what's already typed.
            if (clipboardFallbackReady && node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                return true
            }
            // Fallback: append via SET_TEXT.
            val combined = existing + text
            val setArgs = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, combined)
            }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)) {
                setCursorEnd(node, combined.length)
                return true
            }
        }

        return clipboardFallbackReady && node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun setCursorEnd(node: AccessibilityNodeInfo, end: Int) {
        val selArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, end)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
    }

    companion object {
        @Volatile
        private var instance: WisprFoxAccessibilityService? = null

        /** Whether the service is currently connected (user enabled it). */
        fun isConnected(): Boolean = instance != null

        /**
         * Attempt auto-paste from any context. [clipboardFallbackReady] should
         * be true if the caller has already put [text] on the clipboard so the
         * ACTION_PASTE fallback works.
         */
        fun tryPaste(text: String, clipboardFallbackReady: Boolean): Boolean =
            instance?.pasteInternal(text, clipboardFallbackReady) ?: false
    }
}
