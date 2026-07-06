package com.wisprfox.android.delivery

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.AppState
import com.wisprfox.android.overlay.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Opt-in (default-on, since we sideload) auto-paste. When connected, it can
 * drop the final text straight into whatever editable field currently has
 * input focus — this is what makes "tap, speak, the words appear in the box"
 * literally true rather than "the words are on your clipboard".
 *
 * The service does nothing autonomous on content: it only writes text when
 * [tryPaste] is called by our own pipeline. What it DOES observe passively is
 * keyboard visibility, so the floating fox can appear with the keyboard.
 *
 * Reliability hardening (see `docs/AUDIT_2026-07-06_FABLE.md`):
 *  - RC-1.2: keyboard detection also requires the IME window to have real
 *    on-screen height, plus a debounced re-check so missed transitions
 *    self-heal, plus a reset on connect/unbind so stale state can't outlive us.
 *  - RC-1.4: also (re)start the OverlayService on connect, so the fox survives
 *    process death / reboot once accessibility is on.
 *  - RC-2.1: focus lookup uses the service-level findFocus across all windows,
 *    not the flaky rootInActiveWindow.
 *  - RC-2.2: the paste itself is retried by [DeliveryManager]; each attempt
 *    re-reads focus fresh here.
 */
class WisprFoxAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var debounceJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AppState.setA11yConnected(true)
        // RC-1.2: clear any stale keyboard state left over from a previous
        // service lifetime so the fox doesn't start out wrongly pinned.
        AppState.setKeyboardVisible(false)
        // RC-1.4: the enabled a11y service keeps our process alive and is the
        // natural companion signal, so (re)start the overlay here — this is
        // what makes the fox survive reboot / process death once a11y is on.
        // Guarded by the same conditions MainActivity uses.
        startOverlayIfAllowed()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        AppState.setA11yConnected(false)
        // RC-1.2: keyboard signal is gone; don't let a stale true linger.
        AppState.setKeyboardVisible(false)
        debounceJob?.cancel()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        AppState.setA11yConnected(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * We never act autonomously on content. The only thing we observe is
     * whether the soft keyboard is currently on screen, so the floating avatar
     * can appear with the keyboard and vanish when it's dismissed.
     *
     * RC-1.2: recompute immediately, then schedule a single debounced re-check
     * ~600ms later. Accessibility events are queued/coalesced by the framework,
     * so a keyboard transition can leave us with a stale reading; the delayed
     * re-check reconciles against the settled window state and self-heals.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        AppState.setKeyboardVisible(isKeyboardVisible())
        scheduleDebouncedRecheck()
    }

    override fun onInterrupt() {}

    private fun scheduleDebouncedRecheck() {
        debounceJob?.cancel()
        debounceJob = serviceScope.launch {
            delay(KEYBOARD_RECHECK_MS)
            AppState.setKeyboardVisible(isKeyboardVisible())
        }
    }

    private fun startOverlayIfAllowed() {
        runCatching {
            val container = WisprFoxApp.container(this)
            serviceScope.launch {
                val settings = container.currentSettings()
                if (Settings.canDrawOverlays(this@WisprFoxAccessibilityService) &&
                    settings.overlayBubbleEnabled
                ) {
                    startService(android.content.Intent(this@WisprFoxAccessibilityService, OverlayService::class.java))
                }
            }
        }
    }

    /**
     * RC-1.2: an IME window merely being *listed* isn't enough on OneUI — a
     * dismissed Samsung keyboard can keep a zero-height IME window in the list.
     * Require real on-screen height via [KeyboardHeuristics]. Bounds are read
     * with getBoundsInScreen, available on all of API 31–35.
     */
    private fun isKeyboardVisible(): Boolean =
        runCatching {
            val heights = windows
                ?.filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                ?.map { win ->
                    val r = Rect()
                    win.getBoundsInScreen(r)
                    r.height()
                }
                .orEmpty()
            KeyboardHeuristics.isKeyboardVisible(heights)
        }.getOrDefault(false)

    /**
     * RC-2.1: find the focused editable field robustly. `rootInActiveWindow` is
     * the wrong lens at delivery time — the "active" window is often the IME,
     * our own overlay, or momentarily null. The service-level
     * findFocus(FOCUS_INPUT) searches across all interactive windows; we fall
     * back to iterating window roots. We refresh() the node before trusting it
     * so its editable/text state is current.
     */
    private fun focusedEditable(): AccessibilityNodeInfo? {
        val found = runCatching { findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
            ?: runCatching {
                windows?.asSequence()
                    ?.mapNotNull { it.root }
                    ?.mapNotNull { root -> root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }
                    ?.firstOrNull()
            }.getOrNull()
            ?: return null
        runCatching { found.refresh() }
        return if (found.isEditable) found else null
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
        private const val KEYBOARD_RECHECK_MS = 600L

        @Volatile
        private var instance: WisprFoxAccessibilityService? = null

        /** Whether the service is currently connected (user enabled it). */
        fun isConnected(): Boolean = instance != null

        /** Package name of the currently focused editable field, when known. */
        fun currentEditablePackage(): String? =
            instance?.focusedEditable()?.packageName?.toString()

        /**
         * Attempt auto-paste from any context. [clipboardFallbackReady] should
         * be true if the caller has already put [text] on the clipboard so the
         * ACTION_PASTE fallback works. Each call re-reads focus fresh, so the
         * caller's retry loop (RC-2.2) can resolve a transitioning focus.
         */
        fun tryPaste(text: String, clipboardFallbackReady: Boolean): Boolean =
            instance?.pasteInternal(text, clipboardFallbackReady) ?: false
    }
}
