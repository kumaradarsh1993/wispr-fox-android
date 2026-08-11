package com.wisprfox.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.ImportConfig
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.settings.AppSettings
import java.text.DateFormat
import java.util.Date

/** Android-first upload workspace used by both file picking and share targets. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UploadScreen(onOpenSettings: () -> Unit, onSelectTab: (NavTab) -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())
    val staged by container.importController.staged.collectAsState()
    var autoMessage by remember { mutableStateOf<String?>(null) }
    var config by remember(settings) {
        mutableStateOf(
            ImportConfig(
                sttProvider = settings.sttProvider,
                sttModel = settings.sttModel,
                mode = DictationMode.CLEANED,
                llmProvider = settings.llmProvider,
                llmModel = settings.activeLlmModel,
                strongProvider = settings.strongProvider,
                strongModel = settings.strongModel,
            ),
        )
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        container.importController.stageSharedUris(uris)
    }
    val missingKeys = buildList {
        container.providerKeys.stt(config.sttProvider).takeUnless { it.ready }?.let {
            add("${it.providerLabel} key for transcription")
        }
        if (config.generateCleaned) container.providerKeys.llm(config.llmProvider).takeUnless { it.ready }?.let {
            add("${it.providerLabel} key for cleanup")
        }
        if (config.generateDraft || config.generateMeetingNotes) container.providerKeys.llm(config.strongProvider).takeUnless { it.ready }?.let {
            add("${it.providerLabel} key for Draft/Meeting")
        }
    }.distinct()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Upload recordings") }) },
        bottomBar = { WisprBottomBar(NavTab.UPLOAD, onSelectTab) },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(
                "Share from Samsung Voice Recorder or choose files here. Shared audio is copied into Foxy immediately so Android cannot revoke access mid-process.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.FileUpload, null)
                Text("  Choose audio files")
            }

            if (staged.isNotEmpty()) SectionCard("Files · ${staged.size}") {
                staged.forEachIndexed { index, file ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${index + 1}. ${file.displayName}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                buildString {
                                    append(formatBytes(file.bytes))
                                    file.modifiedAt?.let { append(" · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}") }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { container.importController.reorderStaged(index, index - 1) }, enabled = index > 0) {
                            Icon(Icons.Filled.ArrowUpward, "Move earlier")
                        }
                        IconButton(onClick = { container.importController.reorderStaged(index, index + 1) }, enabled = index < staged.lastIndex) {
                            Icon(Icons.Filled.ArrowDownward, "Move later")
                        }
                        IconButton(onClick = { container.importController.removeStaged(file.id) }) { Icon(Icons.Filled.Close, "Remove") }
                    }
                }
                if (staged.size > 1) {
                    CheckRow("Merge into one recording, in this order", config.merge) {
                        config = config.copy(merge = it)
                    }
                    Text(
                        "Foxy joins the audio locally before uploading it. Reorder the list above to set the meeting timeline.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard("1 · Transcription") {
                Text("Provider", style = MaterialTheme.typography.labelMedium)
                ChoiceFlow(ProviderCatalog.sttProviders.map { it.id to it.label }, config.sttProvider) { provider ->
                    val model = ProviderCatalog.defaultSttModel(provider)
                    if (config.diarizationEnabled) {
                        val compatible = ProviderCatalog.compatibleDiarizationSelection(provider, model)
                        config = config.copy(sttProvider = compatible.first, sttModel = compatible.second)
                        if (compatible.first != provider) autoMessage = "Switched to Deepgram Nova-3 because speaker labels are on."
                    } else config = config.copy(sttProvider = provider, sttModel = model)
                }
                ChoiceFlow(ProviderCatalog.sttModelsFor(config.sttProvider).map { it.id to it.label }, config.sttModel) {
                    config = config.copy(sttModel = it)
                }
                CheckRow("Label speakers (meeting)", config.diarizationEnabled) { enabled ->
                    config = if (enabled) {
                        val compatible = ProviderCatalog.compatibleDiarizationSelection(config.sttProvider, config.sttModel)
                        if (compatible.first != config.sttProvider) autoMessage = "Switched to Deepgram Nova-3 because the selected model cannot label speakers."
                        config.copy(diarizationEnabled = true, sttProvider = compatible.first, sttModel = compatible.second)
                    } else config.copy(diarizationEnabled = false)
                }
                autoMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }

            SectionCard("2 · Outputs") {
                CheckRow("Cleaned transcript", config.generateCleaned) { config = config.copy(generateCleaned = it) }
                CheckRow("Draft", config.generateDraft) { config = config.copy(generateDraft = it) }
                CheckRow("Meeting Notes", config.generateMeetingNotes) { config = config.copy(generateMeetingNotes = it) }
                Text("Cleanup model", style = MaterialTheme.typography.labelMedium)
                ChoiceFlow(ProviderCatalog.llmProviders.map { it.id to it.label }, config.llmProvider) {
                    config = config.copy(llmProvider = it, llmModel = ProviderCatalog.defaultLlmModel(it))
                }
                ChoiceFlow(ProviderCatalog.llmModelsFor(config.llmProvider).map { it.id to it.label }, config.llmModel) {
                    config = config.copy(llmModel = it)
                }
                if (config.generateDraft || config.generateMeetingNotes) {
                    Text("Strong model · Draft & Meeting", style = MaterialTheme.typography.labelMedium)
                    ChoiceFlow(ProviderCatalog.llmProviders.map { it.id to it.label }, config.strongProvider) {
                        config = config.copy(strongProvider = it, strongModel = ProviderCatalog.defaultStrongLlmModel(it))
                    }
                    ChoiceFlow(ProviderCatalog.llmModelsFor(config.strongProvider).map { it.id to it.label }, config.strongModel) {
                        config = config.copy(strongModel = it)
                    }
                }
            }

            Button(
                onClick = { container.importController.processStaged(config); onSelectTab(NavTab.HISTORY) },
                enabled = staged.isNotEmpty() && missingKeys.isEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val outputCount = if (config.merge && staged.size > 1) 1 else staged.size
                Text("Process ${outputCount.takeIf { it > 0 } ?: ""} recording${if (outputCount == 1) "" else "s"}")
            }
            if (missingKeys.isNotEmpty()) {
                Text(
                    "Add ${missingKeys.joinToString()} before processing. Your staged files are safe if you leave this screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text("Defaults and provider keys", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.End))
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Open AI & model settings") }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChecked)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceFlow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        options.forEach { (id, label) -> FilterChip(selected == id, { onSelect(id) }, { Text(label) }) }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
