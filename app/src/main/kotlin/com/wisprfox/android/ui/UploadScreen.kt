package com.wisprfox.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.ImportConfig
import com.wisprfox.android.core.UploadKind
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Android-first upload workspace used by both file picking and the share
 * target (a share from Samsung Voice Recorder lands here). It asks one question
 * first, as two large buttons: **Voice note** (one speaker → Whisper + Clean)
 * or **Conversation** (several people → speaker labels, optional Whisper
 * transcript, optional meeting notes). The last answer is remembered.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UploadScreen(onOpenSettings: () -> Unit, onSelectTab: (NavTab) -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())
    val staged by container.importController.staged.collectAsState()

    // Read the remembered choice once from the store (not the flow's initial
    // default) so the screen opens on the user's last answer.
    var kind by remember { mutableStateOf<UploadKind?>(null) }
    LaunchedEffect(Unit) {
        if (kind == null) kind = UploadKind.parse(container.settingsStore.settings.first().lastUploadKind)
    }
    val selectedKind = kind ?: UploadKind.parse(settings.lastUploadKind)
    fun choose(k: UploadKind) {
        kind = k
        scope.launch { container.settingsStore.setUploadKind(k.raw) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        container.importController.stageSharedUris(uris)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Upload recordings") }) },
        bottomBar = { WisprBottomBar(NavTab.UPLOAD, onSelectTab) },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md), modifier = Modifier.fillMaxWidth()) {
                KindButton(
                    title = "Voice note",
                    body = "One speaker",
                    icon = Icons.Filled.RecordVoiceOver,
                    selected = selectedKind == UploadKind.VOICE_NOTE,
                    modifier = Modifier.weight(1f),
                ) { choose(UploadKind.VOICE_NOTE) }
                KindButton(
                    title = "Conversation",
                    body = "2+ people",
                    icon = Icons.Filled.Forum,
                    selected = selectedKind == UploadKind.CONVERSATION,
                    modifier = Modifier.weight(1f),
                ) { choose(UploadKind.CONVERSATION) }
            }

            OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.FileUpload, null)
                Text("  Choose audio files")
            }
            if (staged.isEmpty()) {
                Text(
                    "Share from Samsung Voice Recorder or choose files here. Shared audio is copied into Foxy immediately so Android cannot revoke access mid-process.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            var merge by remember { mutableStateOf(false) }
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
                    CheckRow("Merge into one recording, in this order", merge) { merge = it }
                    Text(
                        "Foxy joins the audio locally before uploading it. Reorder the list above to set the timeline.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (selectedKind) {
                UploadKind.VOICE_NOTE -> VoiceNoteOptions(settings, merge, staged.size, onSelectTab)
                UploadKind.CONVERSATION -> ConversationOptions(settings, merge, staged.size, onSelectTab)
            }

            Text("Defaults and provider keys", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.End))
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Open AI & model settings") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindButton(
    title: String,
    body: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 96.dp),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Voice note: Whisper (Groq by default) then Clean, with the old Quick transcribe one-tap path. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceNoteOptions(settings: AppSettings, merge: Boolean, stagedCount: Int, onSelectTab: (NavTab) -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    fun defaultConfig() = ImportConfig(
        sttProvider = settings.sttProvider,
        sttModel = settings.sttModel,
        mode = DictationMode.CLEANED,
        llmProvider = settings.llmProvider,
        llmModel = settings.activeLlmModel,
        strongProvider = settings.strongProvider,
        strongModel = settings.strongModel,
    )
    var config by remember(settings) { mutableStateOf(defaultConfig()) }
    var showOptions by remember { mutableStateOf(false) }
    fun missingFor(cfg: ImportConfig): List<String> = buildList {
        container.providerKeys.stt(cfg.sttProvider).takeUnless { it.ready }?.let {
            add("${it.providerLabel} key for transcription")
        }
        if (cfg.generateCleaned) container.providerKeys.llm(cfg.llmProvider).takeUnless { it.ready }?.let {
            add("${it.providerLabel} key for cleanup")
        }
        if (cfg.generateDraft) container.providerKeys.llm(cfg.strongProvider).takeUnless { it.ready }?.let {
            add("${it.providerLabel} key for Draft")
        }
    }.distinct()

    if (stagedCount > 0) {
        val quickConfig = defaultConfig().copy(merge = merge)
        val quickMissing = missingFor(quickConfig)
        SectionCard("Quick transcribe") {
            Text(
                "One tap, your defaults: ${ProviderCatalog.shortModel(quickConfig.sttModel)} transcription into a cleaned transcript (${ProviderCatalog.shortModel(quickConfig.llmModel)}). Runs in the background — leave the app and you'll get a notification with the text on your clipboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { container.importController.processStaged(quickConfig); onSelectTab(NavTab.HISTORY) },
                enabled = quickMissing.isEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (stagedCount == 1) "Quick transcribe" else "Quick transcribe $stagedCount files")
            }
            if (quickMissing.isNotEmpty()) {
                Text(
                    "Add ${quickMissing.joinToString()} first, or pick other models under Options.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    TextButton(onClick = { showOptions = !showOptions }) { Text(if (showOptions) "Hide options" else "Options") }
    if (showOptions) {

        SectionCard("Transcription") {
            ChoiceFlow(ProviderCatalog.sttProviders.map { it.id to it.label }, config.sttProvider) { provider ->
                config = config.copy(sttProvider = provider, sttModel = ProviderCatalog.defaultSttModel(provider))
            }
            ChoiceFlow(ProviderCatalog.sttModelsFor(config.sttProvider).map { it.id to it.label }, config.sttModel) {
                config = config.copy(sttModel = it)
            }
        }
        SectionCard("Outputs") {
            CheckRow("Cleaned transcript", config.generateCleaned) { config = config.copy(generateCleaned = it) }
            CheckRow("Draft", config.generateDraft) { config = config.copy(generateDraft = it) }
            Text("Cleanup model", style = MaterialTheme.typography.labelMedium)
            ChoiceFlow(ProviderCatalog.llmProviders.map { it.id to it.label }, config.llmProvider) {
                config = config.copy(llmProvider = it, llmModel = ProviderCatalog.defaultLlmModel(it))
            }
            ChoiceFlow(ProviderCatalog.llmModelsFor(config.llmProvider).map { it.id to it.label }, config.llmModel) {
                config = config.copy(llmModel = it)
            }
            if (config.generateDraft) {
                Text("Strong model · Draft", style = MaterialTheme.typography.labelMedium)
                ChoiceFlow(ProviderCatalog.llmProviders.map { it.id to it.label }, config.strongProvider) {
                    config = config.copy(strongProvider = it, strongModel = ProviderCatalog.defaultStrongLlmModel(it))
                }
                ChoiceFlow(ProviderCatalog.llmModelsFor(config.strongProvider).map { it.id to it.label }, config.strongModel) {
                    config = config.copy(strongModel = it)
                }
            }
        }
        val missing = missingFor(config)
        val outputCount = if (merge && stagedCount > 1) 1 else stagedCount
        Button(
            onClick = {
                container.importController.processStaged(
                    config.copy(merge = merge, diarizationEnabled = false, generateMeetingNotes = false, accuracyPass = false),
                )
                onSelectTab(NavTab.HISTORY)
            },
            enabled = stagedCount > 0 && missing.isEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Process ${outputCount.takeIf { it > 0 } ?: ""} voice note${if (outputCount == 1) "" else "s"}")
        }
        if (missing.isNotEmpty()) MissingKeys(missing)
    }
}

/**
 * Conversation: the speakers pass on the whole file (Deepgram Nova-3 by
 * default, ElevenLabs Scribe v2 as the alternative), plus an optional Whisper
 * transcript and optional meeting notes, each defaulting on when its key exists.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConversationOptions(settings: AppSettings, merge: Boolean, stagedCount: Int, onSelectTab: (NavTab) -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val keys = container.providerKeys
    val initialEngine = ProviderCatalog.compatibleDiarizationSelection(settings.diarizationProvider, settings.diarizationModel)
    var engine by remember(settings.diarizationProvider, settings.diarizationModel) { mutableStateOf(initialEngine) }
    var whisper by remember(settings) { mutableStateOf(keys.stt(ProviderCatalog.ACCURACY_PROVIDER).ready) }
    var notes by remember(settings) { mutableStateOf(keys.llm(settings.strongProvider).ready) }
    var strongProvider by remember(settings) { mutableStateOf(settings.strongProvider) }
    var strongModel by remember(settings) { mutableStateOf(settings.strongModel) }
    var showOptions by remember { mutableStateOf(false) }

    val missing = buildList {
        keys.stt(engine.first).takeUnless { it.ready }?.let { add("${it.providerLabel} key for speaker labels") }
        if (whisper) keys.stt(ProviderCatalog.ACCURACY_PROVIDER).takeUnless { it.ready }?.let { add("${it.providerLabel} key for the Whisper transcript") }
        if (notes) keys.llm(strongProvider).takeUnless { it.ready }?.let { add("${it.providerLabel} key for meeting notes") }
    }.distinct()

    SectionCard("Conversation") {
        Text(
            "Speakers are labelled on the whole recording in one request, so Speaker 1 stays the same person throughout.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Speaker labels", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            FilterChip(
                selected = engine.first == ProviderCatalog.STT_DEEPGRAM,
                onClick = { engine = ProviderCatalog.STT_DEEPGRAM to "nova-3" },
                label = { Text("Deepgram Nova-3") },
            )
            FilterChip(
                selected = engine.first == ProviderCatalog.STT_ELEVENLABS,
                onClick = { engine = ProviderCatalog.STT_ELEVENLABS to "scribe_v2" },
                label = { Text("ElevenLabs Scribe v2") },
            )
        }
        CheckRow("Also make a Whisper transcript", whisper) { whisper = it }
        Text(
            "Whisper Large v3 on Groq: more accurate wording, no speakers. Kept as a second transcript.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CheckRow("Write meeting notes", notes) { notes = it }
        TextButton(onClick = { showOptions = !showOptions }) { Text(if (showOptions) "Hide options" else "Options") }
        if (showOptions && notes) {
            Text("Notes model", style = MaterialTheme.typography.labelMedium)
            ChoiceFlow(ProviderCatalog.llmProviders.map { it.id to it.label }, strongProvider) {
                strongProvider = it
                strongModel = ProviderCatalog.defaultStrongLlmModel(it)
            }
            ChoiceFlow(ProviderCatalog.llmModelsFor(strongProvider).map { it.id to it.label }, strongModel) { strongModel = it }
        }
    }

    val outputCount = if (merge && stagedCount > 1) 1 else stagedCount
    Button(
        onClick = {
            container.importController.processStaged(
                ImportConfig(
                    sttProvider = engine.first,
                    sttModel = engine.second,
                    mode = DictationMode.RAW,
                    llmProvider = settings.llmProvider,
                    llmModel = settings.activeLlmModel,
                    diarizationEnabled = true,
                    generateCleaned = false,
                    generateDraft = false,
                    generateMeetingNotes = notes,
                    strongProvider = strongProvider,
                    strongModel = strongModel,
                    merge = merge,
                    accuracyPass = whisper,
                ),
            )
            onSelectTab(NavTab.CONVERSATIONS)
        },
        enabled = stagedCount > 0 && missing.isEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Process ${outputCount.takeIf { it > 0 } ?: ""} conversation${if (outputCount == 1) "" else "s"}")
    }
    if (missing.isNotEmpty()) MissingKeys(missing)
}

@Composable
private fun MissingKeys(missing: List<String>) {
    Text(
        "Add ${missing.joinToString()} before processing. Your staged files are safe if you leave this screen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
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
