package com.wisprfox.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Insights
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

/** Where the bottom bar currently is — keeps Home/History one tap apart. */
enum class NavTab { SPEAK, UPLOAD, HISTORY, USAGE }

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
        modifier = Modifier.height(64.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        listOf(
            Triple(NavTab.SPEAK, Icons.Filled.Mic, "Speak"),
            Triple(NavTab.UPLOAD, Icons.Filled.FileUpload, "Upload"),
            Triple(NavTab.HISTORY, Icons.Filled.History, "History"),
            Triple(NavTab.USAGE, Icons.Filled.Insights, "Usage"),
        ).forEach { (tab, icon, label) -> NavigationBarItem(
            selected = current == tab,
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
