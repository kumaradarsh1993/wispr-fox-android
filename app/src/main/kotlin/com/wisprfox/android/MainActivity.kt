package com.wisprfox.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.wisprfox.android.ui.SettingsSpoke
import com.wisprfox.android.ui.WisprFoxTheme
import com.wisprfox.android.ui.rememberThemeChoice
import com.wisprfox.android.ui.settings.AboutSpoke
import com.wisprfox.android.ui.settings.AccountSpoke
import com.wisprfox.android.ui.settings.CleanupSpoke
import com.wisprfox.android.ui.settings.DeliverySpoke
import com.wisprfox.android.ui.settings.FoxySpoke
import com.wisprfox.android.ui.settings.StorageSpoke
import com.wisprfox.android.ui.settings.TranscriptionSpoke
import com.wisprfox.android.ui.settings.UsageSpoke
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wisprfox.android.core.AppState
import com.wisprfox.android.overlay.OverlayService
import com.wisprfox.android.settings.SecureKeyStore
import com.wisprfox.android.sync.SyncWorker
import com.wisprfox.android.ui.HistoryScreen
import com.wisprfox.android.ui.HomeScreen
import com.wisprfox.android.ui.OnboardingScreen
import com.wisprfox.android.ui.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) {}

    /**
     * Deep-link target requested by a notification tap (Task 5). The delivery
     * "Transcript copied" notification should land the user on History. We key
     * off the `open=history` intent extra; the value is bumped on each new
     * intent so the Compose NavHost can react. See the note in the report:
     * DeliveryManager (which we may not edit) currently sets no extra — it needs
     * a one-line `.putExtra("open", "history")` for this to trigger from that
     * notification. The handling here is in place regardless so any caller that
     * adds the extra lands on History.
     */
    private var pendingDeepLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 35 on Android 15+ *enforces* edge-to-edge whether we opt in
        // or not, and this call was simply never made (audit P1-2) — so the app
        // has been drawing under the system bars and only surviving on the
        // screens that happened to use Scaffold. Must precede setContent.
        // WisprFoxTheme sets the bar icon polarity to match the painted surface.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        pendingDeepLink = intent?.getStringExtra(EXTRA_OPEN)
        handleAuthCallbackIfPresent(intent)
        setContent {
            WisprFoxTheme(choice = rememberThemeChoice()) {
                Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    val hasKey = hasAnySttKey()

                    // React to a notification deep-link (Task 5). Consuming it
                    // (setting pendingDeepLink=null) prevents re-navigation on
                    // recomposition / config change.
                    val deepLink = pendingDeepLink
                    LaunchedEffect(deepLink) {
                        if (deepLink == OPEN_HISTORY && hasKey) {
                            nav.navigate("history") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            pendingDeepLink = null
                        }
                    }

                    // Accounts + cross-device sync (v2.0): a 60s in-app ticker
                    // while this Compose tree is alive (effectively the app's
                    // whole foreground lifetime, since MainActivity is the only
                    // activity) — on top of the foreground/periodic/post-recording
                    // triggers elsewhere. SyncEngine.syncNow() is a fast no-op
                    // when signed out or unconfigured, and appForeground gates it
                    // so a backgrounded process doesn't keep polling.
                    LaunchedEffect(Unit) {
                        val container = WisprFoxApp.container(this@MainActivity)
                        while (true) {
                            delay(60_000)
                            if (AppState.state.value.appForeground) {
                                runCatching { container.syncEngine.syncNow() }
                            }
                        }
                    }

                    NavHost(navController = nav, startDestination = if (hasKey) "home" else "onboarding") {
                        composable("onboarding") {
                            OnboardingScreen(onDone = {
                                nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                            })
                        }
                        composable("home") {
                            HomeScreen(
                                onOpenHistory = {
                                    nav.navigate("history") {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onOpenSettings = { nav.navigate("settings") },
                            )
                        }
                        composable("history") {
                            HistoryScreen(
                                onBack = { nav.popBackStack() },
                                onOpenHome = {
                                    nav.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        // Settings is hub-and-spoke (audit P0-3): the hub is a
                        // list of rows with current-value summaries, each opening
                        // a large-title sub-page. One route per spoke.
                        composable("settings") {
                            SettingsScreen(
                                onBack = { nav.popBackStack() },
                                onOpenSpoke = { nav.navigate(it.route) },
                            )
                        }
                        composable(SettingsSpoke.TRANSCRIPTION.route) {
                            TranscriptionSpoke(onBack = { nav.popBackStack() })
                        }
                        composable(SettingsSpoke.CLEANUP.route) {
                            CleanupSpoke(onBack = { nav.popBackStack() })
                        }
                        composable(SettingsSpoke.FOXY.route) {
                            FoxySpoke(onBack = { nav.popBackStack() })
                        }
                        composable(SettingsSpoke.DELIVERY.route) {
                            DeliverySpoke(onBack = { nav.popBackStack() })
                        }
                        composable(SettingsSpoke.USAGE.route) {
                            UsageSpoke(onBack = { nav.popBackStack() })
                        }
                        composable(SettingsSpoke.STORAGE.route) {
                            StorageSpoke(onBack = { nav.popBackStack() })
                        }
                        composable(SettingsSpoke.ACCOUNT.route) {
                            AccountSpoke(onBack = { nav.popBackStack() })
                        }
                        composable(SettingsSpoke.ABOUT.route) {
                            AboutSpoke(
                                onBack = { nav.popBackStack() },
                                // Replaying setup from About shouldn't leave the
                                // whole settings stack underneath it.
                                onReplayOnboarding = {
                                    nav.navigate("onboarding") { popUpTo("home") }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The activity is launched SINGLE_TOP by the delivery notification, so a
        // tap while it's already running arrives here, not onCreate. The
        // Supabase Google-auth Custom Tab redirect (wisprfox://auth-callback)
        // also lands here — MainActivity is `launchMode="singleTask"` so it's
        // reused rather than getting a fresh onCreate.
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let { pendingDeepLink = it }
        handleAuthCallbackIfPresent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Accounts + cross-device sync (v2.0): sync on app foreground. Inert
        // (no-op, no network) when signed out or unconfigured.
        lifecycleScope.launch {
            val container = WisprFoxApp.container(this@MainActivity)
            runCatching { container.syncEngine.syncNow() }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val container = WisprFoxApp.container(this@MainActivity)
            val settings = container.settingsStore.settings.first()
            if (Settings.canDrawOverlays(this@MainActivity) && settings.overlayBubbleEnabled) {
                startService(Intent(this@MainActivity, OverlayService::class.java))
            } else {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
            }
        }
    }

    /**
     * Completes the Supabase Google PKCE round-trip: the Custom Tab redirects
     * to `wisprfox://auth-callback?code=...`, which the manifest's intent-filter
     * routes here (see AndroidManifest.xml). Exchanges the code for a session
     * and, on success, kicks an immediate initial sync.
     */
    private fun handleAuthCallbackIfPresent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "wisprfox" || uri.host != "auth-callback") return
        val code = uri.getQueryParameter("code") ?: return
        lifecycleScope.launch {
            val container = WisprFoxApp.container(this@MainActivity)
            val result = container.authManager.completeGoogleSignIn(code)
            if (result.isSuccess) {
                SyncWorker.ensurePeriodic(this@MainActivity)
                SyncWorker.enqueueOnce(this@MainActivity, initial = true)
            }
        }
    }

    private fun requestRuntimePermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.POST_NOTIFICATIONS
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    private fun hasAnySttKey(): Boolean {
        val secrets = WisprFoxApp.container(this).secrets
        return secrets.has(SecureKeyStore.Key.GroqStt) ||
            secrets.has(SecureKeyStore.Key.OpenAiStt) ||
            secrets.has(SecureKeyStore.Key.DeepgramStt) ||
            secrets.has(SecureKeyStore.Key.ElevenLabsStt)
    }

    companion object {
        /** Intent extra a notification sets to deep-link into a screen. */
        const val EXTRA_OPEN = "open"
        /** Value for [EXTRA_OPEN] that lands on the History screen. */
        const val OPEN_HISTORY = "history"
    }
}
