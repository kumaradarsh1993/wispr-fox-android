package com.wisprfox.android.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.AppState
import com.wisprfox.android.delivery.WisprFoxAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Hosts the always-on floating fox avatar in a system overlay window
 * (TYPE_APPLICATION_OVERLAY). The avatar is Compose, so the ComposeView needs
 * a Lifecycle / ViewModelStore / SavedStateRegistry owner wired manually — a
 * Service isn't a ComponentActivity. This is the fiddly part the plan flagged;
 * [OverlayViewOwner] supplies all three.
 *
 * Position is dragged by the user and persisted in SharedPreferences so the
 * fox reappears where they left it.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var owner: OverlayViewOwner? = null
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var prefs: android.content.SharedPreferences
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * P0-2: the persisted anchor is the FOX's centre-X, not the window's left edge.
     * [UNSET] until the first layout tells us how wide the fox is (needed to migrate
     * the legacy `bx` pref). See [OverlayAnchor] for why.
     */
    private var foxCenterX: Int = UNSET
    private var contentWidthPx: Int = 0
    private var foxWidthPx: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("overlay", Context.MODE_PRIVATE)
        addOverlay()
        observeVisibility()
        observeEnabled()
    }

    /**
     * The fox is NOT permanently on screen. It appears when the keyboard is up
     * (an editable field is focused) and hides when the keyboard is dismissed —
     * but always stays visible while a recording/transcription is in flight so
     * you can scroll or move it mid-dictation.
     *
     * RC-1.1: when the AccessibilityService is OFF we can't detect the keyboard,
     * so instead of pinning the fox always-visible (the "fox sticks around with
     * no text box in sight" bug), we show it only briefly after the user
     * interacts with it. The decision itself lives in the pure
     * [OverlayVisibility] so it's unit-tested. A slow ticker re-evaluates so the
     * grace window can expire even when no AppState change arrives.
     */
    private fun observeVisibility() {
        // Emit periodically so the interaction grace window can lapse without a
        // fresh AppState emission to trigger recomputation.
        val ticker = flow {
            while (true) {
                emit(Unit)
                delay(TICK_MS)
            }
        }
        serviceScope.launch {
            combine(AppState.state, ticker) { snap, _ -> snap }
                .map { snap ->
                    OverlayVisibility.shouldShow(
                        pipeline = snap.pipeline,
                        a11yConnected = WisprFoxAccessibilityService.isConnected(),
                        keyboardVisible = snap.keyboardVisible,
                        nowMs = SystemClock.elapsedRealtime(),
                        lastInteractionMs = snap.lastInteractionMs,
                    )
                }
                .distinctUntilChanged()
                .collect { show ->
                    composeView?.visibility = if (show) View.VISIBLE else View.GONE
                }
        }
    }

    private fun observeEnabled() {
        val container = WisprFoxApp.container(this)
        serviceScope.launch {
            container.settingsStore.settings
                .map { it.overlayBubbleEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (!enabled) stopSelf()
                }
        }
    }

    private fun addOverlay() {
        if (composeView != null) return

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // BOTTOM-anchored: the fox is the bottom-most element, so the bubble +
            // long-press menu expand UPWARD and the fox never moves vertically.
            // Horizontally it's the opposite — x pins the window's LEFT EDGE while the
            // window's width tracks its widest child — so x is re-derived from the
            // fox's centre on every size change (P0-2, see [applyAnchor]). This seeds
            // the legacy value only for the frame before the first layout lands.
            gravity = Gravity.BOTTOM or Gravity.START
            x = prefs.getInt(KEY_LEGACY_LEFT_X, DEFAULT_LEFT_X)
            y = prefs.getInt(KEY_BOTTOM_Y, DEFAULT_BOTTOM_Y)
        }

        val viewOwner = OverlayViewOwner().also { owner = it }
        val container = WisprFoxApp.container(this)

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(viewOwner)
            setViewTreeViewModelStoreOwner(viewOwner)
            setViewTreeSavedStateRegistryOwner(viewOwner)
            setContent {
                val state by AppState.state.collectAsState()
                val avatar by container.settingsStore.settings
                    .map { it.avatar }
                    .collectAsState(initial = com.wisprfox.android.settings.Avatar.FOX)
                // P-3: S/M/L overlay-size preset, collected from the same flow.
                val avatarScale by container.settingsStore.settings
                    .map { it.avatarScale }
                    .collectAsState(initial = com.wisprfox.android.settings.AvatarScale.MEDIUM)
                com.wisprfox.android.ui.WisprFoxTheme {
                AvatarOverlay(
                    snapshot = state,
                    avatar = avatar,
                    onTap = {
                        AppState.markInteraction(SystemClock.elapsedRealtime())
                        container.controller.toggle()
                    },
                    onPickMode = { mode ->
                        AppState.markInteraction(SystemClock.elapsedRealtime())
                        container.controller.startMode(mode)
                    },
                    onDrag = { dx, dy ->
                        // Refresh the grace window so the fox doesn't vanish
                        // mid-drag when a11y is off (RC-1.1).
                        AppState.markInteraction(SystemClock.elapsedRealtime())
                        // P0-2: drag moves the ANCHOR (the fox's centre); the window's
                        // left edge follows from it in applyAnchor().
                        if (foxCenterX != UNSET) foxCenterX += dx.toInt()
                        // BOTTOM gravity: dragging down lowers the window, i.e.
                        // reduces its distance-from-bottom.
                        params.y = (params.y - dy.toInt()).coerceAtLeast(0)
                        applyAnchor()
                    },
                    onDragEnd = {
                        prefs.edit()
                            .putInt(KEY_FOX_CENTER_X, foxCenterX)
                            .putInt(KEY_BOTTOM_Y, params.y)
                            .apply()
                    },
                    onOpenApp = {
                        startActivity(
                            Intent(this@OverlayService, com.wisprfox.android.MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    scale = avatarScale.multiplier,
                    onAnchorMetrics = ::onAnchorMetrics,
                )
                }
            }
        }

        // Seed initial visibility from the same pure rule the collector uses, so
        // the fox doesn't flash on before the first emission decides otherwise.
        val snap = AppState.state.value
        val initiallyVisible = OverlayVisibility.shouldShow(
            pipeline = snap.pipeline,
            a11yConnected = WisprFoxAccessibilityService.isConnected(),
            keyboardVisible = snap.keyboardVisible,
            nowMs = SystemClock.elapsedRealtime(),
            lastInteractionMs = snap.lastInteractionMs,
        )
        view.visibility = if (initiallyVisible) View.VISIBLE else View.GONE

        viewOwner.onCreate()
        composeView = view
        windowManager.addView(view, params)
        viewOwner.onResume()
    }

    /**
     * P0-2: called on every layout where the column's width or the fox's width
     * changed — i.e. exactly when the menu opens/closes, the bubble appears/vanishes,
     * the bubble's live label steps to a longer string, or the user switches avatar
     * scale. Each of those used to shove the centred fox sideways.
     */
    private fun onAnchorMetrics(contentWidth: Int, foxWidth: Int) {
        contentWidthPx = contentWidth
        foxWidthPx = foxWidth
        if (foxCenterX == UNSET) {
            foxCenterX = prefs.getInt(KEY_FOX_CENTER_X, UNSET).takeIf { it != UNSET }
                // First run after the upgrade: the old pref was the window's left
                // edge, which (fox-only column) was the fox's left edge.
                ?: OverlayAnchor.migrateCenterX(prefs.getInt(KEY_LEGACY_LEFT_X, DEFAULT_LEFT_X), foxWidth)
        }
        applyAnchor()
    }

    /** Re-derive the window's left edge so the fox's centre stays exactly on [foxCenterX]. */
    private fun applyAnchor() {
        val view = composeView ?: return
        if (contentWidthPx > 0 && foxWidthPx > 0 && foxCenterX != UNSET) {
            foxCenterX = OverlayAnchor.clampCenterX(foxCenterX, foxWidthPx, screenWidthPx())
            params.x = OverlayAnchor.windowLeftX(foxCenterX, contentWidthPx)
        }
        // onSizeChanged fires from inside the layout pass; calling updateViewLayout
        // there re-enters layout, so hand it to the next frame instead.
        view.post { runCatching { windowManager.updateViewLayout(view, params) } }
    }

    private fun screenWidthPx(): Int = windowManager.currentWindowMetrics.bounds.width()

    override fun onDestroy() {
        serviceScope.cancel()
        owner?.onDestroy()
        composeView?.let { runCatching { windowManager.removeView(it) } }
        composeView = null
        owner = null
        super.onDestroy()
    }

    private companion object {
        /**
         * Re-evaluate visibility this often so the grace window can expire when
         * a11y is off without a fresh AppState emission. 1s is imperceptible and
         * negligible for battery (the overlay process is already resident).
         */
        const val TICK_MS = 1_000L

        /** Sentinel for "we haven't resolved the fox's centre yet" (a real centre is >= 0). */
        const val UNSET = -1

        /** P0-2 anchor: the fox's centre-X, in px from the left screen edge. */
        const val KEY_FOX_CENTER_X = "fcx"

        /** Pre-P0-2 anchor: the overlay WINDOW's left edge. Read once, to migrate. */
        const val KEY_LEGACY_LEFT_X = "bx"
        const val KEY_BOTTOM_Y = "by"
        const val DEFAULT_LEFT_X = 24
        const val DEFAULT_BOTTOM_Y = 320
    }
}

/** Minimal Lifecycle + ViewModelStore + SavedStateRegistry owner for the overlay. */
private class OverlayViewOwner :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
