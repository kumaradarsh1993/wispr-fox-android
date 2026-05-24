package com.wisprfox.android.delivery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Delivers the final text to the user. Two channels (matching the desktop
 * inject dispatcher's SendInput→clipboard fallback):
 *
 *  1. [Channel.ACCESSIBILITY] — auto-paste into the focused field via
 *     [WisprFoxAccessibilityService]. The "it just appears in the box" path.
 *  2. [Channel.CLIPBOARD] — always also placed on the clipboard, so even when
 *     auto-paste is off / no field is focused, the user just long-presses paste.
 */
class DeliveryManager(context: Context) {

    private val appContext = context.applicationContext

    enum class Channel { ACCESSIBILITY, CLIPBOARD }

    /**
     * Always copies to clipboard first (so the ACTION_PASTE fallback works and
     * the user always has the text), then tries auto-paste when [autoPaste] is
     * on and the accessibility service is connected.
     */
    fun deliver(text: String, autoPaste: Boolean): Channel {
        copyToClipboard(text)
        if (autoPaste && WisprFoxAccessibilityService.isConnected()) {
            if (WisprFoxAccessibilityService.tryPaste(text, clipboardFallbackReady = true)) {
                return Channel.ACCESSIBILITY
            }
        }
        return Channel.CLIPBOARD
    }

    fun copyToClipboard(text: String) {
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("wispr-fox", text))
    }
}
