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
import kotlinx.coroutines.withContext

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
 *
 * Auto-paste hardening (`docs/AUDIT_2026-07-17_ANDROID.md`):
 *  - P0-4 #1: a paste is only reported as delivered once the text is read back out
 *    of the node. performAction()'s return value means "handled", not "worked".
 *  - P0-4 #2: all node access is marshalled onto the service's main thread.
 *  - P0-4 #3: ACTION_PASTE is preferred over ACTION_SET_TEXT so the app's
 *    InputConnection sees the edit.
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
     * The field's real text, with the hint stripped.
     *
     * An EMPTY field reports its placeholder/hint (e.g. WhatsApp's "Message") as
     * `node.text`. If we naively appended to that, the hint string leaked into the
     * output ("Messagehello…"). So we detect the hint via
     * [AccessibilityNodeInfo.isShowingHintText] / a text==hint match and treat the
     * field as empty.
     */
    private fun realText(node: AccessibilityNodeInfo): String {
        val rawText = node.text?.toString()
        val hint = node.hintText?.toString()
        val showingHint = node.isShowingHintText || (hint != null && rawText == hint)
        return if (showingHint || rawText == null) "" else rawText
    }

    /**
     * Try to insert [text] into the focused editable field.
     *
     * P0-4 #3: ACTION_PASTE is now preferred in BOTH branches. It goes through the
     * app's InputConnection, so IME/TextWatcher-driven UI wakes up — WhatsApp's and
     * Telegram's send buttons are the classic casualties of ACTION_SET_TEXT, which
     * writes the editable directly and leaves the app thinking nothing was typed.
     * SET_TEXT survives as the fallback for fields that don't advertise ACTION_PASTE
     * (and for a paste that verifiably no-opped).
     *
     * P0-4 #1: neither action's `true` return is trusted. It only means the view
     * handled the action; the text is only reported as delivered once we can read it
     * back out of the node. See [PasteVerification].
     *
     * Must run on the service's main thread — see [tryPaste].
     */
    private suspend fun pasteInternal(text: String, clipboardFallbackReady: Boolean): PasteResult {
        val node = focusedEditable() ?: return PasteResult.FAILED
        val before = realText(node)

        if (clipboardFallbackReady && node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_PASTE }) {
            if (runCatching { node.performAction(AccessibilityNodeInfo.ACTION_PASTE) }.getOrDefault(false)) {
                when (verifyLanded(node, before, text)) {
                    PasteVerification.Outcome.LANDED -> return PasteResult.LANDED
                    PasteVerification.Outcome.UNKNOWN -> return PasteResult.UNVERIFIED
                    // Accepted and did nothing — the SET_TEXT path is exactly what
                    // this fallback is for.
                    PasteVerification.Outcome.NOT_LANDED -> Unit
                }
            }
        }

        // Append rather than replace: `before` is hint-stripped, so an empty field
        // gets just our text and a half-typed one keeps what's there.
        val combined = before + text
        val setArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, combined)
        }
        if (runCatching { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs) }.getOrDefault(false)) {
            setCursorEnd(node, combined.length)
            return when (verifyLanded(node, before, text)) {
                PasteVerification.Outcome.LANDED -> PasteResult.LANDED
                PasteVerification.Outcome.NOT_LANDED -> PasteResult.FAILED
                PasteVerification.Outcome.UNKNOWN -> PasteResult.UNVERIFIED
            }
        }

        return PasteResult.FAILED
    }

    /**
     * P0-4 #1: read the text back out of the node to see whether the action actually
     * did anything.
     *
     * performAction is a synchronous IPC, but the app applies the edit on its own UI
     * thread afterwards, so an immediate refresh() reads stale text. Poll instead: a
     * false "not landed" here is worse than a slow one, because the caller's answer to
     * NOT_LANDED is to write the text again.
     */
    private suspend fun verifyLanded(
        node: AccessibilityNodeInfo,
        before: String,
        inserted: String,
    ): PasteVerification.Outcome {
        var outcome = PasteVerification.Outcome.UNKNOWN
        repeat(VERIFY_READS) {
            delay(VERIFY_SETTLE_MS)
            val after = runCatching { if (node.refresh()) realText(node) else null }.getOrNull()
            outcome = PasteVerification.verify(before, after, inserted)
            if (outcome == PasteVerification.Outcome.LANDED) return outcome
        }
        return outcome
    }

    private fun setCursorEnd(node: AccessibilityNodeInfo, end: Int) {
        val selArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, end)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
    }

    /** Outcome of one auto-paste attempt. See [PasteVerification] for why this is a
     *  tri-state rather than a Boolean (P0-4 #1). */
    enum class PasteResult {
        /** The text is verifiably in the field. Safe to report "Pasted". */
        LANDED,

        /** No field, or the action was rejected / verifiably no-opped. Retrying is safe. */
        FAILED,

        /** The action may or may not have landed and we can't read the field back.
         *  Retrying could double-paste, so the caller must stop and fall back. */
        UNVERIFIED,
    }

    companion object {
        private const val KEYBOARD_RECHECK_MS = 600L

        /** P0-4 #1: up to 3 × 60ms ≈ 180ms for the app's UI thread to apply the edit
         *  before we conclude the action no-opped. */
        private const val VERIFY_SETTLE_MS = 60L
        private const val VERIFY_READS = 3

        @Volatile
        private var instance: WisprFoxAccessibilityService? = null

        /** Whether the service is currently connected (user enabled it). */
        fun isConnected(): Boolean = instance != null

        /**
         * Package name of the currently focused editable field, when known.
         *
         * P0-4 #2: node reads MUST happen on the service's main thread. The framework's
         * AccessibilityInteractionClient caches per-thread and refreshes its
         * window/node snapshots on the service's main looper, so an off-thread
         * findFocus/getWindows can see stale or empty state non-deterministically —
         * and every caller of this used to be on Dispatchers.Default or a WorkManager
         * executor.
         */
        suspend fun currentEditablePackage(): String? = withContext(Dispatchers.Main) {
            instance?.focusedEditable()?.packageName?.toString()
        }

        /**
         * Attempt auto-paste from any context. [clipboardFallbackReady] should
         * be true if the caller has already put [text] on the clipboard so the
         * ACTION_PASTE path works. Each call re-reads focus fresh, so the
         * caller's retry loop (RC-2.2) can resolve a transitioning focus.
         *
         * P0-4 #2: hops to the service's main thread — see [currentEditablePackage].
         */
        suspend fun tryPaste(text: String, clipboardFallbackReady: Boolean): PasteResult =
            withContext(Dispatchers.Main) {
                instance?.pasteInternal(text, clipboardFallbackReady) ?: PasteResult.FAILED
            }
    }
}
