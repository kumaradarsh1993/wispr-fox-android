package com.wisprfox.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wisprfox.android.history.DeleteScope

/**
 * The delete rework dialog (`SYNC_DESIGN.md` "Delete rework per user spec"):
 * two independent checkboxes for *what* to remove, a scope choice for
 * *where* (only meaningful — and only enabled — for transcripts, and
 * "Everywhere" only when signed in), and a plain-words consequence line.
 * One implementation used everywhere a delete can be triggered: the per-row
 * AssistChip, long-press, multi-select, and delete-all — so the semantics
 * (and the phrasing of what's about to happen) never drift between paths.
 */
@Composable
fun DeleteDialog(
    itemCount: Int,
    signedIn: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (voiceFiles: Boolean, transcripts: Boolean, scope: DeleteScope) -> Unit,
) {
    var voiceFiles by remember { mutableStateOf(true) }
    var transcripts by remember { mutableStateOf(true) }
    var scope by remember { mutableStateOf(DeleteScope.THIS_DEVICE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (itemCount == 1) "Delete this recording?" else "Delete $itemCount recordings?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                CheckRow("Voice files (audio)", voiceFiles) { voiceFiles = it }
                CheckRow("Transcripts", transcripts) { transcripts = it }

                Spacer(Modifier.height(6.dp))
                Text("Where", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                RadioRow(
                    label = "This device only",
                    selected = scope == DeleteScope.THIS_DEVICE,
                    enabled = transcripts,
                    onClick = { scope = DeleteScope.THIS_DEVICE },
                )
                RadioRow(
                    label = if (signedIn) "Everywhere" else "Everywhere (sign in to enable)",
                    selected = scope == DeleteScope.EVERYWHERE,
                    enabled = transcripts && signedIn,
                    onClick = { scope = DeleteScope.EVERYWHERE },
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    consequence(voiceFiles, transcripts, scope, itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = voiceFiles || transcripts,
                onClick = { onConfirm(voiceFiles, transcripts, scope) },
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun consequence(voiceFiles: Boolean, transcripts: Boolean, scope: DeleteScope, count: Int): String {
    val subject = if (count == 1) "this recording" else "these $count recordings"
    return when {
        !voiceFiles && !transcripts -> "Pick at least one to delete."
        transcripts && scope == DeleteScope.EVERYWHERE ->
            "Removes $subject everywhere — this phone and every other signed-in device. There's no undo."
        transcripts && voiceFiles ->
            "Removes $subject from this phone (audio and transcript). Any signed-in copy elsewhere is untouched."
        transcripts ->
            "Removes the transcript for $subject from this phone. Any signed-in copy elsewhere is untouched."
        else -> "Removes the audio file for $subject. The transcript stays, marked as audio-removed."
    }
}
