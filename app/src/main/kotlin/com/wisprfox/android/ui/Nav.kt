package com.wisprfox.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height

/**
 * Where the bottom bar currently is. The bar shows Speak, Upload, History and
 * Settings (Nov 2026). Conversations is the second half of History (a Notes /
 * Conversations switch at its top), so it highlights History. Usage lives
 * under Settings → Usage.
 */
enum class NavTab { SPEAK, UPLOAD, CONVERSATIONS, HISTORY, SETTINGS }

/**
 * Bottom NavigationBar shared between [HomeScreen] and [HistoryScreen]. We
 * deliberately keep it to two destinations (the activation surface + the
 * library); Settings stays as the top-bar gear since it's a less-frequent
 * destination and the bottom bar earns its real estate by being one-tap to
 * the two things people actually use moment-to-moment.
 */
@Composable
fun WisprBottomBar(current: NavTab, onSelect: (NavTab) -> Unit) {
    NavigationBar(
        modifier = Modifier.height(58.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        val highlighted = if (current == NavTab.CONVERSATIONS) NavTab.HISTORY else current
        listOf(
            Triple(NavTab.SPEAK, Icons.Filled.Mic, "Speak"),
            Triple(NavTab.UPLOAD, Icons.Filled.FileUpload, "Upload"),
            Triple(NavTab.HISTORY, Icons.Filled.History, "History"),
            Triple(NavTab.SETTINGS, Icons.Filled.Settings, "Settings"),
        ).forEach { (tab, icon, label) -> NavigationBarItem(
            selected = highlighted == tab,
            onClick = { if (current != tab) onSelect(tab) },
            icon = { Icon(icon, contentDescription = null) },
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) }
    }
}

/**
 * The title of the History tab: a Notes / Conversations switch. Conversations
 * used to be its own bottom-bar tab; it now shares History's slot.
 */
@Composable
fun HistoryKindSwitch(current: NavTab, onSelect: (NavTab) -> Unit) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm),
    ) {
        listOf(NavTab.HISTORY to "Notes", NavTab.CONVERSATIONS to "Conversations").forEach { (tab, label) ->
            androidx.compose.material3.FilterChip(
                selected = current == tab,
                onClick = { if (current != tab) onSelect(tab) },
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}
