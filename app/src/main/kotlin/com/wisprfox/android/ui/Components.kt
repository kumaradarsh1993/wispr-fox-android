package com.wisprfox.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wisprfox.android.R
import com.wisprfox.android.core.PipelineState
import com.wisprfox.android.overlay.AvatarView
import com.wisprfox.android.settings.Avatar

/**
 * The shared UI vocabulary (audit "Cross-cutting": *extract shared components*).
 *
 * `SectionCard`, `PermissionRow` and the Settings groups used to be three
 * unrelated implementations of the same visual idea, and the avatar picker
 * existed twice at two different sizes. Each idea now has exactly one
 * implementation, sized off [Space]/[Radius]/[Sizes].
 */

/* ── Screen scaffolding ──────────────────────────────────────────────────── */

/**
 * The standard scrollable screen body: one horizontal inset, one gap rhythm.
 * This is the thing the audit's P0-1 says was missing — the grid to align to.
 */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    gap: androidx.compose.ui.unit.Dp = Space.md,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen),
        verticalArrangement = Arrangement.spacedBy(gap),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

/**
 * A Settings spoke page.
 *
 * `LargeTopAppBar` is the One UI large-title convention, and it buys
 * reachability for free on a 6.8" display: the title starts near the middle of
 * the screen and collapses as you scroll, while the back affordance stays put.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpokeScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        ScreenColumn(Modifier.padding(inner), gap = Space.md) {
            content()
            // The scroll must clear the gesture bar; Scaffold's inset padding is
            // consumed by the top bar, not the bottom, on a bottom-bar-less page.
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

/* ── Cards + rows ────────────────────────────────────────────────────────── */

/** A titled card. One interior padding ([Space.card]), one radius ([Radius.lg]). */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            content()
        }
    }
}

/** Group heading above a run of [SettingsRow]s. */
@Composable
fun SettingsGroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Space.xs, top = Space.sm),
    )
}

/**
 * One hub row: icon + title + current-value summary + chevron.
 *
 * The summary is what makes the hub *answer questions* rather than just being a
 * menu — "Groq · Whisper Large v3 Turbo" means you don't have to open the page
 * to know what it's set to.
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touch),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.padding(Space.card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Sizes.rowIcon),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Label + switch. Whole row is the target so it clears 48dp. */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    summary: String? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * A permission's live state with an inline fix action.
 *
 * Replaces both the naked `OutlinedButton`s that used to sit in Settings (which
 * told you nothing about whether the thing was already on) and Onboarding's
 * private copy of this idea.
 */
@Composable
fun PermissionCard(
    title: String,
    subtitle: String,
    granted: Boolean,
    cta: String,
    onClick: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            // The green tick is the only thing conveying granted/not-granted, and
            // a decorative circle says nothing to TalkBack.
            .semantics { contentDescription = if (granted) "$title, granted" else "$title, not granted" },
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.padding(Space.card),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            val tick = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(
                Modifier
                    .size(Sizes.rowIcon)
                    .background(tick, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (granted) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!granted) {
                    TextButton(onClick = onClick, modifier = Modifier.padding(top = Space.xs)) { Text(cta) }
                }
            }
        }
    }
}

/* ── Press-and-hold destructive action ───────────────────────────────────── */

/**
 * A deliberately-slow destructive control: the user must press and *hold* until
 * the fill completes before [onHoldComplete] fires. Used to arm irreversible,
 * cross-device actions (Purge) where a stray tap must never be enough — the hold
 * is the first gate, a confirm dialog the second.
 *
 * The hold is genuinely cancellable: lifting the finger early cancels the
 * progress animation and nothing fires. Completion only happens if the finger
 * stays down for the full [holdMillis].
 */
@Composable
fun HoldToConfirmButton(
    text: String,
    onHoldComplete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdMillis: Int = 1500,
) {
    val progress = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }

    LaunchedEffect(pressed) {
        if (pressed) {
            progress.snapTo(0f)
            // animateTo suspends for the full duration; an early release flips
            // `pressed` false, which restarts this effect and cancels the
            // animation — so the line below is reached ONLY on a completed hold.
            progress.animateTo(1f, animationSpec = tween(holdMillis, easing = LinearEasing))
            onHoldComplete()
            pressed = false
        } else {
            progress.snapTo(0f)
        }
    }

    val bg = if (enabled) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (enabled) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier
            .fillMaxWidth()
            .height(Sizes.touch)
            .clip(RoundedCornerShape(Radius.lg))
            .background(bg)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // The fill grows left→right as the hold progresses.
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .align(Alignment.CenterStart)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        )
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/* ── Avatar picker: ONE component, ONE size ──────────────────────────────── */

/** Display name per skin — also the TalkBack label (audit P1-9). */
fun avatarLabel(avatar: Avatar): String = when (avatar) {
    Avatar.FOX -> "Fox"
    Avatar.CLIPPY -> "Clippy"
    Avatar.ORU_GUJIA -> "Oru & Gujia"
    Avatar.SIRI -> "Orb"
}

/**
 * The avatar picker. Previously existed twice — `SettingsScreen` at 56dp cells /
 * 40dp previews and `HomeScreen` at 54dp / 38dp — same control, two sizes, two
 * files, no shared component. Now one component at [Sizes.avatarCell].
 *
 * Accessibility (audit P1-9): each cell used to be a bare `Box().clickable{}`
 * with no role and no label, so TalkBack announced four identical
 * "wispr-fox ready" buttons (the inner `AvatarView`'s state description) with no
 * way to tell them apart or know which was selected. Each cell is now a labelled
 * `RadioButton`-role selectable in a `selectableGroup`, and the inner art's own
 * semantics are cleared so they can't leak through.
 */
@Composable
fun AvatarPicker(
    selected: Avatar,
    onSelect: (Avatar) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Avatar.entries.forEach { avatar ->
            AvatarCell(
                avatar = avatar,
                selected = selected == avatar,
                onSelect = { onSelect(avatar) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AvatarCell(
    avatar: Avatar,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onSelect,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Sizes.avatarCell)
                .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(Radius.lg))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.lg)),
            contentAlignment = Alignment.Center,
        ) {
            // The art is decorative here — the cell carries the label.
            Box(Modifier.clearAndSetSemantics { }) {
                AvatarPreview(avatar, Modifier.size(Sizes.avatarPreview))
            }
        }
        Text(
            avatarLabel(avatar),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Static preview art for a skin. The cat pack uses its thumbnail (a cheap raster
 * preview); the rest render their idle state. Previously the thumbnail was drawn
 * with a `null` contentDescription and no wrapper, leaving it entirely unlabelled.
 */
@Composable
fun AvatarPreview(avatar: Avatar, modifier: Modifier = Modifier) {
    if (avatar == Avatar.ORU_GUJIA) {
        Image(painterResource(R.drawable.oru_gujia_thumbnail), contentDescription = null, modifier = modifier)
    } else {
        AvatarView(avatar, PipelineState.IDLE, modifier)
    }
}
