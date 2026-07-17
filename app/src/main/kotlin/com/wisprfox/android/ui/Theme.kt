package com.wisprfox.android.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Themes, ported to match the desktop/web sibling's `src/app.css` token sets so
 * the three apps read as one family (audit P1-1 — Android shipped exactly one
 * hard-coded light scheme while desktop/web shipped three).
 *
 * Token values are lifted verbatim from `wispr-fox/src/app.css`; keep the two in
 * lockstep when either moves.
 */

/* ── Palettes (verbatim from desktop app.css) ────────────────────────────── */

/**
 * The token set each theme fills in. This mirrors the CSS custom-property names
 * on the desktop side one-for-one, so a change there maps here mechanically
 * rather than by interpretation.
 */
private data class FoxPalette(
    val surface: Color,
    val card: Color,
    val sidebar: Color,
    val elev: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentPressed: Color,
    val accentSoft: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val isDark: Boolean,
)

/** Foxy light — the brand default. `:root` in app.css. */
private val FoxyLightPalette = FoxPalette(
    surface = Color(0xFFFAF6EC),
    card = Color(0xFFFFFFFF),
    sidebar = Color(0xFFF4EAD6),
    elev = Color(0xFFFFFFFF),
    border = Color(0xFFCDBBA0),
    borderSubtle = Color(0xFFE6DAC4),
    textPrimary = Color(0xFF2B2218),
    textSecondary = Color(0xFF7D6A55),
    textMuted = Color(0xFFB3A08A),
    accent = Color(0xFFEC7C34),
    accentPressed = Color(0xFFB85412),
    accentSoft = Color(0xFFFDE2C4),
    success = Color(0xFF6CB16D),
    warning = Color(0xFFE89150),
    danger = Color(0xFFCF5A47),
    info = Color(0xFF5C92C8),
    isDark = false,
)

/** Dark — "fox curled up in front of a fireplace". `[data-theme="dark"]`. */
private val FoxyDarkPalette = FoxPalette(
    surface = Color(0xFF1F1812),
    card = Color(0xFF2C2218),
    sidebar = Color(0xFF1A130D),
    elev = Color(0xFF36281D),
    border = Color(0xFF4A3B2C),
    borderSubtle = Color(0xFF3A2E22),
    textPrimary = Color(0xFFF5EAD5),
    textSecondary = Color(0xFFB3A08A),
    textMuted = Color(0xFF7D6A55),
    accent = Color(0xFFF08D4A),
    accentPressed = Color(0xFFD56A26),
    accentSoft = Color(0xFF4A3120),
    success = Color(0xFF76B076),
    warning = Color(0xFFE89150),
    danger = Color(0xFFD65F47),
    info = Color(0xFF5C92C8),
    isDark = true,
)

/** Retro — vintage warmth without going Win95. `[data-theme="retro"]`. */
private val RetroPalette = FoxPalette(
    surface = Color(0xFFF3E9D2),
    card = Color(0xFFFBF6E5),
    sidebar = Color(0xFFE8DAB8),
    elev = Color(0xFFFBF6E5),
    border = Color(0xFFC4A87A),
    borderSubtle = Color(0xFFDCC9A2),
    textPrimary = Color(0xFF3A2812),
    textSecondary = Color(0xFF7A5A30),
    textMuted = Color(0xFF9A7A50),
    accent = Color(0xFFC47A30),
    accentPressed = Color(0xFF8F551C),
    accentSoft = Color(0xFFEBD9B8),
    success = Color(0xFF5E8038),
    warning = Color(0xFFC47A30),
    danger = Color(0xFFA83A2A),
    info = Color(0xFF5C92C8),
    isDark = false,
)

/**
 * Maps our token set onto M3 roles.
 *
 * The `background` = app surface / `surface` = card split is deliberate and
 * predates this file: Scaffold paints `background`, Card paints `surface`. On
 * the desktop those are `--bg-surface` and `--bg-card`, so the mapping is a
 * straight port rather than an M3-idiomatic reading of the role names.
 */
private fun FoxPalette.toColorScheme() = (if (isDark) darkColorScheme() else lightColorScheme()).copy(
    primary = accent,
    onPrimary = if (isDark) Color(0xFF1F1812) else Color.White,
    primaryContainer = accentSoft,
    onPrimaryContainer = if (isDark) textPrimary else accentPressed,
    secondary = textSecondary,
    onSecondary = if (isDark) Color(0xFF1F1812) else Color.White,
    secondaryContainer = accentSoft,
    onSecondaryContainer = if (isDark) textPrimary else accentPressed,
    tertiary = accent,
    tertiaryContainer = accentSoft,
    onTertiaryContainer = if (isDark) textPrimary else accentPressed,
    background = surface,
    onBackground = textPrimary,
    surface = card,
    onSurface = textPrimary,
    surfaceVariant = sidebar,
    onSurfaceVariant = textSecondary,
    surfaceContainerLowest = card,
    surfaceContainerLow = card,
    surfaceContainer = surface,
    surfaceContainerHigh = elev,
    surfaceContainerHighest = sidebar,
    outline = border,
    outlineVariant = borderSubtle,
    error = danger,
    onError = Color.White,
    errorContainer = danger.copy(alpha = 0.14f),
    onErrorContainer = danger,
    scrim = Color.Black,
)

/* ── Brand colours M3 has no role for ────────────────────────────────────── */

/**
 * success/warning/info have no M3 colour role, and the screens were hard-coding
 * them (`GREEN = Color(0xFF6CB16D)` in HomeScreen, the METER_* trio in
 * UsageMeter, `Color(0xFF2FB170)` in the overlay). Every one of those would have
 * stayed light-mode green on a dark surface. Route them through the theme so
 * they track it.
 */
data class FoxColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
)

val LocalFoxColors = staticCompositionLocalOf {
    FoxColors(
        success = FoxyLightPalette.success,
        warning = FoxyLightPalette.warning,
        danger = FoxyLightPalette.danger,
        info = FoxyLightPalette.info,
    )
}

/** Accessor mirroring `MaterialTheme.colorScheme`'s ergonomics. */
val MaterialTheme.foxColors: FoxColors
    @Composable get() = LocalFoxColors.current

/* ── Typography + shapes ─────────────────────────────────────────────────── */

/**
 * The theme previously passed only `colorScheme`, so the type and shape systems
 * were stock M3 — meaning the app had no voice beyond its colour. These are the
 * roles the screens actually use, tuned for a 6.8" One UI display: slightly
 * tighter tracking on display/headline roles, and `Trim.None` line-height so
 * multi-line body copy in cards doesn't lose its first-line top padding.
 */
private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private val FoxTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, lineHeightStyle = lineHeightStyle),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, lineHeightStyle = lineHeightStyle),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, lineHeightStyle = lineHeightStyle),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeightStyle = lineHeightStyle),
        bodyMedium = bodyMedium.copy(lineHeightStyle = lineHeightStyle),
        bodySmall = bodySmall.copy(lineHeightStyle = lineHeightStyle),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium),
    )
}

/**
 * Shapes read off [Radius] so the two can't drift. M3's baseline is 12dp for
 * `medium`; One UI's house style is rounder, hence 16dp.
 */
private val FoxShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.sm),
    small = RoundedCornerShape(Radius.md),
    medium = RoundedCornerShape(Radius.lg),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)

/* ── Theme selection ─────────────────────────────────────────────────────── */

/**
 * What the user picked in Settings → Foxy → Appearance.
 *
 * [SYSTEM] is the default: Foxy light by day, the fireside dark palette at
 * night. That matches desktop's `data-theme="auto"`, which deliberately keeps
 * the two token sets in lockstep rather than falling back to a generic scheme.
 *
 * [DYNAMIC] (Material You) is offered but is **not** the default. The reasoning,
 * since the brief asked for it: wallpaper-derived colour is a genuine One UI
 * expectation and `minSdk 31` means it costs no version gate — but wispr-fox's
 * identity *is* the cream-and-orange fox, and a blue-wallpaper build would make
 * the phone a stranger to the desktop app the owner explicitly wants it to feel
 * related to. So: available, opt-in, never chosen for the user. This is the same
 * shape as desktop's theme picker.
 */
enum class ThemeChoice(val label: String, val summary: String) {
    SYSTEM("Auto", "Foxy by day, fireside at night"),
    FOXY("Foxy", "Warm cream and fox orange"),
    DARK("Dark", "Fireside browns"),
    RETRO("Retro", "Vintage paper"),
    DYNAMIC("Material You", "Colours from your wallpaper"),
}

@Composable
fun WisprFoxTheme(
    choice: ThemeChoice = ThemeChoice.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val ctx = LocalContext.current

    val palette = when (choice) {
        ThemeChoice.FOXY -> FoxyLightPalette
        ThemeChoice.DARK -> FoxyDarkPalette
        ThemeChoice.RETRO -> RetroPalette
        ThemeChoice.SYSTEM, ThemeChoice.DYNAMIC -> if (systemDark) FoxyDarkPalette else FoxyLightPalette
    }

    // Dynamic borrows the wallpaper's scheme but keeps our semantic colours —
    // a wallpaper has no opinion about what "over quota" should look like.
    val colorScheme = when (choice) {
        ThemeChoice.DYNAMIC -> if (systemDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> palette.toColorScheme()
    }

    // Status/nav-bar icon polarity has to track the *rendered* surface, not the
    // system setting: "Foxy" locked on a phone in dark mode still needs dark
    // icons over cream. Paired with enableEdgeToEdge() in MainActivity, which
    // makes the bars transparent (audit P1-2).
    val view = LocalView.current
    if (!view.isInEditMode) {
        val lightBars = !(if (choice == ThemeChoice.DYNAMIC) systemDark else palette.isDark)
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    CompositionLocalProvider(
        LocalFoxColors provides FoxColors(
            success = palette.success,
            warning = palette.warning,
            danger = palette.danger,
            info = palette.info,
        ),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FoxTypography,
            shapes = FoxShapes,
            content = content,
        )
    }
}
