package com.wisprfox.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.AltVersionGenerator
import com.wisprfox.android.history.AltKind
import com.wisprfox.android.history.Recording
import com.wisprfox.android.history.RecordingStatus
import com.wisprfox.android.provider.DictationMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
fun HistoryScreen(onBack: () -> Unit, onOpenHome: () -> Unit) {
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
        bottomBar = { WisprBottomBar(NavTab.HISTORY, onSelect = { if (it == NavTab.HOME) onOpenHome() }) },
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
    val rowBg = if (isError) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.surface

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
                            if (selectMode) { if (!rec.remote) onToggleSelect() } else expanded = !expanded
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

            if (!expanded && !selectMode) {
                rec.error?.takeIf { isError }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                }
            } else if (!selectMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
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
                    TextButton(onClick = { container.delivery.copyToClipboard(text) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.size(Space.xs))
                        Text("Copy")
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

                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
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
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch { container.recordings.deleteOwned(listOf(rec.id)) }
            },
        )
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
