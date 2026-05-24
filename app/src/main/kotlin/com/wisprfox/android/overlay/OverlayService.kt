package com.wisprfox.android.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("overlay", Context.MODE_PRIVATE)
        addOverlay()
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
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", 32)
            y = prefs.getInt("y", 240)
        }

        val viewOwner = OverlayViewOwner().also { owner = it }
        val container = WisprFoxApp.container(this)

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(viewOwner)
            setViewTreeViewModelStoreOwner(viewOwner)
            setViewTreeSavedStateRegistryOwner(viewOwner)
            setContent {
                val state by AppState.state.collectAsState()
                AvatarOverlay(
                    snapshot = state,
                    onTap = { container.controller.toggle() },
                    onPickMode = { mode -> container.controller.startMode(mode) },
                    onDrag = { dx, dy ->
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        windowManager.updateViewLayout(this, params)
                    },
                    onDragEnd = {
                        prefs.edit().putInt("x", params.x).putInt("y", params.y).apply()
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

        viewOwner.onCreate()
        composeView = view
        windowManager.addView(view, params)
        viewOwner.onResume()
    }

    override fun onDestroy() {
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
