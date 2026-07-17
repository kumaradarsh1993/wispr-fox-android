package com.wisprfox.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.settings.AppSettings
import com.wisprfox.android.settings.SecureKeyStore
import com.wisprfox.android.ui.SectionCard
import com.wisprfox.android.ui.Space
import com.wisprfox.android.ui.SpokeScaffold
import kotlinx.coroutines.launch

/* ── 1 · Transcription ───────────────────────────────────────────────────── */

@Composable
fun TranscriptionSpoke(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())

    SpokeScaffold("Transcription", onBack) {
        SectionCard("Service") {
            ProviderChips(
                options = ProviderCatalog.sttProviders,
                selected = settings.sttProvider,
                onSelect = { scope.launch { container.settingsStore.setSttProvider(it) } },
            )
        }

        SectionCard("Model") {
            ModelChips(
                options = ProviderCatalog.sttModelsFor(settings.sttProvider),
                selected = settings.sttModel,
                onSelect = { scope.launch { container.settingsStore.setSttModel(it) } },
            )
        }

        KeySection(
            selectedProvider = settings.sttProvider,
            providers = ProviderCatalog.sttProviders,
            keyFor = ::sttKeyFor,
            secrets = container.secrets,
            labelFor = { "${ProviderCatalog.label(it)} key" },
            footer = "Audio is sent only to the provider selected here. Keys stay encrypted on this phone.",
        )
    }
}

/* ── 2 · Cleanup & modes ─────────────────────────────────────────────────── */

@Composable
fun CleanupSpoke(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())

    SpokeScaffold("Cleanup & modes", onBack) {
        // "Default tap mode" used to sit orphaned between "Usage today" and
        // "Delivery & avatar". It belongs with the modes it names.
        SectionCard("Default tap mode") {
            Text(
                "What a single tap on Foxy does. Long-press always offers all three.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ModeSegmented(
                selected = settings.defaultMode,
                onSelect = { scope.launch { container.settingsStore.setDefaultMode(it) } },
            )
        }

        // The user already met these in onboarding — reinforce, don't re-explain.
        ModeExample("Raw", "the meeting is at 4 pm tomorrow", "the meeting is at 4 pm tomorrow")
        ModeExample("Clean", "uhh so the meeting tomorrow at 4 i think", "The meeting tomorrow is at 4.")
        ModeExample("Draft", "tell saurabh i'll be late tomorrow", "Hi Saurabh, just letting you know I'll be running late tomorrow…")

        SectionCard("Cleanup service") {
            Text(
                "Raw mode skips cleanup entirely. Clean and Draft use the provider here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProviderChips(
                options = ProviderCatalog.llmProviders,
                selected = settings.llmProvider,
                onSelect = { scope.launch { container.settingsStore.setLlmProvider(it) } },
            )
        }

        SectionCard("Cleanup model") {
            ModelChips(
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
        }

        KeySection(
            selectedProvider = settings.llmProvider,
            providers = ProviderCatalog.llmProviders,
            keyFor = ::llmKeyFor,
            secrets = container.secrets,
            labelFor = { "${ProviderCatalog.label(it)} cleanup key" },
            footer = "Groq's cleanup key is optional — it falls back to your Groq speech key.",
        )
    }
}

/** One mode's say→get example, mirroring onboarding's `ModeCard`. */
@Composable
private fun ModeExample(tag: String, said: String, got: String) {
    SectionCard(tag) {
        ExampleLine("You say", said)
        ExampleLine("You get", got)
    }
}

@Composable
private fun ExampleLine(label: String, text: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // widthIn(min=), not a fixed size(): a hard-sized Text clips at
            // increased font scale, and One UI's font-size slider is well used.
            modifier = Modifier.widthIn(min = 56.dp),
        )
        Text(
            "\"$text\"",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

/* ── Shared provider/model controls ──────────────────────────────────────── */

/**
 * Provider selection.
 *
 * Chips rather than a segmented button, deliberately: segmented buttons want a
 * small set of short, equal-weight labels, and "ElevenLabs"/"Deepgram" in a
 * 4-way split on a phone would truncate. The selected provider's summary renders
 * below instead of being crammed into every chip — the old code rendered
 * `"$label · $summary"` inside each one, which is what made them so wide.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderChips(
    options: List<ProviderCatalog.ProviderOption>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option.id,
                onClick = { onSelect(option.id) },
                label = { Text(option.label) },
            )
        }
    }
    options.firstOrNull { it.id == selected }?.let {
        Text(
            it.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelChips(
    options: List<ProviderCatalog.ModelOption>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option.id,
                onClick = { onSelect(option.id) },
                label = { Text(option.label) },
            )
        }
    }
    options.firstOrNull { it.id == selected }?.let {
        Text(
            it.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Three peer options, short labels, always visible — segmented earns it here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSegmented(selected: DictationMode, onSelect: (DictationMode) -> Unit) {
    val modes = listOf(DictationMode.RAW, DictationMode.CLEANED, DictationMode.REFORMATTED)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
            ) { Text(mode.label) }
        }
    }
}

/* ── provider id → keystore slot ─────────────────────────────────────────── */

private fun sttKeyFor(provider: String): SecureKeyStore.Key = when (provider) {
    ProviderCatalog.STT_OPENAI -> SecureKeyStore.Key.OpenAiStt
    ProviderCatalog.STT_DEEPGRAM -> SecureKeyStore.Key.DeepgramStt
    ProviderCatalog.STT_ELEVENLABS -> SecureKeyStore.Key.ElevenLabsStt
    else -> SecureKeyStore.Key.GroqStt
}

private fun llmKeyFor(provider: String): SecureKeyStore.Key = when (provider) {
    ProviderCatalog.LLM_OPENAI -> SecureKeyStore.Key.OpenAiLlm
    ProviderCatalog.LLM_GEMINI -> SecureKeyStore.Key.GeminiLlm
    else -> SecureKeyStore.Key.GroqLlm
}
