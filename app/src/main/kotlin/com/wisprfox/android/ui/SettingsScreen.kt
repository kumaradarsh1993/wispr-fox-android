package com.wisprfox.android.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.settings.AppSettings
import com.wisprfox.android.settings.SecureKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onReplayOnboarding: () -> Unit = {}) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTitle("Speech-to-text")
            Text("Pick the service that hears you best. Groq stays the default for existing installs.", style = MaterialTheme.typography.bodySmall)
            ProviderChipRow(
                options = ProviderCatalog.sttProviders,
                selected = settings.sttProvider,
                onSelect = { scope.launch { container.settingsStore.setSttProvider(it) } },
            )
            ModelChipRow(
                options = ProviderCatalog.sttModelsFor(settings.sttProvider),
                selected = settings.sttModel,
                onSelect = { scope.launch { container.settingsStore.setSttModel(it) } },
            )
            KeyField("Groq STT key", SecureKeyStore.Key.GroqStt, container.secrets)
            KeyField("OpenAI key", SecureKeyStore.Key.OpenAiStt, container.secrets)
            KeyField("Deepgram key", SecureKeyStore.Key.DeepgramStt, container.secrets)
            KeyField("ElevenLabs key", SecureKeyStore.Key.ElevenLabsStt, container.secrets)

            HorizontalDivider()
            SectionTitle("Cleanup")
            Text("Raw mode skips cleanup. Clean and Draft use the provider/model here.", style = MaterialTheme.typography.bodySmall)
            ProviderChipRow(
                options = ProviderCatalog.llmProviders,
                selected = settings.llmProvider,
                onSelect = { scope.launch { container.settingsStore.setLlmProvider(it) } },
            )
            ModelChipRow(
                options = ProviderCatalog.llmModelsFor(settings.llmProvider),
                selected = settings.activeLlmModel,
                onSelect = { model ->
                    scope.launch {
                        when (settings.llmProvider) {
                            AppSettings.PROVIDER_OPENAI -> container.settingsStore.setOpenAiLlmModel(model)
                            AppSettings.PROVIDER_GEMINI -> container.settingsStore.setGeminiModel(model)
                            else -> container.settingsStore.setGroqLlmModel(model)
                        }
                    }
                },
            )
            KeyField("Groq cleanup key (optional)", SecureKeyStore.Key.GroqLlm, container.secrets)
            KeyField("OpenAI cleanup key", SecureKeyStore.Key.OpenAiLlm, container.secrets)
            KeyField("Gemini cleanup key", SecureKeyStore.Key.GeminiLlm, container.secrets)

            HorizontalDivider()
            SectionTitle("Default tap mode")
            Text("What a single tap on the fox does. Long-press always offers all three.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeOption("Raw", DictationMode.RAW, settings.defaultMode) { scope.launch { container.settingsStore.setDefaultMode(it) } }
                ModeOption("Clean", DictationMode.CLEANED, settings.defaultMode) { scope.launch { container.settingsStore.setDefaultMode(it) } }
                ModeOption("Draft", DictationMode.REFORMATTED, settings.defaultMode) { scope.launch { container.settingsStore.setDefaultMode(it) } }
            }

            HorizontalDivider()
            SectionTitle("Delivery & avatar")
            ToggleRow("Auto-paste into the focused field", settings.autoPasteEnabled) {
                scope.launch { container.settingsStore.setAutoPaste(it) }
            }
            ToggleRow("Show floating fox avatar", settings.overlayBubbleEnabled) {
                scope.launch { container.settingsStore.setOverlayBubble(it) }
            }
            ToggleRow("Haptics on long-press", settings.hapticsEnabled) {
                scope.launch { container.settingsStore.setHaptics(it) }
            }
            OutlinedButton(
                onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open Accessibility settings (auto-paste)") }
            OutlinedButton(
                onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Allow overlay (floating fox)") }

            HorizontalDivider()
            SectionTitle("Retention")
            var days by remember(settings.retentionDays) { mutableStateOf(settings.retentionDays.toFloat()) }
            Text("Keep audio for ${days.toInt()} days")
            Slider(
                value = days, onValueChange = { days = it },
                onValueChangeFinished = { scope.launch { container.settingsStore.setRetentionDays(days.toInt()) } },
                valueRange = 0f..30f, steps = 29,
            )
            var cap by remember(settings.retentionMaxMb) { mutableStateOf(settings.retentionMaxMb.toFloat()) }
            Text("Storage cap: ${cap.toInt()} MB")
            Slider(
                value = cap, onValueChange = { cap = it },
                onValueChangeFinished = { scope.launch { container.settingsStore.setRetentionMaxMb(cap.toInt()) } },
                valueRange = 100f..1000f, steps = 17,
            )

            HorizontalDivider()
            SectionTitle("About")
            Text("wispr-fox for Android - bring-your-own-key dictation. Audio is sent only to the provider you choose for that request; keys stay encrypted on this phone.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
                Text("Replay setup guide")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

@Composable
private fun ModeOption(label: String, mode: DictationMode, current: DictationMode, onSelect: (DictationMode) -> Unit) {
    FilterChip(selected = current == mode, onClick = { onSelect(mode) }, label = { Text(label) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderChipRow(
    options: List<ProviderCatalog.ProviderOption>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option.id,
                onClick = { onSelect(option.id) },
                label = { Text("${option.label} · ${option.summary}") },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelChipRow(
    options: List<ProviderCatalog.ModelOption>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option.id,
                onClick = { onSelect(option.id) },
                label = { Text(option.label) },
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
// KeyField lives in ui/KeyField.kt (shared with Onboarding).
