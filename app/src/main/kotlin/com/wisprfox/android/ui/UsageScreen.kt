package com.wisprfox.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.settings.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(onSelectTab: (NavTab) -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())
    val usage = rememberUsageSnapshot(settings)
    Scaffold(
        topBar = { TopAppBar(title = { Text("Usage") }) },
        bottomBar = { WisprBottomBar(NavTab.USAGE, onSelectTab) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(Space.screen), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Text("Today", style = MaterialTheme.typography.titleLarge)
            SectionCard {
                if (usage == null) Text("Working it out…") else {
                    UsageMeterRow("Speech-to-text", usage.stt)
                    UsageMeterRow("AI outputs", usage.llm)
                    Text(usage.resetLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("These are local estimates for the provider and model currently selected. Nothing is sent to an analytics service.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
