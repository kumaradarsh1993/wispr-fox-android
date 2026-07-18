package com.wisprfox.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Ownership-scoped delete confirm (SYNC_DESIGN.md "Delete — ownership-scoped").
 *
 * Replaces the old What (voice files / transcripts) × Where (this device /
 * everywhere) matrix with a single rule: a client deletes only the rows it
 * originated, and that always means everywhere — locally *and* tombstoned on the
 * server so every other signed-in device drops it too. So there's nothing to
 * choose here; this dialog is purely the confirm gate behind the press-and-hold
 * (long-press) affordance. The copy states the everywhere-and-forever
 * consequence plainly. Only shown for rows this device owns — the caller hides
 * the affordance entirely on rows pulled from another device.
 */
@Composable
fun DeleteDialog(
    itemCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val subject = if (itemCount == 1) "this recording" else "these $itemCount recordings"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (itemCount == 1) "Delete this recording?" else "Delete $itemCount recordings?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    "Deletes $subject — the audio and transcript on this phone, and the synced copy on every other signed-in device. There's no undo.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
