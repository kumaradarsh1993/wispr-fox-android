package com.wisprfox.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.wisprfox.android.R
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
private val GREEN = Color(0xFF6CB16D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenHistory: () -> Unit, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()
    val state by AppState.state.collectAsState()
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())
    val recording = state.pipeline == PipelineState.RECORDING

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.fox_favicon), null, Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)) { append("wispr") }
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) { append("fox") }
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusPill(state, recording)

            Box(
                modifier = Modifier.size(170.dp).clickable { container.controller.toggle(settings.defaultMode) },
                contentAlignment = Alignment.Center,
            ) {
                AvatarView(settings.avatar, state.pipeline, Modifier.size(150.dp))
            }

            Text(heroTitle(state), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(heroSubtitle(state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Avatar picker + mic record button.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarChip(
                        selected = settings.avatar == Avatar.FOX,
                        onClick = { scope.launch { container.settingsStore.setAvatar(Avatar.FOX) } },
                    ) { AvatarView(Avatar.FOX, PipelineState.IDLE, Modifier.size(40.dp)) }
                    AvatarChip(
                        selected = settings.avatar == Avatar.CLIPPY,
                        onClick = { scope.launch { container.settingsStore.setAvatar(Avatar.CLIPPY) } },
                    ) { AvatarView(Avatar.CLIPPY, PipelineState.IDLE, Modifier.size(40.dp)) }
                    AvatarChip(selected = false, enabled = false, onClick = {}) {
                        Text("soon", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.weight(1f))
                FilledIconButton(
                    onClick = { container.controller.toggle(settings.defaultMode) },
                    modifier = Modifier.size(54.dp),
                    colors = if (recording) IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                    else IconButtonDefaults.filledIconButtonColors(),
                ) {
                    Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = if (recording) "Stop" else "Record")
                }
            }

            SectionCard("Recording style") {
                ChipRow(
                    options = listOf(DictationMode.RAW to "Raw", DictationMode.CLEANED to "Clean", DictationMode.REFORMATTED to "Draft"),
                    selected = settings.defaultMode,
                    onSelect = { scope.launch { container.settingsStore.setDefaultMode(it) } },
                )
            }

            SectionCard("Speed") {
                ChipRow(
                    options = listOf(STT_TURBO to "Fast", STT_ACCURATE to "Accurate"),
                    selected = settings.sttModel,
                    onSelect = { scope.launch { container.settingsStore.setSttModel(it) } },
                )
            }

            SectionCard("Smart cleanup") {
                val geminiReady = container.secrets.has(SecureKeyStore.Key.GeminiLlm)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.llmProvider == AppSettings.PROVIDER_GROQ,
                        onClick = { scope.launch { container.settingsStore.setLlmProvider(AppSettings.PROVIDER_GROQ) } },
                        label = { Text("Llama 70B · Groq") },
                    )
                    FilterChip(
                        selected = settings.llmProvider == AppSettings.PROVIDER_GEMINI && geminiReady,
                        enabled = geminiReady,
                        onClick = { scope.launch { container.settingsStore.setLlmProvider(AppSettings.PROVIDER_GEMINI) } },
                        label = { Text("Gemini") },
                    )
                }
                if (!geminiReady) {
                    Text("Add a Gemini key in Settings to enable Gemini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Setup tiles.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val overlayOk = Settings.canDrawOverlays(ctx)
                val a11yOk = WisprFoxAccessibilityService.isConnected()
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val batteryOk = pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
                SetupTile(Modifier.weight(1f), "Overlay", "Floating fox", overlayOk) {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                }
                SetupTile(Modifier.weight(1f), "Auto-insert", "Into the box", a11yOk) {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                SetupTile(Modifier.weight(1f), "Battery", "Stay alive", batteryOk) {
                    val intent = if (batteryOk) Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    else Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                    ctx.startActivity(intent)
                }
            }

            Card(
                Modifier.fillMaxWidth().clickable { onOpenHistory() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatusPill(state: AppState.Snapshot, recording: Boolean) {
    val color = if (recording || state.pipeline != PipelineState.IDLE) MaterialTheme.colorScheme.primary else GREEN
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(statusLine(state), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun AvatarChip(
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .size(54.dp)
            .border(2.dp, border, RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SetupTile(modifier: Modifier, title: String, subtitle: String, ok: Boolean, onClick: () -> Unit) {
    Card(modifier.clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (ok) {
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Filled.Check, null, tint = GREEN, modifier = Modifier.size(13.dp))
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun heroTitle(s: AppState.Snapshot): String = when (s.pipeline) {
    PipelineState.IDLE -> "Tap to speak"
    PipelineState.RECORDING -> "Listening…"
    PipelineState.TRANSCRIBING -> "Transcribing…"
    PipelineState.CLEANING -> "Polishing…"
    PipelineState.INJECTING -> "Delivering…"
    PipelineState.DONE -> "Done"
    PipelineState.ERROR -> "Something went wrong"
}

private fun heroSubtitle(s: AppState.Snapshot): String = when (s.pipeline) {
    PipelineState.IDLE -> "I'm here when you're ready."
    PipelineState.ERROR -> s.message ?: "Tap to try again."
    else -> s.message ?: "Hang tight…"
}

private fun statusLine(s: AppState.Snapshot): String = when (s.pipeline) {
    PipelineState.IDLE -> "Ready"
    PipelineState.RECORDING -> "Recording"
    PipelineState.TRANSCRIBING -> "Transcribing"
    PipelineState.CLEANING -> "Polishing"
    PipelineState.INJECTING -> "Delivering"
    PipelineState.DONE -> "Done"
    PipelineState.ERROR -> "Error"
}
