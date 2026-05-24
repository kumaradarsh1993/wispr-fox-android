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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.core.content.ContextCompat
import com.wisprfox.android.overlay.OverlayService
import com.wisprfox.android.settings.SecureKeyStore
import com.wisprfox.android.ui.HistoryScreen
import com.wisprfox.android.ui.HomeScreen
import com.wisprfox.android.ui.OnboardingScreen
import com.wisprfox.android.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        setContent {
            WisprFoxTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    val hasKey = WisprFoxApp.container(this).secrets.has(SecureKeyStore.Key.GroqStt)
                    NavHost(navController = nav, startDestination = if (hasKey) "home" else "onboarding") {
                        composable("onboarding") {
                            OnboardingScreen(onDone = {
                                nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                            })
                        }
                        composable("home") {
                            HomeScreen(
                                onOpenHistory = { nav.navigate("history") },
                                onOpenSettings = { nav.navigate("settings") },
                            )
                        }
                        composable("history") { HistoryScreen(onBack = { nav.popBackStack() }) }
                        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, OverlayService::class.java))
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
}
