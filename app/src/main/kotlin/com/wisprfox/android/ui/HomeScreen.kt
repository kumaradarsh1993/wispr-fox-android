package com.wisprfox.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wisprfox.android.R
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.AppState
import com.wisprfox.android.core.PipelineState
import com.wisprfox.android.delivery.WisprFoxAccessibilityService
import com.wisprfox.android.history.Recording
import com.wisprfox.android.overlay.AvatarView
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.settings.AppSettings
import com.wisprfox.android.settings.Avatar
import com.wisprfox.android.settings.SecureKeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private const val STT_TURBO = "whisper-large-v3-turbo"
private const val STT_ACCURATE = "whisper-large-v3"
private val GREEN = Color(0xFF6CB16D)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(onOpenHistory: () -> Unit, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()
    val state by AppState.state.collectAsState()
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())
    val recordings by container.recordings.observeRecent(limit = 3).collectAsState(initial = emptyList())
    val recording = state.pipeline == PipelineState.RECORDING

    // Permission status is read live and refreshed on each ON_RESUME so the
    // "Finish setup" banner disappears the moment the user returns from the
    // system page that granted it.
    var permTick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) permTick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val missing = remember(permTick) { missingPermissions(ctx) }

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
        bottomBar = { WisprBottomBar(NavTab.HOME, onSelect = { if (it == NavTab.HISTORY) onOpenHistory() }) },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Setup banner (only when something's not granted). Whole row is
            // tappable; it deep-links to Settings where the full toggles live.
            if (missing.isNotEmpty()) {
                SetupBanner(missing.size) { onOpenSettings() }
            }

            // Hero: status pill, tappable avatar, voice prompt.
            Spacer(Modifier.height(2.dp))
            StatusPill(state, recording)
            Box(
                modifier = Modifier.size(180.dp).clickable { container.controller.toggle(settings.defaultMode) },
                contentAlignment = Alignment.Center,
            ) {
                AvatarView(settings.avatar, state.pipeline, Modifier.size(160.dp))
            }
            Text(heroTitle(state), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                heroSubtitle(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Avatar picker + big mic.
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
                    modifier = Modifier.size(56.dp),
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

            SectionCard("Speech-to-text") {
                Text(
                    "${ProviderCatalog.label(settings.sttProvider)} · ${ProviderCatalog.shortModel(settings.sttModel)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ModelFlow(
                    options = ProviderCatalog.sttModelsFor(settings.sttProvider).map { it.id to it.label },
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
                        selected = settings.llmProvider == AppSettings.PROVIDER_OPENAI,
                        onClick = { scope.launch { container.settingsStore.setLlmProvider(AppSettings.PROVIDER_OPENAI) } },
                        label = { Text("OpenAI") },
                    )
                    FilterChip(
                        selected = settings.llmProvider == AppSettings.PROVIDER_GEMINI && geminiReady,
                        enabled = geminiReady,
                        onClick = { scope.launch { container.settingsStore.setLlmProvider(AppSettings.PROVIDER_GEMINI) } },
                        label = { Text("Gemini") },
                    )
                }
                if (!geminiReady) {
                    Text(
                        "Add a Gemini key in Settings to enable Gemini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Recents preview (kept on the main screen so it's never more than
            // a glance away — the bottom-nav History tab opens the full list).
            RecentsCard(recordings = recordings, onOpenAll = onOpenHistory)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/* ── Setup banner: appears only when something's missing ─────────────────── */

@Composable
private fun SetupBanner(missingCount: Int, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Column(Modifier.weight(1f)) {
                Text(
                    "Finish setup",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    if (missingCount == 1) "One permission still needs to be granted."
                    else "$missingCount permissions still need to be granted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/* ── Recents preview ─────────────────────────────────────────────────────── */

@Composable
private fun RecentsCard(recordings: List<Recording>, onOpenAll: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenAll).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recent", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("See all", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (recordings.isEmpty()) {
                Text(
                    "Nothing yet — tap the fox to dictate something.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            } else {
                recordings.forEach { r ->
                    RecentRow(r, onClick = onOpenAll)
                }
            }
        }
    }
}

@Composable
private fun RecentRow(rec: Recording, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                rec.primaryText()?.takeIf { it.isNotBlank() }?.let { it.take(80) }
                    ?: "(no transcript yet)",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                "${recentTimeFmt.format(Date(rec.createdAt))} · ${rec.mode.label} · ${rec.durationMs / 1000}s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val recentTimeFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

/* ── Reusables ───────────────────────────────────────────────────────────── */

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelFlow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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

/* ── Permission probe ────────────────────────────────────────────────────── */

private fun missingPermissions(ctx: Context): List<String> {
    val out = mutableListOf<String>()
    if (!Settings.canDrawOverlays(ctx)) out += "overlay"
    if (!WisprFoxAccessibilityService.isConnected()) out += "auto-paste"
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
    if (pm?.isIgnoringBatteryOptimizations(ctx.packageName) != true) out += "battery"
    return out
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
