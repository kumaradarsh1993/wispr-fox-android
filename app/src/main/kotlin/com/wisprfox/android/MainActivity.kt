package com.wisprfox.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.wisprfox.android.ui.WisprFoxTheme
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
import com.wisprfox.android.overlay.OverlayService
import com.wisprfox.android.settings.SecureKeyStore
import com.wisprfox.android.ui.HistoryScreen
import com.wisprfox.android.ui.HomeScreen
import com.wisprfox.android.ui.OnboardingScreen
import com.wisprfox.android.ui.SettingsScreen
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
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        pendingDeepLink = intent?.getStringExtra(EXTRA_OPEN)
        setContent {
            WisprFoxTheme {
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
                        composable("settings") {
                            SettingsScreen(
                                onBack = { nav.popBackStack() },
                                onReplayOnboarding = { nav.navigate("onboarding") },
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
        // tap while it's already running arrives here, not onCreate.
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let { pendingDeepLink = it }
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
