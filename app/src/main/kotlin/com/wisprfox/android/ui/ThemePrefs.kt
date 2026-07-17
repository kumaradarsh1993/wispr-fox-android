package com.wisprfox.android.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistence for the theme picker.
 *
 * This is deliberately *not* in `settings/SettingsStore.kt` with the rest of the
 * app's preferences, for two reasons:
 *
 *  1. The theme has to be readable **synchronously, before the first frame**.
 *     `SettingsStore` is DataStore-backed and its flow emits asynchronously, so
 *     routing the theme through it would paint one frame of the wrong palette on
 *     every cold start — the exact flash the picker exists to avoid.
 *  2. It's presentation state, not app behaviour. Nothing outside `ui/` reads it.
 *
 * SharedPreferences is the right tool here precisely because it's the one store
 * that's warm on first read.
 */
class ThemePrefs private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("wisprfox_theme", Context.MODE_PRIVATE)

    private val _choice = MutableStateFlow(read())
    val choice: StateFlow<ThemeChoice> = _choice.asStateFlow()

    private fun read(): ThemeChoice {
        val saved = prefs.getString(KEY, null) ?: return ThemeChoice.SYSTEM
        // valueOf-with-fallback, matching how SettingsStore parses its enums: a
        // value written by a build that had an entry this one doesn't must not
        // crash the app on launch.
        return runCatching { ThemeChoice.valueOf(saved) }.getOrDefault(ThemeChoice.SYSTEM)
    }

    fun set(value: ThemeChoice) {
        prefs.edit().putString(KEY, value.name).apply()
        _choice.value = value
    }

    companion object {
        private const val KEY = "choice"

        @Volatile
        private var instance: ThemePrefs? = null

        fun get(context: Context): ThemePrefs =
            instance ?: synchronized(this) {
                instance ?: ThemePrefs(context).also { instance = it }
            }
    }
}

/** The live theme choice, for the picker and for [WisprFoxTheme] at the root. */
@Composable
fun rememberThemeChoice(): ThemeChoice {
    val ctx = LocalContext.current
    val prefs = remember(ctx) { ThemePrefs.get(ctx) }
    val choice by prefs.choice.collectAsState()
    return choice
}
