package com.wisprfox.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.PipelineState
import com.wisprfox.android.overlay.AvatarView
import com.wisprfox.android.settings.AppSettings
import com.wisprfox.android.settings.AvatarScale
import com.wisprfox.android.ui.AvatarPicker
import com.wisprfox.android.ui.Radius
import com.wisprfox.android.ui.SectionCard
import com.wisprfox.android.ui.Sizes
import com.wisprfox.android.ui.Space
import com.wisprfox.android.ui.SpokeScaffold
import com.wisprfox.android.ui.ThemeChoice
import com.wisprfox.android.ui.ThemePrefs
import com.wisprfox.android.ui.ToggleRow
import com.wisprfox.android.ui.rememberThemeChoice
import kotlinx.coroutines.launch

/**
 * 3 · Foxy — everything about how the app and the mascot *look*.
 *
 * This is where the old "Delivery & avatar" category's appearance half lands.
 * That heading merged auto-paste, the overlay toggle, the avatar skin, avatar
 * size, haptics and two system deep-links — four unrelated concerns under one
 * name, and the name existed only because someone needed somewhere to put them.
 * Delivery behaviour now lives in its own spoke; this page is appearance only.
 */
@Composable
fun FoxySpoke(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())
    val themePrefs = remember(ctx) { ThemePrefs.get(ctx) }
    val theme = rememberThemeChoice()

    SpokeScaffold("Foxy", onBack) {
        SectionCard("Theme") {
            Text(
                "The same three looks as wispr-fox on desktop and the web.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ThemePicker(selected = theme, onSelect = { themePrefs.set(it) })
        }

        SectionCard("Skin") {
            AvatarPicker(
                selected = settings.avatar,
                onSelect = { scope.launch { container.settingsStore.setAvatar(it) } },
            )
        }

        SectionCard("Size") {
            Text(
                "How big Foxy floats over your apps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScaleSegmented(
                selected = settings.avatarScale,
                onSelect = { scope.launch { container.settingsStore.setAvatarScale(it) } },
            )
            // A size preset with no preview makes the user guess, then go
            // check. The preview tracks the same multiplier the overlay uses.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(Sizes.hero),
                contentAlignment = Alignment.Center,
            ) {
                AvatarView(
                    settings.avatar,
                    PipelineState.IDLE,
                    Modifier.size(Sizes.avatarCell * settings.avatarScale.multiplier),
                )
            }
        }

        SectionCard("Behaviour") {
            ToggleRow(
                label = "Show floating Foxy",
                summary = "The tap-to-talk button that hovers over your apps.",
                checked = settings.overlayBubbleEnabled,
                onChange = { scope.launch { container.settingsStore.setOverlayBubble(it) } },
            )
            ToggleRow(
                label = "Haptics on long-press",
                checked = settings.hapticsEnabled,
                onChange = { scope.launch { container.settingsStore.setHaptics(it) } },
            )
        }
    }
}

/* ── Theme picker ────────────────────────────────────────────────────────── */

/**
 * Each theme shows its actual colours as swatches rather than only a name —
 * "Retro" means nothing until you see it, and a picker that requires you to
 * apply an option to find out what it is isn't a picker.
 */
@Composable
private fun ThemePicker(selected: ThemeChoice, onSelect: (ThemeChoice) -> Unit) {
    Column(
        Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        ThemeChoice.entries.forEach { choice ->
            ThemeOptionRow(
                choice = choice,
                selected = selected == choice,
                onSelect = { onSelect(choice) },
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(choice: ThemeChoice, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touch)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                RoundedCornerShape(Radius.md),
            )
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        ThemeSwatch(choice)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
                choice.label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                choice.summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * A two-tone chip: the theme's surface behind its accent.
 *
 * These are hard-coded rather than read from the live scheme on purpose — a
 * swatch has to show the theme it *offers*, not the theme currently applied.
 * They're the same values as the palettes in `Theme.kt`; if those move, these
 * move. Auto shows light-over-dark to signal that it's the pair.
 */
@Composable
private fun ThemeSwatch(choice: ThemeChoice) {
    val (surface, accent) = when (choice) {
        ThemeChoice.SYSTEM -> Color(0xFF1F1812) to Color(0xFFEC7C34)
        ThemeChoice.FOXY -> Color(0xFFFAF6EC) to Color(0xFFEC7C34)
        ThemeChoice.DARK -> Color(0xFF1F1812) to Color(0xFFF08D4A)
        ThemeChoice.RETRO -> Color(0xFFF3E9D2) to Color(0xFFC47A30)
        // Material You has no fixed colour by definition — show the live one.
        ThemeChoice.DYNAMIC -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.primary
    }
    Box(
        Modifier
            .size(Sizes.rowIcon)
            .background(surface, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .background(accent, CircleShape),
        )
    }
}

/* ── Avatar size ─────────────────────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScaleSegmented(selected: AvatarScale, onSelect: (AvatarScale) -> Unit) {
    val scales = AvatarScale.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        scales.forEachIndexed { index, scale ->
            SegmentedButton(
                selected = selected == scale,
                onClick = { onSelect(scale) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = scales.size),
            ) { Text(scale.label) }
        }
    }
}
