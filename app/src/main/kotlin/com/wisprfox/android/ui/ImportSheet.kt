package com.wisprfox.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wisprfox.android.core.ImportConfig
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.provider.ProviderCatalog

/**
 * Bottom sheet that configures a batch import: which speech-to-text model, the
 * output style (Raw / Clean / Draft — the same single choice used everywhere
 * else in the app), and, when cleanup runs, which LLM. Defaults are Whisper
 * Large v3 on Groq for transcription and Gemini 3.5 Flash for cleanup. The
 * primary button hands control to the caller's file picker.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportSheet(
    config: ImportConfig,
    onConfigChange: (ImportConfig) -> Unit,
    geminiReady: Boolean,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Import audio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Transcribe voice notes, call recordings, or any audio file from this " +
                    "phone. Long clips are chunked automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── Transcription model ──────────────────────────────────────────
            Section("Transcribe with") {
                ChipFlow(
                    options = ProviderCatalog.sttProviders.map { it.id to it.label },
                    selected = config.sttProvider,
                    onSelect = { provider ->
                        onConfigChange(
                            config.copy(
                                sttProvider = provider,
                                sttModel = ProviderCatalog.defaultSttModel(provider),
                            )
                        )
                    },
                )
                ChipFlow(
                    options = ProviderCatalog.sttModelsFor(config.sttProvider).map { it.id to it.label },
                    selected = config.sttModel,
                    onSelect = { onConfigChange(config.copy(sttModel = it)) },
                )
            }

            // ── Output style (single choice, matches the rest of the app) ────
            Section("Output") {
                ChipFlow(
                    options = listOf(
                        DictationMode.RAW to "Raw transcript",
                        DictationMode.CLEANED to "Clean up",
                        DictationMode.REFORMATTED to "Draft",
                    ),
                    selected = config.mode,
                    onSelect = { onConfigChange(config.copy(mode = it)) },
                )
                Text(
                    when (config.mode) {
                        DictationMode.RAW -> "Just the transcript, no LLM."
                        DictationMode.REFORMATTED -> "Treats the audio as a brief and returns a polished draft."
                        else -> "Adds punctuation and paragraphs while keeping your words."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Cleanup model (only when an LLM step runs) ───────────────────
            if (config.mode.usesLlm) {
                Section("Clean up with") {
                    ChipFlow(
                        options = ProviderCatalog.llmProviders.map { it.id to it.label },
                        selected = config.llmProvider,
                        onSelect = { provider ->
                            onConfigChange(
                                config.copy(
                                    llmProvider = provider,
                                    llmModel = ProviderCatalog.defaultLlmModel(provider),
                                )
                            )
                        },
                    )
                    ChipFlow(
                        options = ProviderCatalog.llmModelsFor(config.llmProvider).map { it.id to it.label },
                        selected = config.llmModel,
                        onSelect = { onConfigChange(config.copy(llmModel = it)) },
                    )
                    if (config.llmProvider == ProviderCatalog.LLM_GEMINI && !geminiReady) {
                        Text(
                            "Add a Gemini key in Settings, or the transcript is delivered raw.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.FileUpload, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text("  Choose files & transcribe")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipFlow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}
