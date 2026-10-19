package com.wisprfox.android.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.PipelineProgress
import com.wisprfox.android.history.ConversationExport
import com.wisprfox.android.history.ConversationPass
import com.wisprfox.android.history.ConversationPasses
import com.wisprfox.android.history.PassState
import com.wisprfox.android.history.Recording
import com.wisprfox.android.history.RecordingStatus
import com.wisprfox.android.history.SpeakerTurnsCodec
import com.wisprfox.android.history.SpeakerTurnsDocument
import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.queue.TranscribeWorker
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ── List ────────────────────────────────────────────────────────────────── */

/** The Conversations tab: one card per `is_meeting` row. Dictations stay in History. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(onOpen: (String) -> Unit, onSelectTab: (NavTab) -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val rows by container.recordings.observeConversations().collectAsState(initial = emptyList())
    val progress by PipelineProgress.lines.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Conversations") },
            )
        },
        bottomBar = { WisprBottomBar(NavTab.CONVERSATIONS, onSelectTab) },
    ) { inner ->
        if (rows.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(inner).padding(horizontal = Space.screen),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No conversations yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(Space.sm))
                Text(
                    "Share a meeting recording to wispr-fox, or pick one on Upload and choose Conversation. You'll get who said what, and meeting notes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(Space.lg))
                Button(onClick = { onSelectTab(NavTab.UPLOAD) }) { Text("Upload a conversation") }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.sm, bottom = Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                items(rows, key = { it.id }) { rec ->
                    ConversationCard(rec, progress[rec.id]?.text) { onOpen(rec.id) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConversationCard(rec: Recording, liveLine: String?, onClick: () -> Unit) {
    val doc = remember(rec.speakerTurns) { SpeakerTurnsCodec.decodeDocument(rec.speakerTurns) }
    val names = remember(rec.speakerNames) { SpeakerTurnsCodec.names(rec.speakerNames) }
    val speakers = remember(doc, names) {
        SpeakerTurnsCodec.speakers(doc?.turns.orEmpty()).map { SpeakerTurnsCodec.displayName(it, names) }
    }
    val inFlight = rec.isInFlight()
    val preview = remember(rec.meetingNotesText, rec.transcript) { previewLines(rec.meetingNotesText ?: rec.transcript) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Icon(Icons.Filled.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Text(
                    conversationTitle(rec),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${cardDateFmt.format(Date(rec.createdAt))} · ${lengthLabel(rec.durationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (speakers.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    speakers.forEach { SpeakerChip(it) }
                }
            }
            if (preview.isNotBlank()) {
                Text(preview, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val status = when {
                inFlight -> liveLine ?: statusText(rec.status)
                rec.status == RecordingStatus.ERROR -> rec.error ?: "Failed"
                else -> ConversationPasses.problems(doc, inFlight = false).joinToString(" · ").ifBlank { null }
            }
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (inFlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun SpeakerChip(name: String) {
    Surface(shape = RoundedCornerShape(Radius.sm), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs),
        )
    }
}

/* ── Detail ──────────────────────────────────────────────────────────────── */

private enum class ConvTab(val label: String) { NOTES("Notes"), SPEAKERS("Speakers"), WHISPER("Whisper") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(recordingId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()
    val rec by container.recordings.observe(recordingId).collectAsState(initial = null)
    val progress by PipelineProgress.lines.collectAsState()

    val r = rec
    if (r == null) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text("Conversation") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        }) { inner -> Box(Modifier.fillMaxSize().padding(inner)) }
        return
    }

    val doc = remember(r.speakerTurns) { SpeakerTurnsCodec.decodeDocument(r.speakerTurns) }
    val names = remember(r.speakerNames) { SpeakerTurnsCodec.names(r.speakerNames) }
    val turns = doc?.turns.orEmpty()
    val whisper = doc?.accuracy?.text?.takeIf { it.isNotBlank() }
    val inFlight = r.isInFlight()
    val tabs = buildList {
        add(ConvTab.NOTES)
        add(ConvTab.SPEAKERS)
        if (whisper != null) add(ConvTab.WHISPER)
    }
    var tabName by rememberSaveable(r.id) { mutableStateOf(if (r.meetingNotesText.isNullOrBlank()) ConvTab.SPEAKERS.name else ConvTab.NOTES.name) }
    val tab = tabs.firstOrNull { it.name == tabName } ?: ConvTab.NOTES
    var copyMenu by remember { mutableStateOf(false) }
    var showNaming by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var queued by remember(r.id) { mutableStateOf(emptySet<ConversationPass>()) }
    LaunchedEffect(inFlight) { if (inFlight) queued = emptySet() }

    val exportInput = ConversationExport.Input(
        title = conversationTitle(r),
        createdAtMillis = r.createdAt,
        durationMs = r.durationMs,
        meetingNotes = r.meetingNotesText,
        doc = doc,
        names = names,
    )
    fun copy(label: String, text: String) {
        container.delivery.copyToClipboard(text)
        Toast.makeText(ctx, "$label copied", Toast.LENGTH_SHORT).show()
    }
    fun saveNames(updated: Map<String, String>) {
        scope.launch {
            container.recordings.setSpeakerNames(r.id, SpeakerTurnsCodec.encodeNames(updated.filterValues { it.isNotBlank() }))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(conversationTitle(r), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (turns.isNotEmpty()) {
                        IconButton(onClick = { showNaming = true }) { Icon(Icons.Filled.People, "Name speakers") }
                    }
                    Box {
                        IconButton(onClick = { copyMenu = true }) { Icon(Icons.Filled.ContentCopy, "Copy") }
                        DropdownMenu(expanded = copyMenu, onDismissRequest = { copyMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Notes") },
                                enabled = !r.meetingNotesText.isNullOrBlank(),
                                onClick = { copyMenu = false; copy("Notes", ConversationExport.notesText(exportInput)) },
                            )
                            DropdownMenuItem(
                                text = { Text("Speakers") },
                                enabled = turns.isNotEmpty(),
                                onClick = { copyMenu = false; copy("Speakers", ConversationExport.speakersPlain(turns, names)) },
                            )
                            if (whisper != null) DropdownMenuItem(
                                text = { Text("Whisper") },
                                onClick = { copyMenu = false; copy("Whisper transcript", whisper) },
                            )
                            DropdownMenuItem(
                                text = { Text("Everything") },
                                onClick = { copyMenu = false; copy("Everything", ConversationExport.markdown(exportInput)) },
                            )
                        }
                    }
                    IconButton(onClick = { shareMarkdown(ctx, exportInput) }) { Icon(Icons.Filled.Share, "Export") }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Column(
                Modifier.padding(horizontal = Space.screen, vertical = Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text(
                    buildString {
                        append(cardDateFmt.format(Date(r.createdAt)))
                        append(" · ").append(lengthLabel(r.durationMs))
                        doc?.engine?.let { append(" · ").append(ProviderCatalog.label(it.provider)).append(' ').append(it.model) }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (inFlight) {
                    Text(
                        progress[r.id]?.text ?: statusText(r.status),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (r.status == RecordingStatus.ERROR && r.error != null) {
                    Text(r.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                PassActions(r, doc, inFlight, queued) { pass ->
                    queued = queued + pass
                    TranscribeWorker.enqueuePasses(ctx, r.id, setOf(pass))
                }
            }

            PrimaryTabRow(selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0)) {
                tabs.forEach { t ->
                    Tab(selected = t == tab, onClick = { tabName = t.name }, text = { Text(t.label) })
                }
            }

            when (tab) {
                ConvTab.NOTES -> TextPane(
                    r.meetingNotesText?.takeIf { it.isNotBlank() }?.let { ConversationExport.notesText(exportInput) },
                    empty = if (inFlight) "Notes are on their way." else "No meeting notes yet.",
                    markdown = true,
                )
                ConvTab.SPEAKERS -> if (turns.isEmpty()) {
                    TextPane(null, empty = if (inFlight) "Labelling speakers…" else "No speaker transcript yet.", markdown = false)
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.md, bottom = Space.xxl),
                        verticalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        items(turns.size) { index ->
                            val turn = turns[index]
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                                Text(
                                    turn.start?.let { ConversationExport.mmss(it) } ?: "",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(48.dp).padding(top = 2.dp),
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                                    Text(
                                        SpeakerTurnsCodec.displayName(turn.speaker, names),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { renaming = turn.speaker },
                                    )
                                    Text(turn.text.trim(), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
                ConvTab.WHISPER -> TextPane(whisper, empty = "No Whisper transcript.", markdown = false)
            }
        }
    }

    renaming?.let { speaker ->
        RenameSpeakerDialog(
            speaker = speaker,
            current = names[speaker].orEmpty(),
            onDismiss = { renaming = null },
        ) { name ->
            renaming = null
            saveNames(names + (speaker to name.trim()))
        }
    }
    if (showNaming) {
        SpeakerNamingSheet(doc, names, onDismiss = { showNaming = false }) { updated ->
            showNaming = false
            saveNames(updated)
        }
    }
}

/** Per-pass retry buttons: each enqueues work for just that pass. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PassActions(
    rec: Recording,
    doc: SpeakerTurnsDocument?,
    inFlight: Boolean,
    queued: Set<ConversationPass>,
    onRun: (ConversationPass) -> Unit,
) {
    if (inFlight) return
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val hasAudio = remember(rec.audioPath) { !rec.remote && rec.audioPath.isNotBlank() && File(rec.audioPath).isFile }
    if (rec.remote) {
        Text(
            "Made on another device. Retries run on the device that has the audio.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val retryable = ConversationPasses.retryable(doc, rec.meetingNotesText, inFlight = false)
    if (retryable.isEmpty()) return

    doc?.errors?.forEach { (key, message) ->
        val pass = ConversationPass.parse(key) ?: return@forEach
        if (ConversationPasses.state(doc, pass) == PassState.FAILED) {
            Text("${pass.label}: $message", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    val hasText = !doc?.turns.isNullOrEmpty() || !doc?.accuracy?.text.isNullOrBlank()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        retryable.forEach { pass ->
            val (label, enabled) = when (pass) {
                ConversationPass.SPEAKERS -> "Retry speakers" to hasAudio
                ConversationPass.ACCURACY -> "Add Whisper transcript" to
                    (hasAudio && container.providerKeys.stt(ProviderCatalog.ACCURACY_PROVIDER).ready)
                ConversationPass.NOTES -> "Write notes" to hasText
            }
            val isQueued = pass in queued
            AssistChip(
                onClick = { onRun(pass) },
                enabled = enabled && !isQueued,
                label = { Text(if (isQueued) "Queued…" else label) },
            )
        }
    }
    if (!hasAudio) {
        Text(
            "The audio file is no longer on this phone, so only notes can be rewritten.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TextPane(text: String?, empty: String, markdown: Boolean) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen, vertical = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        if (text.isNullOrBlank()) {
            Text(empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (markdown) {
            text.lines().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.isEmpty() -> Spacer(Modifier.size(Space.xs))
                    trimmed.startsWith("#") -> Text(
                        trimmed.trimStart('#').trim(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                        Text("•  " + trimmed.drop(2), style = MaterialTheme.typography.bodyLarge)
                    else -> Text(trimmed, style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            text.trim().split(Regex("\\n\\s*\\n")).forEach {
                Text(it.trim(), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.size(Space.xxl))
    }
}

@Composable
private fun RenameSpeakerDialog(speaker: String, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(speaker) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename $speaker") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Name") },
                placeholder = { Text(speaker) },
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { onSave(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Each speaker with the first thing they said, so the user can tell who is who. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeakerNamingSheet(
    doc: SpeakerTurnsDocument?,
    names: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
) {
    val turns = doc?.turns.orEmpty()
    val speakers = SpeakerTurnsCodec.speakers(turns)
    var values by remember(names) { mutableStateOf(speakers.associateWith { names[it].orEmpty() }) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen).padding(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text("Name speakers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            speakers.forEach { speaker ->
                val first = turns.firstOrNull { it.speaker == speaker }
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(speaker, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    first?.let {
                        Text(
                            "\u201C${it.text.trim().take(140)}\u201D" + (it.start?.let { s -> " · ${ConversationExport.mmss(s)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedTextField(
                        value = values[speaker].orEmpty(),
                        onValueChange = { values = values + (speaker to it) },
                        placeholder = { Text("Real name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { onSave(values) }, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }
}

/* ── Helpers ─────────────────────────────────────────────────────────────── */

/** Writes the contract Markdown to cache and offers it through the share sheet. */
private fun shareMarkdown(ctx: Context, input: ConversationExport.Input) {
    runCatching {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, ConversationExport.fileName(input))
        file.writeText(ConversationExport.markdown(input))
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, input.title)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Export conversation"))
    }.onFailure {
        Toast.makeText(ctx, "Couldn't export: ${it.message}", Toast.LENGTH_LONG).show()
    }
}

private fun Recording.isInFlight(): Boolean = status != RecordingStatus.DONE && status != RecordingStatus.ERROR

private fun conversationTitle(rec: Recording): String =
    rec.title?.takeIf { it.isNotBlank() }
        ?: rec.sourceName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
        ?: "Conversation"

private fun lengthLabel(durationMs: Long): String {
    val total = durationMs / 1000
    return if (total >= 3600) ConversationExport.hms(durationMs)
    else String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
}

/** First two meaningful lines of notes (markdown markers stripped) or of the transcript. */
private fun previewLines(text: String?): String =
    text.orEmpty().lines()
        .map { it.trim().trimStart('#', '-', '*', ' ').trim() }
        .filter { it.isNotEmpty() && !it.equals("Summary", ignoreCase = true) }
        .take(2)
        .joinToString("\n")

private fun statusText(s: RecordingStatus): String = when (s) {
    RecordingStatus.IMPORTING -> "Preparing audio…"
    RecordingStatus.TRANSCRIBING -> "Transcribing…"
    RecordingStatus.DIARIZING -> "Labelling speakers…"
    RecordingStatus.MEETING_NOTES -> "Writing notes…"
    RecordingStatus.DONE -> "Done"
    RecordingStatus.ERROR -> "Failed"
    else -> "Working…"
}

private val cardDateFmt = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault())
