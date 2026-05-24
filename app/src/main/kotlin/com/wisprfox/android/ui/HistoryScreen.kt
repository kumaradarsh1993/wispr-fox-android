package com.wisprfox.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.AltVersionGenerator
import com.wisprfox.android.history.AltKind
import com.wisprfox.android.history.Recording
import com.wisprfox.android.history.RecordingStatus
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.queue.TranscribeWorker
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val recordings by container.recordings.observeRecent().collectAsState(initial = emptyList())
    val player = rememberAudioPlayer()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        if (recordings.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(inner).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Nothing yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Recordings you make will appear here — replay the audio, and view the raw, cleaned, and reformatted text.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(inner),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(recordings, key = { it.id }) { rec ->
                    HistoryRow(rec, player)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(rec: Recording, player: AudioPlayerState) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()

    var expanded by remember(rec.id) { mutableStateOf(false) }
    var tab by remember(rec.id) { mutableStateOf(defaultTabFor(rec.mode)) }
    var generating by remember(rec.id) { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(timeFmt.format(Date(rec.createdAt)), fontWeight = FontWeight.SemiBold)
                    Text(
                        "${rec.mode.label()} · ${rec.durationMs / 1000}s · ${rec.status.name.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val playing = player.playingPath == rec.audioPath
                IconButton(onClick = { player.toggle(rec.audioPath) }) {
                    Icon(
                        if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Stop" else "Play",
                    )
                }
            }

            // Collapsed preview.
            if (!expanded) {
                rec.primaryText()?.takeIf { it.isNotBlank() }?.let {
                    Text(it.take(140), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TabChip("Raw", tab == Tab.RAW) { tab = Tab.RAW }
                    TabChip("Cleaned", tab == Tab.CLEANED) { tab = Tab.CLEANED }
                    TabChip("Reformat", tab == Tab.REFORMATTED) { tab = Tab.REFORMATTED }
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
                    Text("No transcript yet.", style = MaterialTheme.typography.bodySmall)
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
                    if (rec.status == RecordingStatus.ERROR) {
                        AssistChip(
                            onClick = {
                                scope.launch {
                                    container.recordings.setStatus(rec.id, RecordingStatus.TRANSCRIBING)
                                    TranscribeWorker.enqueue(ctx, rec.id)
                                }
                            },
                            label = { Text("Retry") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        )
                    }
                    AssistChip(
                        onClick = { scope.launch { container.recordings.delete(rec.id) } },
                        label = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    )
                }
                rec.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

private enum class Tab(val label: String) { RAW("Raw"), CLEANED("Cleaned"), REFORMATTED("Reformat") }

private fun defaultTabFor(mode: DictationMode): Tab = when (mode) {
    DictationMode.RAW -> Tab.RAW
    DictationMode.CLEANED, DictationMode.ADVANCED -> Tab.CLEANED
    DictationMode.REFORMATTED -> Tab.REFORMATTED
}

private fun DictationMode.label(): String = when (this) {
    DictationMode.RAW -> "Raw"
    DictationMode.CLEANED -> "Cleaned"
    DictationMode.ADVANCED -> "Advanced"
    DictationMode.REFORMATTED -> "Reformat"
}

private val timeFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
