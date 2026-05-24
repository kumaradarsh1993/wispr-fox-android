package com.wisprfox.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wisprfox.android.R
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.AppState
import com.wisprfox.android.core.PipelineState
import com.wisprfox.android.delivery.WisprFoxAccessibilityService
import com.wisprfox.android.overlay.avatarFor
import com.wisprfox.android.provider.DictationMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenHistory: () -> Unit, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val state by AppState.state.collectAsState()
    var selectedMode by remember { mutableStateOf(DictationMode.CLEANED) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("wispr-fox") },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "History")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(avatarFor(state.pipeline)),
                contentDescription = null,
                modifier = Modifier.size(140.dp),
            )
            Text(statusLine(state), style = MaterialTheme.typography.titleMedium)
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.height(4.dp))
            Text("Mode", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("Raw", DictationMode.RAW, selectedMode) { selectedMode = it }
                ModeChip("Cleaned", DictationMode.CLEANED, selectedMode) { selectedMode = it }
                ModeChip("Reformat", DictationMode.REFORMATTED, selectedMode) { selectedMode = it }
            }

            val recording = state.pipeline == PipelineState.RECORDING
            Button(onClick = { container.controller.toggle(selectedMode) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (recording) "Stop & transcribe" else "Start recording")
            }

            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Setup", style = MaterialTheme.typography.labelLarge)
                    SetupButtons(ctx)
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, mode: DictationMode, selected: DictationMode, onSelect: (DictationMode) -> Unit) {
    FilterChip(selected = selected == mode, onClick = { onSelect(mode) }, label = { Text(label) })
}

@Composable
private fun SetupButtons(ctx: Context) {
    OutlinedButton(
        onClick = {
            if (!Settings.canDrawOverlays(ctx)) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Allow floating avatar (overlay)") }

    OutlinedButton(
        onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (WisprFoxAccessibilityService.isConnected()) "Auto-paste: ON" else "Enable auto-paste (Accessibility)")
    }

    OutlinedButton(
        onClick = {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm?.isIgnoringBatteryOptimizations(ctx.packageName) != true) {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Keep alive (battery exemption)") }
}

private fun statusLine(s: AppState.Snapshot): String = when (s.pipeline) {
    PipelineState.IDLE -> "Idle — tap to dictate"
    PipelineState.RECORDING -> "Recording…  ${formatDuration(s.elapsedMs)}"
    PipelineState.TRANSCRIBING -> "Transcribing…"
    PipelineState.CLEANING -> "Polishing…"
    PipelineState.INJECTING -> "Delivering…"
    PipelineState.DONE -> "Done"
    PipelineState.ERROR -> "Error"
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
