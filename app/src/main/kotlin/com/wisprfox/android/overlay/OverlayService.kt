package com.wisprfox.android.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
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
import com.wisprfox.android.core.PipelineState
import com.wisprfox.android.delivery.WisprFoxAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("overlay", Context.MODE_PRIVATE)
        addOverlay()
        observeVisibility()
    }

    /**
     * The fox is NOT permanently on screen. It appears when the keyboard is up
     * (an editable field is focused) and hides when the keyboard is dismissed —
     * but always stays visible while a recording/transcription is in flight so
     * you can scroll or move it mid-dictation. When the AccessibilityService is
     * off we can't detect the keyboard, so we fall back to always-visible.
     */
    private fun observeVisibility() {
        serviceScope.launch {
            AppState.state
                .map { snap ->
                    val busy = snap.pipeline == PipelineState.RECORDING ||
                        snap.pipeline == PipelineState.TRANSCRIBING ||
                        snap.pipeline == PipelineState.CLEANING ||
                        snap.pipeline == PipelineState.INJECTING
                    val a11yOn = WisprFoxAccessibilityService.isConnected()
                    busy || (if (a11yOn) snap.keyboardVisible else true)
                }
                .distinctUntilChanged()
                .collect { show ->
                    composeView?.visibility = if (show) View.VISIBLE else View.GONE
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
            // BOTTOM-anchored: the fox is the bottom-most element, so the
            // bubble + long-press menu expand UPWARD and the fox never moves.
            gravity = Gravity.BOTTOM or Gravity.START
            x = prefs.getInt("bx", 24)   // distance from left
            y = prefs.getInt("by", 320)  // distance from bottom
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
                com.wisprfox.android.ui.WisprFoxTheme {
                AvatarOverlay(
                    snapshot = state,
                    avatar = avatar,
                    onTap = { container.controller.toggle() },
                    onPickMode = { mode -> container.controller.startMode(mode) },
                    onDrag = { dx, dy ->
                        params.x = (params.x + dx.toInt()).coerceAtLeast(0)
                        // BOTTOM gravity: dragging down lowers the window, i.e.
                        // reduces its distance-from-bottom.
                        params.y = (params.y - dy.toInt()).coerceAtLeast(0)
                        windowManager.updateViewLayout(this, params)
                    },
                    onDragEnd = {
                        prefs.edit().putInt("bx", params.x).putInt("by", params.y).apply()
                    },
                    onOpenApp = {
                        startActivity(
                            Intent(this@OverlayService, com.wisprfox.android.MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                )
                }
            }
        }

        // Start hidden when accessibility can drive visibility and the keyboard
        // isn't up — the collector flips it on as soon as a field is focused.
        view.visibility = if (WisprFoxAccessibilityService.isConnected() &&
            AppState.state.value.pipeline == PipelineState.IDLE &&
            !AppState.state.value.keyboardVisible
        ) View.GONE else View.VISIBLE

        viewOwner.onCreate()
        composeView = view
        windowManager.addView(view, params)
        viewOwner.onResume()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        owner?.onDestroy()
        composeView?.let { runCatching { windowManager.removeView(it) } }
        composeView = null
        owner = null
        super.onDestroy()
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
