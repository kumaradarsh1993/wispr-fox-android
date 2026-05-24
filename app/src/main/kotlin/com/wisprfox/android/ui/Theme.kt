package com.wisprfox.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * "Foxy" palette, ported from the desktop sibling's app.css: warm cream
 * surfaces, fox-orange accent, dark-brown text. Single light scheme for now
 * (the brand identity is the warm look — dark mode can come later).
 */
private val Cream = Color(0xFFFAF6EC)
private val CardWhite = Color(0xFFFFFFFF)
private val CreamDeep = Color(0xFFF4EAD6)
private val FoxOrange = Color(0xFFEC7C34)
private val FoxOrangePressed = Color(0xFFB85412)
private val AccentSoft = Color(0xFFFDE2C4)
private val Brown = Color(0xFF2B2218)
private val BrownSecondary = Color(0xFF7D6A55)
private val TanOutline = Color(0xFFCDBBA0)
private val Danger = Color(0xFFCF5A47)

private val FoxyLight = lightColorScheme(
    primary = FoxOrange,
    onPrimary = Color.White,
    primaryContainer = AccentSoft,
    onPrimaryContainer = FoxOrangePressed,
    secondary = BrownSecondary,
    onSecondary = Color.White,
    secondaryContainer = AccentSoft,
    onSecondaryContainer = FoxOrangePressed,
    tertiary = FoxOrange,
    background = Cream,
    onBackground = Brown,
    surface = CardWhite,
    onSurface = Brown,
    surfaceVariant = CreamDeep,
    onSurfaceVariant = BrownSecondary,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = CardWhite,
    surfaceContainer = Cream,
    surfaceContainerHigh = CreamDeep,
    surfaceContainerHighest = CreamDeep,
    outline = TanOutline,
    outlineVariant = Color(0xFFE6DAC4),
    error = Danger,
    onError = Color.White,
)

@Composable
fun WisprFoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FoxyLight, content = content)
}
