package com.wisprfox.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.AltVersionGenerator
import com.wisprfox.android.history.AltKind
import com.wisprfox.android.history.DeleteScope
import com.wisprfox.android.history.Recording
import com.wisprfox.android.history.RecordingStatus
import com.wisprfox.android.provider.DictationMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * History/Library, with the v1.1 additions:
 *
 *  - **Retry chip is always visible**, matching the desktop sibling
 *    (`HistoryRow.svelte` retry comment): stranded rows can be stuck in
 *    transcribing/cleaning, and retry is the recovery path. Confirm
 *    dialog on non-error rows so a stray tap doesn't burn an STT call.
 *    Retry count appears next to the meta line (↻ N).
 *  - **Select mode** in the top bar — multi-select rows and bulk delete.
 *    Overflow menu → "Delete all". Every destructive path goes through
 *    a confirm dialog and removes the WAV from disk via the repository.
 *  - **Bottom NavigationBar** (Home/History) — replaces the buried
 *    history card on home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenHome: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()
    val recordings by container.recordings.observeRecent().collectAsState(initial = emptyList())
    val player = rememberAudioPlayer()

    val authState by container.authManager.state.collectAsState()
    val signedIn = authState.signedIn

    var selectMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }

    fun exitSelectMode() {
        selectMode = false
        selected.clear()
    }

    // Shared delete applier for the bulk paths (delete-all + multi-select) —
    // routes the DeleteDialog's choices to the repository the same way the
    // per-row path does, so semantics never diverge between the three surfaces.
    fun applyDelete(ids: List<String>, voiceFiles: Boolean, transcripts: Boolean, scope2: DeleteScope) {
        scope.launch {
            when {
                transcripts -> container.recordings.deleteTranscripts(ids, scope2)
                voiceFiles -> container.recordings.deleteAudioOnly(ids)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Text(
                        if (selectMode) "${selected.count { it.value }} selected" else "History",
                    )
                },
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
                        val anyChecked = selected.any { it.value }
                        IconButton(onClick = { confirmDeleteSelected = anyChecked }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected", tint = if (anyChecked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        IconButton(
                            onClick = { selectMode = true },
                            enabled = recordings.isNotEmpty(),
                        ) { Icon(Icons.Filled.CheckBoxOutlineBlank, contentDescription = "Select") }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Delete all", color = MaterialTheme.colorScheme.error) },
                                    enabled = recordings.isNotEmpty(),
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; confirmDeleteAll = true },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = { WisprBottomBar(NavTab.HISTORY, onSelect = { if (it == NavTab.HOME) onOpenHome() }) },
    ) { inner ->
        if (recordings.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(inner).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Nothing yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Recordings you make will appear here — replay the audio, retry, and view Raw / Clean / Draft versions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(inner),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(recordings, key = { it.id }) { rec ->
                    HistoryRow(
                        rec = rec,
                        player = player,
                        selectMode = selectMode,
                        signedIn = signedIn,
                        checked = selected[rec.id] == true,
                        onToggleSelect = { selected[rec.id] = !(selected[rec.id] ?: false) },
                    )
                }
            }
        }
    }

    if (confirmDeleteAll) {
        val ids = recordings.map { it.id }
        DeleteDialog(
            itemCount = ids.size,
            signedIn = signedIn,
            onDismiss = { confirmDeleteAll = false },
            onConfirm = { voiceFiles, transcripts, delScope ->
                confirmDeleteAll = false
                applyDelete(ids, voiceFiles, transcripts, delScope)
                exitSelectMode()
            },
        )
    }

    if (confirmDeleteSelected) {
        val ids = selected.filter { it.value }.keys.toList()
        DeleteDialog(
            itemCount = ids.size,
            signedIn = signedIn,
            onDismiss = { confirmDeleteSelected = false },
            onConfirm = { voiceFiles, transcripts, delScope ->
                confirmDeleteSelected = false
                applyDelete(ids, voiceFiles, transcripts, delScope)
                exitSelectMode()
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    rec: Recording,
    player: AudioPlayerState,
    selectMode: Boolean,
    signedIn: Boolean,
    checked: Boolean,
    onToggleSelect: () -> Unit,
) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()

    var expanded by remember(rec.id) { mutableStateOf(false) }
    var tab by remember(rec.id) { mutableStateOf(defaultTabFor(rec.mode)) }
    var generating by remember(rec.id) { mutableStateOf(false) }
    var confirmRetry by remember(rec.id) { mutableStateOf(false) }
    var confirmDelete by remember(rec.id) { mutableStateOf(false) }

    val isError = rec.status == RecordingStatus.ERROR
    val rowBg = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surface

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = rowBg)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().combinedClickable(
                    onClick = { if (selectMode) onToggleSelect() else expanded = !expanded },
                    // Long-press opens the delete dialog straight for this row
                    // (SYNC_DESIGN.md press-and-hold delete), unless we're in
                    // multi-select where a long-press has no separate meaning.
                    onLongClick = { if (!selectMode) confirmDelete = true },
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (selectMode) {
                    Icon(
                        if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(timeFmt.format(Date(rec.createdAt)), fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlatformBadge(rec.platformLabel())
                        Text(
                            "${rec.mode.label} · ${rec.durationMs / 1000}s · ${statusLabel(rec.status)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (rec.retryCount > 0) {
                            Box(
                                Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    "↻ ${rec.retryCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                // Rows synced from another device have no local audio — hide play.
                if (!selectMode && !rec.remote) {
                    val playing = player.playingPath == rec.audioPath
                    IconButton(onClick = { player.toggle(rec.audioPath) }) {
                        Icon(
                            if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "Stop" else "Play",
                        )
                    }
                }
            }

            // Collapsed preview text.
            if (!expanded && !selectMode) {
                rec.primaryText()?.takeIf { it.isNotBlank() }?.let {
                    Text(it.take(140), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
                rec.error?.takeIf { isError }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                }
            } else if (!selectMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TabChip("Raw", tab == Tab.RAW) { tab = Tab.RAW }
                    TabChip("Clean", tab == Tab.CLEANED) { tab = Tab.CLEANED }
                    TabChip("Draft", tab == Tab.REFORMATTED) { tab = Tab.REFORMATTED }
                }

                val text = when (tab) {
                    Tab.RAW -> rec.transcript
                    Tab.CLEANED -> rec.cleanedText
                    Tab.REFORMATTED -> rec.draftedText
                }

                if (text != null && text.isNotBlank()) {
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { container.delivery.copyToClipboard(text) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Text(" Copy")
                        }
                    }
                } else if (tab == Tab.RAW) {
                    Text("No transcript yet — tap Retry to run it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (rec.transcript.isNullOrBlank()) {
                    Text("Transcribe first, then a version can be generated.", style = MaterialTheme.typography.bodySmall)
                } else {
                    TextButton(
                        enabled = !generating,
                        onClick = {
                            generating = true
                            scope.launch {
                                runCatching {
                                    AltVersionGenerator(container).generate(
                                        rec.id,
                                        if (tab == Tab.REFORMATTED) AltKind.DRAFTED else AltKind.CLEANED,
                                    )
                                }
                                generating = false
                            }
                        },
                    ) { Text(if (generating) "Generating…" else "Generate ${tab.label}") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Retry/Re-run re-transcribes from the local WAV — a remote
                    // row (synced from another device) has no local audio, so
                    // it's hidden there; only Delete remains.
                    if (!rec.remote) {
                        AssistChip(
                            onClick = {
                                // Same pattern as desktop's retry(): if the row's
                                // not an error, confirm before nuking existing
                                // text. If it IS an error, fire immediately —
                                // that's the whole point of retry.
                                if (isError) {
                                    scope.launch { container.recordings.retry(ctx, rec.id) }
                                } else {
                                    confirmRetry = true
                                }
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
                    AssistChip(
                        onClick = { confirmDelete = true },
                        label = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    )
                }
                rec.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (confirmRetry) {
        ConfirmDialog(
            title = "Re-run this recording?",
            body = "Re-running will replace the current transcript (and any cleaned/drafted versions). The audio stays.",
            confirm = "Re-run",
            onDismiss = { confirmRetry = false },
            onConfirm = {
                confirmRetry = false
                scope.launch { container.recordings.retry(ctx, rec.id) }
            },
        )
    }
    if (confirmDelete) {
        DeleteDialog(
            itemCount = 1,
            signedIn = signedIn,
            onDismiss = { confirmDelete = false },
            onConfirm = { voiceFiles, transcripts, delScope ->
                confirmDelete = false
                val ids = listOf(rec.id)
                scope.launch {
                    when {
                        transcripts -> container.recordings.deleteTranscripts(ids, delScope)
                        voiceFiles -> container.recordings.deleteAudioOnly(ids)
                    }
                }
            },
        )
    }
}

/** Small chip showing where a recording came from (Desktop / Web / Mobile). */
@Composable
private fun PlatformBadge(label: String) {
    Box(
        Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
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
        confirmButton = {
            Button(onClick = onConfirm) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

private enum class Tab(val label: String) { RAW("Raw"), CLEANED("Clean"), REFORMATTED("Draft") }

private fun defaultTabFor(mode: DictationMode): Tab = when (mode) {
    DictationMode.RAW -> Tab.RAW
    DictationMode.CLEANED, DictationMode.ADVANCED -> Tab.CLEANED
    DictationMode.REFORMATTED -> Tab.REFORMATTED
}

private fun statusLabel(s: RecordingStatus): String = when (s) {
    RecordingStatus.RECORDING -> "recording"
    RecordingStatus.IMPORTING -> "importing"
    RecordingStatus.TRANSCRIBING -> "transcribing"
    RecordingStatus.CLEANING -> "polishing"
    RecordingStatus.INJECTING -> "delivering"
    RecordingStatus.DONE -> "done"
    RecordingStatus.ERROR -> "error"
}

private val timeFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
