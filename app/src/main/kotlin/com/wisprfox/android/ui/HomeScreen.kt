package com.wisprfox.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.AppState
import com.wisprfox.android.core.PipelineState
import com.wisprfox.android.delivery.WisprFoxAccessibilityService
import com.wisprfox.android.overlay.AvatarView
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.settings.AppSettings
import com.wisprfox.android.settings.Avatar
import com.wisprfox.android.settings.SecureKeyStore
import kotlinx.coroutines.launch

private const val STT_TURBO = "whisper-large-v3-turbo"
private const val STT_ACCURATE = "whisper-large-v3"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenHistory: () -> Unit, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()
    val state by AppState.state.collectAsState()
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("wispr-fox") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Tappable hero avatar — tap to start/stop in the default mode.
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clickable { container.controller.toggle(settings.defaultMode) },
                contentAlignment = Alignment.Center,
            ) {
                AvatarView(settings.avatar, state.pipeline, Modifier.size(140.dp))
            }
            Text(statusLine(state), style = MaterialTheme.typography.titleMedium)
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.height(4.dp))
            Text("Avatar", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.avatar == Avatar.FOX,
                    onClick = { scope.launch { container.settingsStore.setAvatar(Avatar.FOX) } },
                    label = { Text("Fox") },
                )
                FilterChip(
                    selected = settings.avatar == Avatar.CLIPPY,
                    onClick = { scope.launch { container.settingsStore.setAvatar(Avatar.CLIPPY) } },
                    label = { Text("Clippy") },
                )
                FilterChip(
                    selected = false,
                    enabled = false,
                    onClick = {},
                    label = { Text("MS Clippy · soon") },
                )
            }

            Spacer(Modifier.height(4.dp))
            Text("Default mode", style = MaterialTheme.typography.labelLarge)
            Text(
                "Applies to a single tap on Foxy. Long-press Foxy to pick a different mode just for that recording.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DictationMode.entries.filter { it != DictationMode.ADVANCED }.forEach { mode ->
                    FilterChip(
                        selected = settings.defaultMode == mode,
                        onClick = { scope.launch { container.settingsStore.setDefaultMode(mode) } },
                        label = { Text(mode.label) },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text("Transcription model", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.sttModel == STT_TURBO,
                    onClick = { scope.launch { container.settingsStore.setSttModel(STT_TURBO) } },
                    label = { Text("Turbo") },
                )
                FilterChip(
                    selected = settings.sttModel == STT_ACCURATE,
                    onClick = { scope.launch { container.settingsStore.setSttModel(STT_ACCURATE) } },
                    label = { Text("Accurate") },
                )
            }

            Text("Cleanup model", style = MaterialTheme.typography.labelLarge)
            val geminiReady = container.secrets.has(SecureKeyStore.Key.GeminiLlm)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.llmProvider == AppSettings.PROVIDER_GROQ,
                    onClick = { scope.launch { container.settingsStore.setLlmProvider(AppSettings.PROVIDER_GROQ) } },
                    label = { Text("Llama 70B") },
                )
                FilterChip(
                    selected = settings.llmProvider == AppSettings.PROVIDER_GEMINI && geminiReady,
                    enabled = geminiReady,
                    onClick = { scope.launch { container.settingsStore.setLlmProvider(AppSettings.PROVIDER_GEMINI) } },
                    label = { Text("Gemini Flash") },
                )
            }
            if (!geminiReady) {
                Text("Add a Gemini key (Settings) to enable Gemini.", style = MaterialTheme.typography.bodySmall)
            }

            val recording = state.pipeline == PipelineState.RECORDING
            Button(onClick = { container.controller.toggle(settings.defaultMode) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (recording) "Stop & transcribe" else "Start recording")
            }
            FilledTonalButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.History, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Past chats")
            }

            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Setup", style = MaterialTheme.typography.labelLarge)
                    SetupButtons(ctx)
                }
            }
        }
    }
}

@Composable
private fun SetupButtons(ctx: Context) {
    val overlayOk = Settings.canDrawOverlays(ctx)
    val a11yOk = WisprFoxAccessibilityService.isConnected()
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val batteryOk = pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true

    // Three compact tiles side by side. Tapping always opens the relevant page
    // (so it never feels like a dead button), and the tick shows current state.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SetupTile(Modifier.weight(1f), "Overlay", "Floating fox", overlayOk) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        }
        SetupTile(Modifier.weight(1f), "Auto-paste", "Into the box", a11yOk) {
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        SetupTile(Modifier.weight(1f), "Battery", "Stay alive", batteryOk) {
            val intent = if (batteryOk) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            } else {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
            }
            ctx.startActivity(intent)
        }
    }
}

@Composable
private fun SetupTile(
    modifier: Modifier,
    title: String,
    subtitle: String,
    ok: Boolean,
    onClick: () -> Unit,
) {
    Card(modifier.clickable { onClick() }) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (ok) {
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Filled.Check, null, tint = Color(0xFF2FB170), modifier = Modifier.size(13.dp))
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun statusLine(s: AppState.Snapshot): String = when (s.pipeline) {
    PipelineState.IDLE -> "Idle — tap Foxy to dictate"
    PipelineState.RECORDING -> "Listening…"
    PipelineState.TRANSCRIBING -> "Transcribing…"
    PipelineState.CLEANING -> "Polishing…"
    PipelineState.INJECTING -> "Delivering…"
    PipelineState.DONE -> "Done"
    PipelineState.ERROR -> "Error"
}
