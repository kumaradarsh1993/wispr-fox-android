package com.wisprfox.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Where the bottom bar currently is — keeps Home/History one tap apart. */
enum class NavTab { HOME, HISTORY }

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
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp(),
    ) {
        NavigationBarItem(
            selected = current == NavTab.HOME,
            onClick = { if (current != NavTab.HOME) onSelect(NavTab.HOME) },
            icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
            label = { Text("Speak") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        )
        NavigationBarItem(
            selected = current == NavTab.HISTORY,
            onClick = { if (current != NavTab.HISTORY) onSelect(NavTab.HISTORY) },
            icon = { Icon(Icons.Filled.History, contentDescription = null) },
            label = { Text("History") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        )
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
