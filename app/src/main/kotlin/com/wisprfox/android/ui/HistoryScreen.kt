package com.wisprfox.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.AltVersionGenerator
import com.wisprfox.android.core.ProviderKeyReadiness
import com.wisprfox.android.history.AltKind
import com.wisprfox.android.history.Recording
import com.wisprfox.android.history.RecordingStatus
import com.wisprfox.android.history.SpeakerTurnsCodec
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.settings.AppSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.io.File
import kotlinx.coroutines.launch

/**
 * History/Library.
 *
 * The audit's read was that this is the best screen in the app and needed
 * density and grouping rather than rework — the select mode, the always-visible
 * retry chip and the confirm dialogs are all well-judged and are untouched here.
 *
 * What changed:
 *  - **Date group headers** ("Today" / "Yesterday" / …) as sticky headers. Every
 *    row used to print an absolute timestamp, which is noisy and hard to scan.
 *  - **Density**: rows were ~120dp (card + 14dp + timestamp line + badge row +
 *    2-line preview), so ~6 fit a screen. The preview is now the headline and
 *    the meta collapses to one line beneath it.
 *  - **Status noise**: every row rendered "done" as a status label. Status now
 *    only appears when it *isn't* done — i.e. when it means something.
 *  - **Search**, filtering the transcript. There was no way to find anything in
 *    a corpus that only grows.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    initialExpandId: String? = null,
    onBack: () -> Unit,
    onOpenHome: () -> Unit,
    onSelectTab: (NavTab) -> Unit,
) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()
    val recordings by container.recordings.observeRecent().collectAsState(initial = emptyList())
    val player = rememberAudioPlayer()

    // rememberSaveable: select mode and the query survive process death, which
    // `remember` did not.
    var selectMode by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    var expandedIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(initialExpandId) {
        initialExpandId?.let { expandedIds = expandedIds + it }
    }

    fun exitSelectMode() {
        selectMode = false
        selected.clear()
    }

    // Ownership-scoped delete (SYNC_DESIGN.md): deleting an owned row removes it
    // locally AND tombstones the server copy so it's gone from every signed-in
    // device. deleteOwned filters to this device's rows, so passing a mixed
    // selection can never touch another device's history. One applier for the
    // bulk paths (delete-all + multi-select); the per-row path calls the same.
    fun applyDelete(ids: List<String>) {
        scope.launch { container.recordings.deleteOwned(ids) }
    }

    // "Delete all" means all of THIS device's rows only — remote rows survive.
    val ownedIds = remember(recordings) { recordings.filter { !it.remote }.map { it.id } }

    val visible = remember(recordings, query) {
        if (query.isBlank()) recordings
        else recordings.filter { rec ->
            rec.primaryText()?.contains(query, ignoreCase = true) == true ||
                rec.transcript?.contains(query, ignoreCase = true) == true
        }
    }
    val grouped = remember(visible) { visible.groupBy { dayBucket(it.createdAt) } }
    val anyChecked = selected.any { it.value }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(if (selectMode) "${selected.count { it.value }} selected" else "History") },
                navigationIcon = {
                    IconButton(onClick = { if (selectMode) exitSelectMode() else onBack() }) {
                        Icon(
                            if (selectMode) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (selectMode) "Cancel" else "Back",
                        )
                    }
                },
                actions = {
                    if (selectMode) {
                        // Was `onClick = { confirmDeleteSelected = anyChecked }`,
                        // which set the flag to *false* with nothing selected —
                        // so the button silently did nothing. Disable it instead.
                        IconButton(onClick = { confirmDeleteSelected = true }, enabled = anyChecked) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete selected",
                                tint = if (anyChecked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                            Icon(Icons.Filled.Search, contentDescription = if (searchOpen) "Close search" else "Search")
                        }
                        IconButton(onClick = { selectMode = true }, enabled = recordings.isNotEmpty()) {
                            Icon(Icons.Filled.CheckBoxOutlineBlank, contentDescription = "Select")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    // "Delete all" is this device's rows only; if
                                    // every row is synced-from-elsewhere there's
                                    // nothing here we're allowed to delete.
                                    text = { Text("Delete all", color = MaterialTheme.colorScheme.error) },
                                    enabled = ownedIds.isNotEmpty(),
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; confirmDeleteAll = true },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = { WisprBottomBar(NavTab.HISTORY, onSelectTab) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (searchOpen && !selectMode) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search transcripts") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.lg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.screen, vertical = Space.sm),
                )
            }

            if (recordings.isEmpty()) {
                EmptyState(
                    title = "Nothing yet",
                    body = "Recordings you make will appear here — replay the audio, retry, and view Raw / Clean / Draft versions.",
                )
            } else if (visible.isEmpty()) {
                EmptyState(
                    title = "No matches",
                    body = "Nothing in your history matches \"$query\".",
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Space.screen,
                        end = Space.screen,
                        top = Space.sm,
                        bottom = Space.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Space.listGap),
                ) {
                    grouped.forEach { (bucket, rows) ->
                        stickyHeader(key = "h-$bucket") { DayHeader(bucket) }
                        items(rows, key = { it.id }) { rec ->
                            HistoryRow(
                                rec = rec,
                                player = player,
                                selectMode = selectMode,
                                checked = selected[rec.id] == true,
                                onToggleSelect = { selected[rec.id] = !(selected[rec.id] ?: false) },
                                expanded = rec.id in expandedIds,
                                onToggleExpanded = {
                                    expandedIds = if (rec.id in expandedIds) expandedIds - rec.id else expandedIds + rec.id
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDeleteAll) {
        DeleteDialog(
            itemCount = ownedIds.size,
            onDismiss = { confirmDeleteAll = false },
            onConfirm = {
                confirmDeleteAll = false
                applyDelete(ownedIds)
                exitSelectMode()
            },
        )
    }

    if (confirmDeleteSelected) {
        // Defensive: only owned rows are selectable (remote rows have no
        // checkbox), but filter again so a stray remote id can never slip in.
        val ids = selected.filter { it.value }.keys.filter { id ->
            recordings.firstOrNull { it.id == id }?.remote == false
        }
        DeleteDialog(
            itemCount = ids.size,
            onDismiss = { confirmDeleteSelected = false },
            onConfirm = {
                confirmDeleteSelected = false
                applyDelete(ids)
                exitSelectMode()
            },
        )
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.screen),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(Space.sm))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Today" / "Yesterday" / "Mon, Jul 14". Replaces per-row absolute timestamps. */
@Composable
private fun DayHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = Space.sm, bottom = Space.xs),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    rec: Recording,
    player: AudioPlayerState,
    selectMode: Boolean,
    checked: Boolean,
    onToggleSelect: () -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()

    var tab by remember(rec.id) { mutableStateOf(defaultTabFor(rec.mode)) }
    var generating by remember(rec.id) { mutableStateOf(false) }
    var showRerun by remember(rec.id) { mutableStateOf(false) }
    var confirmDelete by remember(rec.id) { mutableStateOf(false) }
    var showReader by remember(rec.id) { mutableStateOf(false) }
    var showSpeakers by remember(rec.id) { mutableStateOf(false) }
    var actionError by remember(rec.id) { mutableStateOf<String?>(null) }

    val isError = rec.status == RecordingStatus.ERROR
    val hasLocalAudio = remember(rec.audioPath) { rec.audioPath.isNotBlank() && File(rec.audioPath).isFile }
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())
    val rowBg = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        rec.isMeeting -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = rowBg),
    ) {
        Column(Modifier.padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (selectMode) Sizes.touch else 0.dp)
                    .combinedClickable(
                        // Remote rows (synced from another device) aren't ours to
                        // delete, so they're not selectable and have no long-press
                        // delete — the affordance is hidden, not just disabled.
                        onClick = {
                            if (selectMode) { if (!rec.remote) onToggleSelect() } else onToggleExpanded()
                        },
                        // Long-press = press-and-hold delete for an owned row
                        // (SYNC_DESIGN.md), the confirm dialog being the gate.
                        onLongClick = { if (!selectMode && !rec.remote) confirmDelete = true },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                if (selectMode && !rec.remote) {
                    Icon(
                        if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    // The transcript is the headline now — it's what the user is
                    // scanning for. The timestamp used to occupy this line while
                    // the text they came for sat two rows below.
                    Text(
                        rec.primaryText()?.takeIf { it.isNotBlank() }?.take(140) ?: "(no transcript yet)",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded && !selectMode) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MetaLine(rec, isError)
                }
                // Copy is the primary recovery action; playback moves into the
                // compact expanded action row.
                if (!selectMode) {
                    IconButton(onClick = {
                        rec.primaryText()?.let { container.delivery.copyToClipboard(SpeakerTurnsCodec.applyNames(it, rec.speakerNames)) }
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                    }
                }
            }

            if (!expanded && !selectMode) {
                rec.error?.takeIf { isError }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                }
            } else if (!selectMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    TabChip("Raw", tab == Tab.RAW) { tab = Tab.RAW }
                    TabChip("Clean", tab == Tab.CLEANED) { tab = Tab.CLEANED }
                    TabChip("Draft", tab == Tab.REFORMATTED) { tab = Tab.REFORMATTED }
                    if (rec.isMeeting || rec.meetingNotesText != null) {
                        TabChip("Meeting", tab == Tab.MEETING) { tab = Tab.MEETING }
                    }
                }

                val text = when (tab) {
                    Tab.RAW -> SpeakerTurnsCodec.render(rec.speakerTurns, rec.speakerNames).ifBlank { rec.transcript.orEmpty() }
                    Tab.CLEANED -> rec.cleanedText
                    Tab.REFORMATTED -> rec.draftedText
                    Tab.MEETING -> rec.meetingNotesText
                }?.let { SpeakerTurnsCodec.applyNames(it, rec.speakerNames) }

                if (text != null && text.isNotBlank()) {
                    ReadableText(text, meeting = tab == Tab.MEETING)
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    TextButton(onClick = { container.delivery.copyToClipboard(text) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.size(Space.xs))
                        Text("Copy")
                    }
                    TextButton(onClick = { showReader = true }) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = null)
                        Text(" Read")
                    }
                    if (rec.isMeeting) TextButton(onClick = { showSpeakers = true }) {
                        Icon(Icons.Filled.People, contentDescription = null)
                        Text(" Speakers")
                    }
                    }
                } else if (tab == Tab.RAW) {
                    Text(
                        "No transcript yet — tap Retry to run it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (rec.transcript.isNullOrBlank()) {
                    Text("Transcribe first, then a version can be generated.", style = MaterialTheme.typography.bodySmall)
                } else {
                    TextButton(
                        enabled = !generating,
                        onClick = {
                            generating = true
                            actionError = null
                            scope.launch {
                                runCatching {
                                    AltVersionGenerator(container).generate(
                                        rec.id,
                                        when (tab) {
                                            Tab.REFORMATTED -> AltKind.DRAFTED
                                            Tab.MEETING -> AltKind.MEETING_NOTES
                                            else -> AltKind.CLEANED
                                        },
                                    )
                                }.onFailure { actionError = it.message ?: "Could not generate ${tab.label}. Check the provider key and try again." }
                                generating = false
                            }
                        },
                    ) { Text(if (generating) "Generating…" else "Generate ${tab.label}") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    // Retry/Re-run re-transcribes from the local WAV — a remote
                    // row (synced from another device) has no local audio, so
                    // it's hidden there; only Delete remains.
                    if (!rec.remote && hasLocalAudio) {
                        val playing = player.playingPath == rec.audioPath
                        AssistChip(
                            onClick = { player.toggle(rec.audioPath) },
                            label = { Text(if (playing) "Stop" else "Play") },
                            leadingIcon = { Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, null) },
                        )
                    }
                    if (hasLocalAudio || !rec.transcript.isNullOrBlank()) {
                        AssistChip(
                            onClick = {
                                // Same pattern as desktop's retry(): if the row's
                                // not an error, confirm before nuking existing
                                // text. If it IS an error, fire immediately —
                                // that's the whole point of retry.
                                showRerun = true
                            },
                            label = { Text(if (isError) "Retry" else "Re-run") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            colors = if (isError) AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) else AssistChipDefaults.assistChipColors(),
                        )
                    }
                    // Delete only appears on rows this device owns — a remote row
                    // can't be deleted from here (SYNC_DESIGN.md ownership rule).
                    if (!rec.remote) {
                        AssistChip(
                            onClick = { confirmDelete = true },
                            label = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        )
                    }
                }
                rec.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                actionError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (showRerun) {
        RerunDialog(
            rec,
            settings,
            providerKeys = container.providerKeys,
            canTranscribe = !rec.remote && hasLocalAudio,
            onDismiss = { showRerun = false },
        ) { request ->
            showRerun = false
            actionError = null
            scope.launch {
                if (request.transcribe) {
                    container.recordings.setProcessingOverrides(
                        rec.id,
                        request.sttProvider,
                        request.sttModel,
                        request.llmProvider,
                        request.llmModel,
                        request.strongProvider,
                        request.strongModel,
                    )
                    container.recordings.setRequestedOutputs(rec.id, buildList {
                        if (request.cleaned) add("cleaned")
                        if (request.drafted) add("drafted")
                        if (request.meeting) add("meeting")
                    }.joinToString(","))
                    container.recordings.setMeetingMetadata(
                        rec.id,
                        if (request.diarize) rec.speakerTurns else null,
                        request.diarize || request.meeting,
                        request.diarize,
                    )
                    container.recordings.retry(ctx, rec.id)
                } else {
                    val generator = AltVersionGenerator(container)
                    val failures = mutableListOf<String>()
                    suspend fun generate(kind: AltKind, label: String, provider: String, model: String) {
                        runCatching { generator.generate(rec.id, kind, provider, model) }
                            .onFailure { failures += "$label: ${it.message ?: "provider request failed"}" }
                    }
                    if (request.cleaned) generate(AltKind.CLEANED, "Clean", request.llmProvider, request.llmModel)
                    if (request.drafted) generate(AltKind.DRAFTED, "Draft", request.strongProvider, request.strongModel)
                    if (request.meeting) generate(AltKind.MEETING_NOTES, "Meeting notes", request.strongProvider, request.strongModel)
                    actionError = failures.takeIf { it.isNotEmpty() }?.joinToString("\n")
                }
            }
        }
    }
    if (confirmDelete) {
        DeleteDialog(
            itemCount = 1,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch { container.recordings.deleteOwned(listOf(rec.id)) }
            },
        )
    }
    if (showReader) {
        val readerText = when (tab) {
            Tab.RAW -> SpeakerTurnsCodec.render(rec.speakerTurns, rec.speakerNames).ifBlank { rec.transcript.orEmpty() }
            Tab.CLEANED -> rec.cleanedText.orEmpty()
            Tab.REFORMATTED -> rec.draftedText.orEmpty()
            Tab.MEETING -> rec.meetingNotesText.orEmpty()
        }.let { SpeakerTurnsCodec.applyNames(it, rec.speakerNames) }
        FocusReader(rec, tab.label, readerText, onClose = { showReader = false }, onSpeakers = { showSpeakers = true })
    }
    if (showSpeakers) {
        SpeakerNamesDialog(rec, onDismiss = { showSpeakers = false }) { names ->
            scope.launch { container.recordings.setSpeakerNames(rec.id, SpeakerTurnsCodec.encodeNames(names)) }
            showSpeakers = false
        }
    }
}

/**
 * One meta line: time · platform · mode · duration, plus status only when it's
 * worth saying. "done" on every completed row is noise, and completed is the
 * overwhelmingly common case.
 */
@Composable
private fun MetaLine(rec: Recording, isError: Boolean) {
    val parts = buildList {
        add(clockFmt.format(Date(rec.createdAt)))
        add(rec.platformLabel())
        add(rec.mode.label)
        add("${rec.durationMs / 1000}s")
        if (rec.status != RecordingStatus.DONE) add(statusLabel(rec.status))
        if (rec.retryCount > 0) add("↻ ${rec.retryCount}")
    }
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ReadableText(text: String, meeting: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        readingSegments(text).forEach { paragraph ->
            Text(
                paragraph.trim(),
                style = if (meeting && paragraph.startsWith("#")) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.bodyLarge,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.15,
            )
        }
    }
}

/** Break unformatted cleanup blobs into eye-sized paragraphs without rewriting words. */
private fun readingSegments(text: String): List<String> {
    val explicit = text.trim().split(Regex("\\n\\s*\\n")).filter(String::isNotBlank)
    if (explicit.size > 1 || text.length < 420) return explicit
    val sentences = text.trim().split(Regex("(?<=[.!?])\\s+(?=[A-Z0-9])")).filter(String::isNotBlank)
    if (sentences.size < 3) return explicit
    val result = mutableListOf<String>()
    var paragraph = StringBuilder()
    sentences.forEach { sentence ->
        if (paragraph.isNotEmpty() && paragraph.length + sentence.length > 300) {
            result += paragraph.toString()
            paragraph = StringBuilder()
        }
        if (paragraph.isNotEmpty()) paragraph.append(' ')
        paragraph.append(sentence)
    }
    if (paragraph.isNotEmpty()) result += paragraph.toString()
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusReader(
    rec: Recording,
    label: String,
    text: String,
    onClose: () -> Unit,
    onSpeakers: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(label) },
                        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") } },
                        actions = {
                            if (rec.isMeeting) IconButton(onClick = onSpeakers) { Icon(Icons.Filled.People, "Name speakers") }
                        },
                    )
                },
            ) { inner ->
                Column(
                    Modifier.fillMaxSize().padding(inner).verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(horizontal = Space.xl, vertical = Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.lg),
                ) {
                    Text(rec.title ?: rec.sourceName ?: "Recording", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    ReadableText(text, meeting = label == "Meeting Notes")
                }
            }
        }
    }
}

@Composable
private fun SpeakerNamesDialog(rec: Recording, onDismiss: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    val speakers = SpeakerTurnsCodec.decode(rec.speakerTurns).map { it.speaker }.distinct()
    val existing = SpeakerTurnsCodec.names(rec.speakerNames)
    var values by remember(rec.id, rec.speakerNames) { mutableStateOf(speakers.associateWith { existing[it].orEmpty() }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name speakers") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (speakers.isEmpty()) Text("No speaker turns were returned for this recording.")
                speakers.forEach { speaker ->
                    OutlinedTextField(
                        value = values[speaker].orEmpty(),
                        onValueChange = { values = values + (speaker to it) },
                        label = { Text(speaker) },
                        placeholder = { Text("Real name") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(values.filterValues(String::isNotBlank)) }, enabled = speakers.isNotEmpty()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private data class RerunRequest(
    val transcribe: Boolean,
    val diarize: Boolean,
    val cleaned: Boolean,
    val drafted: Boolean,
    val meeting: Boolean,
    val sttProvider: String,
    val sttModel: String,
    val llmProvider: String,
    val llmModel: String,
    val strongProvider: String,
    val strongModel: String,
)

/** One dependency-ordered recipe instead of four unrelated retry actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RerunDialog(
    rec: Recording,
    settings: AppSettings,
    providerKeys: ProviderKeyReadiness,
    canTranscribe: Boolean,
    onDismiss: () -> Unit,
    onRun: (RerunRequest) -> Unit,
) {
    val existing = remember(rec.requestedOutputs) {
        rec.requestedOutputs.split(',').map(String::trim).filter(String::isNotEmpty).toSet()
    }
    val mustTranscribe = rec.transcript.isNullOrBlank()
    var transcribe by remember(rec.id, canTranscribe) { mutableStateOf(canTranscribe) }
    var diarize by remember(rec.id, canTranscribe) { mutableStateOf(rec.diarizationEnabled && canTranscribe) }
    var cleaned by remember(rec.id) { mutableStateOf(rec.cleanedText != null || "cleaned" in existing) }
    var drafted by remember(rec.id) { mutableStateOf(rec.draftedText != null || "drafted" in existing) }
    var meeting by remember(rec.id) { mutableStateOf(rec.meetingNotesText != null || "meeting" in existing) }
    var sttProvider by remember(rec.id) { mutableStateOf(rec.sttProviderOverride ?: settings.sttProvider) }
    var sttModel by remember(rec.id) { mutableStateOf(rec.sttModelOverride ?: settings.sttModel) }
    var llmProvider by remember(rec.id) { mutableStateOf(rec.llmProviderOverride ?: settings.llmProvider) }
    var llmModel by remember(rec.id) { mutableStateOf(rec.llmModelOverride ?: settings.activeLlmModel) }
    var strongProvider by remember(rec.id) { mutableStateOf(rec.strongProviderOverride ?: settings.strongProvider) }
    var strongModel by remember(rec.id) { mutableStateOf(rec.strongModelOverride ?: settings.strongModel) }
    LaunchedEffect(rec.id) {
        if (diarize) {
            val compatible = ProviderCatalog.compatibleDiarizationSelection(sttProvider, sttModel)
            sttProvider = compatible.first
            sttModel = compatible.second
        }
    }
    val missingKeys = buildList {
        if (transcribe) providerKeys.stt(sttProvider).takeUnless { it.ready }?.let {
            add("${it.providerLabel} key for transcription")
        }
        if (cleaned) providerKeys.llm(llmProvider).takeUnless { it.ready }?.let {
            add("${it.providerLabel} key for Clean")
        }
        if (drafted || meeting) providerKeys.llm(strongProvider).takeUnless { it.ready }?.let {
            add("${it.providerLabel} key for Draft/Meeting")
        }
    }.distinct()
    val canRun = (transcribe || cleaned || drafted || meeting) && missingKeys.isEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rec.status == RecordingStatus.ERROR) "Retry recording" else "Re-run recording") },
        text = {
            Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text("1 · Speech", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                RerunChoice(
                    "Transcription",
                    "${ProviderCatalog.label(sttProvider)} · ${ProviderCatalog.shortModel(sttModel)}",
                    transcribe,
                    enabled = canTranscribe && !mustTranscribe,
                ) { transcribe = it; if (!it) diarize = false }
                RerunChoice(
                    "Label speakers",
                    "Uses the diarization default and marks this as a meeting",
                    diarize,
                    enabled = transcribe,
                ) { enabled ->
                    diarize = enabled
                    if (enabled) {
                        val compatible = ProviderCatalog.compatibleDiarizationSelection(sttProvider, sttModel)
                        sttProvider = compatible.first
                        sttModel = compatible.second
                    }
                }
                if (transcribe) {
                    RerunProviderPicker(
                        if (diarize) ProviderCatalog.sttProviders.filter { it.id == ProviderCatalog.STT_DEEPGRAM }
                        else ProviderCatalog.sttProviders,
                        sttProvider,
                    ) { provider ->
                        sttProvider = provider
                        sttModel = ProviderCatalog.defaultSttModel(provider)
                    }
                    RerunModels(ProviderCatalog.sttModelsFor(sttProvider), sttModel) { sttModel = it }
                }

                Text("2 · AI outputs", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Selected outputs run after speech, in this order. Existing transcript can be reused when Transcription is off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RerunChoice(
                    "Clean",
                    "${ProviderCatalog.label(llmProvider)} · ${ProviderCatalog.shortModel(llmModel)}",
                    cleaned,
                ) { cleaned = it }
                RerunChoice(
                    "Draft",
                    "${ProviderCatalog.label(strongProvider)} · ${ProviderCatalog.shortModel(strongModel)}",
                    drafted,
                ) { drafted = it }
                RerunChoice(
                    "Meeting notes",
                    "${ProviderCatalog.label(strongProvider)} · ${ProviderCatalog.shortModel(strongModel)}",
                    meeting,
                ) { meeting = it }
                if (cleaned) {
                    Text("Clean model", style = MaterialTheme.typography.labelMedium)
                    RerunProviderPicker(ProviderCatalog.llmProviders, llmProvider) {
                        llmProvider = it
                        llmModel = ProviderCatalog.defaultLlmModel(it)
                    }
                    RerunModels(ProviderCatalog.llmModelsFor(llmProvider), llmModel) { llmModel = it }
                }
                if (drafted || meeting) {
                    Text("Draft & Meeting model", style = MaterialTheme.typography.labelMedium)
                    RerunProviderPicker(ProviderCatalog.llmProviders, strongProvider) {
                        strongProvider = it
                        strongModel = ProviderCatalog.defaultStrongLlmModel(it)
                    }
                    RerunModels(ProviderCatalog.llmModelsFor(strongProvider), strongModel) { strongModel = it }
                }
                if (missingKeys.isNotEmpty()) {
                    Text(
                        "Add ${missingKeys.joinToString()} in AI & model settings before running this recipe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canRun,
                onClick = {
                    onRun(RerunRequest(
                        transcribe, diarize && transcribe, cleaned, drafted, meeting,
                        sttProvider, sttModel, llmProvider, llmModel, strongProvider, strongModel,
                    ))
                },
            ) { Text(if (rec.status == RecordingStatus.ERROR) "Retry" else "Run") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RerunProviderPicker(
    options: List<ProviderCatalog.ProviderOption>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        options.forEach { option ->
            FilterChip(selected = option.id == selected, onClick = { onSelect(option.id) }, label = { Text(option.label) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RerunModels(
    options: List<ProviderCatalog.ModelOption>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        options.forEach { option ->
            FilterChip(selected = option.id == selected, onClick = { onSelect(option.id) }, label = { Text(option.label) })
        }
    }
}

@Composable
private fun RerunChoice(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChecked(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

private enum class Tab(val label: String) { RAW("Raw"), CLEANED("Clean"), REFORMATTED("Draft"), MEETING("Meeting Notes") }

private fun defaultTabFor(mode: DictationMode): Tab = when (mode) {
    DictationMode.RAW -> Tab.RAW
    DictationMode.CLEANED, DictationMode.ADVANCED -> Tab.CLEANED
    DictationMode.REFORMATTED -> Tab.REFORMATTED
}

private fun statusLabel(s: RecordingStatus): String = when (s) {
    RecordingStatus.RECORDING -> "recording"
    RecordingStatus.IMPORTING -> "importing"
    RecordingStatus.TRANSCRIBING -> "transcribing"
    RecordingStatus.DIARIZING -> "labeling speakers"
    RecordingStatus.CLEANING -> "polishing"
    RecordingStatus.DRAFTING -> "drafting"
    RecordingStatus.MEETING_NOTES -> "meeting notes"
    RecordingStatus.INJECTING -> "delivering"
    RecordingStatus.DONE -> "done"
    RecordingStatus.ERROR -> "error"
}

/**
 * Groups a row under Today / Yesterday / an absolute date.
 *
 * Deliberately calendar-day based, not "hours ago": two recordings either side
 * of midnight belong under different headers even if they're minutes apart,
 * which is how people actually remember when they said something.
 */
private fun dayBucket(millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    if (sameYear && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) return "Today"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    ) return "Yesterday"
    return (if (sameYear) dayFmt else dayYearFmt).format(Date(millis))
}

private val clockFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dayFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
private val dayYearFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
