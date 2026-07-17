package com.wisprfox.android.delivery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.core.app.NotificationCompat
import com.wisprfox.android.MainActivity
import kotlinx.coroutines.delay

/**
 * Delivers the final text to the user. Two channels (matching the desktop
 * inject dispatcher's SendInput→clipboard fallback):
 *
 *  1. [Channel.ACCESSIBILITY] — auto-paste into the focused field via
 *     [WisprFoxAccessibilityService]. The "it just appears in the box" path.
 *  2. [Channel.CLIPBOARD] — always also placed on the clipboard, so even when
 *     auto-paste is off / no field is focused, the user just long-presses paste.
 *
 * Reliability hardening (`docs/AUDIT_2026-07-06_FABLE.md`):
 *  - RC-2.2 / P0-4 #4: paste is retried on a ladder reaching ~1.4s before giving up,
 *    because focus is frequently transitioning at the instant delivery fires.
 *  - P0-4 #1: [Channel.ACCESSIBILITY] is only returned when the text was verified to
 *    have landed. It's what the user is shown as "Pasted", so it must not be a guess.
 *  - RC-2.3: the cross-app package guard lives in [DeliveryDecision]; a null
 *    current-focus read no longer trips it — the retry loop resolves it.
 *  - RC-2.4: when we fall back to clipboard-only and the app isn't foreground,
 *    post a notification so delivery never fails silently.
 */
class DeliveryManager(context: Context) {

    private val appContext = context.applicationContext

    enum class Channel { ACCESSIBILITY, CLIPBOARD }

    /**
     * Always copies to clipboard first (so the ACTION_PASTE fallback works and
     * the user always has the text), then tries auto-paste — with a short retry
     * loop — when it's enabled, connected, and safe.
     *
     * Suspends: the retry loop uses coroutine delays. Callers already run this
     * from a coroutine (the worker's [doWork]).
     */
    suspend fun deliver(text: String, autoPaste: Boolean, expectedPackage: String?): Channel {
        copyToClipboard(text)

        val decision = DeliveryDecision.decide(
            autoPasteEnabled = autoPaste,
            a11yConnected = WisprFoxAccessibilityService.isConnected(),
            expectedPackage = expectedPackage,
            currentPackage = WisprFoxAccessibilityService.currentEditablePackage(),
        )

        if (decision == DeliveryDecision.Action.ATTEMPT_PASTE) {
            if (attemptPasteWithRetry(text, expectedPackage)) {
                return Channel.ACCESSIBILITY
            }
        }

        // Clipboard-only outcome. RC-2.4: never fail silently — if the user
        // isn't looking at our app, they have no idea the text is ready.
        maybeNotifyClipboardOnly()
        return Channel.CLIPBOARD
    }

    /**
     * RC-2.2 + RC-2.3: retry the paste on the [PASTE_RETRY_DELAYS_MS] ladder. Between
     * attempts we re-read focus fresh: if focus was momentarily null/mismatched because
     * the user was switching back to the chat, a later attempt catches it.
     * Once a concrete DIFFERENT package is seen we stop — that's a genuine
     * cross-app mismatch, not a transient, and pasting would be unsafe.
     *
     * P0-4 #4: the first attempt now waits too. It used to fire the instant
     * [copyToClipboard] returned, with the recording UI still tearing down, the IME
     * re-attaching and the clipboard service round-trip still in flight — i.e. the
     * attempt most likely to be needed was the one given the least chance to work.
     */
    private suspend fun attemptPasteWithRetry(text: String, expectedPackage: String?): Boolean {
        for (delayMs in PASTE_RETRY_DELAYS_MS) {
            delay(delayMs)

            val currentPackage = WisprFoxAccessibilityService.currentEditablePackage()
            if (expectedPackage != null && currentPackage != null && currentPackage != expectedPackage) {
                // Focus has settled onto a different app — stop, fall to clipboard.
                return false
            }
            when (WisprFoxAccessibilityService.tryPaste(text, clipboardFallbackReady = true)) {
                WisprFoxAccessibilityService.PasteResult.LANDED -> return true
                // P0-4 #1: the action may have landed in a field we can't read back.
                // Retrying would risk pasting twice, so stop and let the clipboard +
                // notification path carry it. Under-claiming beats duplicating text.
                WisprFoxAccessibilityService.PasteResult.UNVERIFIED -> return false
                // No field yet, or a verified no-op. Both are worth another go.
                WisprFoxAccessibilityService.PasteResult.FAILED -> Unit
            }
        }
        return false
    }

    fun copyToClipboard(text: String) {
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("wispr-fox", text)
        val extras = PersistableBundle().apply {
            putBoolean(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ClipDescription.EXTRA_IS_SENSITIVE
                } else {
                    "android.content.extra.IS_SENSITIVE"
                },
                true,
            )
        }
        clip.description.extras = extras
        cm.setPrimaryClip(clip)
    }

    /**
     * RC-2.4: post a low-importance "transcript copied" notification when
     * delivery is clipboard-only AND our app isn't in the foreground. If the
     * app IS foreground the in-app UI already shows the result, so we stay
     * quiet. Tapping the notification opens History.
     */
    private fun maybeNotifyClipboardOnly() {
        if (com.wisprfox.android.core.AppState.state.value.appForeground) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            // No notification permission — nothing more we can do; the text is
            // still on the clipboard.
            return
        }
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)

        // Land on History, where the finished transcript lives.
        val openApp = PendingIntent.getActivity(
            appContext,
            2,
            Intent(appContext, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_HISTORY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(com.wisprfox.android.R.drawable.fox_favicon)
            .setContentTitle("Transcript ready — copied to clipboard")
            .setContentText("Tap to view · long-press paste anywhere")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching { nm.notify(NOTIF_ID, notification) }
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Transcript ready", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Lets you know when dictated text is copied but not auto-pasted"
                    setShowBadge(false)
                }
            )
        }
    }

    companion object {
        /**
         * RC-2.2 / P0-4 #4: delay BEFORE each attempt, so attempts land at
         * 150/400/800/1400ms — a ~1.4s window that opens with a settle rather than
         * firing instantly. (The previous ladder was documented as ~1.5s but was
         * 0/300/600/900ms: no settle at all, and 900ms of reach, not 1500.)
         */
        val PASTE_RETRY_DELAYS_MS = longArrayOf(150, 250, 400, 600)

        const val CHANNEL_ID = "delivery"
        private const val NOTIF_ID = 2001
    }
}
