package com.wisprfox.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.AppState
import com.wisprfox.android.core.ImportConfig
import com.wisprfox.android.core.RecentRecording
import com.wisprfox.android.core.RecentRecordings
import com.wisprfox.android.core.UploadKind
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Upload (Nov 2026 revamp). One screen, no scrolling on an S23 Ultra:
 *  1. your 3 newest recordings to tick, plus a Files button for anything else;
 *  2. three presets: Transcript, Meeting, Custom;
 *  3. compact options the presets fill in (editing one switches to Custom);
 *  4. one Transcribe button.
 * A file shared in from Voice Recorder opens a two-choice sheet first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(onOpenSettings: () -> Unit, onSelectTab: (NavTab) -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())
    val staged by container.importController.staged.collectAsState()
    val app by AppState.state.collectAsState()
    val keys = container.providerKeys

    // ── Preset + options ────────────────────────────────────────────────────
    var preset by remember { mutableStateOf<UploadKind?>(null) }
    var opts by remember { mutableStateOf<UploadOptions?>(null) }
    LaunchedEffect(Unit) {
        val s = container.settingsStore.settings.first()
        val p = UploadKind.parse(s.lastUploadKind)
        preset = p
        opts = UploadOptions.forPreset(p, s)
    }
    val current = opts ?: UploadOptions.forPreset(UploadKind.parse(settings.lastUploadKind), settings)
    fun choosePreset(p: UploadKind) {
        preset = p
        opts = UploadOptions.forPreset(p, settings)
        scope.launch { container.settingsStore.setUploadKind(p.raw) }
    }
    /** Any manual edit makes this a Custom run (the edit is kept). */
    fun edit(change: (UploadOptions) -> UploadOptions) {
        opts = change(current)
        if (preset != UploadKind.CUSTOM) {
            preset = UploadKind.CUSTOM
            scope.launch { container.settingsStore.setUploadKind(UploadKind.CUSTOM.raw) }
        }
    }

    // ── Recent recordings ───────────────────────────────────────────────────
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    var recent by remember { mutableStateOf<List<RecentRecording>>(emptyList()) }
    var canRead by remember { mutableStateOf(RecentRecordings.hasPermission(ctx)) }
    LaunchedEffect(tick, settings.recordingsFolder, canRead) {
        canRead = RecentRecordings.hasPermission(ctx)
        recent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            RecentRecordings.newest(ctx, settings.recordingsFolder)
        }
    }
    val askRead = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canRead = granted
        tick++
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        container.importController.stageSharedUris(uris)
    }
    fun isStaged(r: RecentRecording) = staged.any { it.displayName == r.name }
    fun toggle(r: RecentRecording) {
        val match = staged.firstOrNull { it.displayName == r.name }
        if (match != null) container.importController.removeStaged(match.id)
        else container.importController.stageSharedUris(listOf(r.uri))
    }

    var merge by remember { mutableStateOf(false) }
    val missing = current.missingKeys(keys)
    fun start(o: UploadOptions) {
        container.importController.processStaged(o.toImportConfig(settings, merge && staged.size > 1))
        onSelectTab(if (o.labelSpeakers) NavTab.CONVERSATIONS else NavTab.HISTORY)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Upload") },
                actions = {
                    TextButton(onClick = { picker.launch(arrayOf("audio/*")) }) {
                        Icon(Icons.Filled.FolderOpen, null, Modifier.size(Sizes.actionIcon))
                        Text("  Files", style = MaterialTheme.typography.labelLarge)
                    }
                },
            )
        },
        bottomBar = { WisprBottomBar(NavTab.UPLOAD, onSelectTab) },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            // 1. What to transcribe
            Label("Recent recordings")
            when {
                !canRead -> OutlinedButton(onClick = { askRead.launch(RecentRecordings.permission) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Show my latest recordings")
                }
                recent.isEmpty() -> Muted("No recordings in ${settings.recordingsFolder.ifBlank { "any folder" }}. Change the folder in Settings → Storage, or use Files.")
                else -> recent.forEach { r ->
                    RecentRow(r, checked = isStaged(r)) { toggle(r) }
                }
            }
            val others = staged.filter { s -> recent.none { it.name == s.displayName } }
            others.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { container.importController.removeStaged(f.id) }, modifier = Modifier.size(Sizes.iconButton)) {
                        Icon(Icons.Filled.Close, "Remove", Modifier.size(Sizes.actionIcon))
                    }
                }
            }
            if (staged.size > 1) CheckLine("Join into one recording, in this order", merge) { merge = it }

            // 2. Presets
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                PresetButton("Transcript", Icons.Filled.Notes, preset == UploadKind.VOICE_NOTE, Modifier.weight(1f)) { choosePreset(UploadKind.VOICE_NOTE) }
                PresetButton("Meeting", Icons.Filled.Forum, preset == UploadKind.CONVERSATION, Modifier.weight(1f)) { choosePreset(UploadKind.CONVERSATION) }
                PresetButton("Custom", Icons.Filled.Tune, preset == UploadKind.CUSTOM, Modifier.weight(1f)) { choosePreset(UploadKind.CUSTOM) }
            }

            // 3. Options
            HorizontalDivider()
            OptionsBlock(current, onEdit = ::edit)

            // 4. Go
            Button(onClick = { start(current) }, enabled = staged.isNotEmpty() && missing.isEmpty(), modifier = Modifier.fillMaxWidth()) {
                val n = if (merge && staged.size > 1) 1 else staged.size
                Text(if (n == 0) "Pick a recording" else "Transcribe $n recording${if (n == 1) "" else "s"}")
            }
            if (missing.isNotEmpty()) {
                Text(
                    "Add ${missing.joinToString()} in Settings → AI & models.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onOpenSettings),
                )
            }
            Spacer(Modifier.height(Space.sm))
        }
    }

    // A share from Voice Recorder: two big choices, or the full screen.
    if (app.shareChoicePending) {
        ModalBottomSheet(onDismissRequest = { AppState.setShareChoicePending(false) }) {
            Column(Modifier.padding(horizontal = Space.screen).padding(bottom = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(
                    if (staged.size == 1) staged.first().displayName else "${staged.size} recordings",
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    listOf(UploadKind.VOICE_NOTE to "Transcript", UploadKind.CONVERSATION to "Meeting").forEach { (kind, label) ->
                        val o = UploadOptions.forPreset(kind, settings)
                        Button(
                            onClick = {
                                AppState.setShareChoicePending(false)
                                scope.launch { container.settingsStore.setUploadKind(kind.raw) }
                                start(o)
                            },
                            enabled = staged.isNotEmpty() && o.missingKeys(keys).isEmpty(),
                            modifier = Modifier.weight(1f).height(Sizes.touch + Space.lg),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(label, style = MaterialTheme.typography.titleSmall)
                                Text(if (kind == UploadKind.VOICE_NOTE) "Just the words" else "Who said what", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                TextButton(onClick = { AppState.setShareChoicePending(false) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("More options")
                }
            }
        }
    }
}

/** What one Upload run does. The presets are just three starting values. */
data class UploadOptions(
    val sttProvider: String,
    val sttModel: String,
    val labelSpeakers: Boolean,
    /** Meeting: also keep a Whisper transcript of the same audio. */
    val addWhisper: Boolean,
    val clean: Boolean,
    val draft: Boolean,
    val notes: Boolean,
    val llmProvider: String,
    val llmModel: String,
    /** Set when ticking Label speakers changed the engine, to say so. */
    val switchedForSpeakers: Boolean = false,
) {
    fun missingKeys(keys: com.wisprfox.android.core.ProviderKeyReadiness): List<String> = buildList {
        keys.stt(sttProvider).takeUnless { it.ready }?.let { add("a ${it.providerLabel} key") }
        if (labelSpeakers && addWhisper) keys.stt(ProviderCatalog.ACCURACY_PROVIDER).takeUnless { it.ready }?.let { add("a ${it.providerLabel} key") }
        if (clean || draft || notes) keys.llm(llmProvider).takeUnless { it.ready }?.let { add("a ${it.providerLabel} key") }
    }.distinct()

    fun toImportConfig(settings: AppSettings, merge: Boolean) = ImportConfig(
        sttProvider = sttProvider,
        sttModel = sttModel,
        mode = when {
            labelSpeakers -> DictationMode.RAW
            draft -> DictationMode.REFORMATTED
            clean -> DictationMode.CLEANED
            else -> DictationMode.RAW
        },
        llmProvider = llmProvider,
        llmModel = llmModel,
        diarizationEnabled = labelSpeakers,
        generateCleaned = clean && !labelSpeakers,
        generateDraft = draft && !labelSpeakers,
        generateMeetingNotes = notes && labelSpeakers,
        strongProvider = if (notes || draft) llmProvider else settings.strongProvider,
        strongModel = if (notes || draft) llmModel else settings.strongModel,
        merge = merge,
        accuracyPass = labelSpeakers && addWhisper,
    )

    companion object {
        fun forPreset(p: UploadKind, s: AppSettings): UploadOptions = when (p) {
            UploadKind.VOICE_NOTE -> UploadOptions(
                sttProvider = ProviderCatalog.ACCURACY_PROVIDER, sttModel = ProviderCatalog.ACCURACY_MODEL,
                labelSpeakers = false, addWhisper = false, clean = false, draft = false, notes = false,
                llmProvider = s.llmProvider, llmModel = s.activeLlmModel,
            )
            UploadKind.CONVERSATION -> UploadOptions(
                sttProvider = ProviderCatalog.STT_DEEPGRAM, sttModel = "nova-3",
                labelSpeakers = true, addWhisper = true, clean = false, draft = false, notes = false,
                llmProvider = s.strongProvider, llmModel = s.strongModel,
            )
            UploadKind.CUSTOM -> UploadOptions(
                sttProvider = ProviderCatalog.ACCURACY_PROVIDER, sttModel = ProviderCatalog.ACCURACY_MODEL,
                labelSpeakers = false, addWhisper = true, clean = true, draft = false, notes = false,
                llmProvider = s.llmProvider, llmModel = s.activeLlmModel,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsBlock(o: UploadOptions, onEdit: ((UploadOptions) -> UploadOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Label("Transcribe")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Dropdown(
                value = ProviderCatalog.sttProviders.firstOrNull { it.id == o.sttProvider }?.label ?: o.sttProvider,
                options = ProviderCatalog.sttProviders.map { it.id to it.label },
                modifier = Modifier.weight(1f),
            ) { id -> onEdit { it.copy(sttProvider = id, sttModel = ProviderCatalog.defaultSttModel(id), labelSpeakers = it.labelSpeakers && id == ProviderCatalog.STT_DEEPGRAM, switchedForSpeakers = false) } }
            Dropdown(
                value = ProviderCatalog.sttModelsFor(o.sttProvider).firstOrNull { it.id == o.sttModel }?.label ?: o.sttModel,
                options = ProviderCatalog.sttModelsFor(o.sttProvider).map { it.id to it.label },
                modifier = Modifier.weight(1.3f),
            ) { id -> onEdit { it.copy(sttModel = id) } }
        }
        CheckLine("Label speakers", o.labelSpeakers) { on ->
            onEdit {
                if (on) it.copy(labelSpeakers = true, sttProvider = ProviderCatalog.STT_DEEPGRAM, sttModel = "nova-3",
                    switchedForSpeakers = it.sttProvider != ProviderCatalog.STT_DEEPGRAM)
                else it.copy(labelSpeakers = false, switchedForSpeakers = false)
            }
        }
        if (o.labelSpeakers) {
            if (o.switchedForSpeakers) Muted("Switched to Deepgram Nova-3, which can tell speakers apart.")
            CheckLine("Also keep a Whisper version (better wording)", o.addWhisper) { on -> onEdit { it.copy(addWhisper = on) } }
        }

        Label("Outputs")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (!o.labelSpeakers) {
                FilterChip(o.clean, { onEdit { it.copy(clean = !it.clean) } }, { Text("Clean transcript") })
                FilterChip(o.draft, { onEdit { it.copy(draft = !it.draft) } }, { Text("Draft") })
            } else {
                FilterChip(o.notes, { onEdit { it.copy(notes = !it.notes) } }, { Text("Meeting notes") })
            }
        }
        if (o.clean || o.draft || o.notes) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Dropdown(
                    value = ProviderCatalog.llmProviders.firstOrNull { it.id == o.llmProvider }?.label ?: o.llmProvider,
                    options = ProviderCatalog.llmProviders.map { it.id to it.label },
                    modifier = Modifier.weight(1f),
                ) { id -> onEdit { it.copy(llmProvider = id, llmModel = ProviderCatalog.defaultLlmModel(id)) } }
                Dropdown(
                    value = ProviderCatalog.llmModelsFor(o.llmProvider).firstOrNull { it.id == o.llmModel }?.label ?: o.llmModel,
                    options = ProviderCatalog.llmModelsFor(o.llmProvider).map { it.id to it.label },
                    modifier = Modifier.weight(1.3f),
                ) { id -> onEdit { it.copy(llmModel = id) } }
            }
        }
    }
}

@Composable
private fun Dropdown(value: String, options: List<Pair<String, String>>, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.md)) {
            Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(Sizes.actionIcon))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onSelect(id) })
            }
        }
    }
}

@Composable
private fun PresetButton(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(vertical = Space.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(Sizes.rowIcon))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun RecentRow(r: RecentRecording, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(r.name.substringBeforeLast('.'), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatDuration(r.durationMs)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(r.modifiedAtMs))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CheckLine(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChecked(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChecked)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

@Composable
private fun Muted(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
