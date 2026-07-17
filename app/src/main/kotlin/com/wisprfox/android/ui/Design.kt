package com.wisprfox.android.ui

import androidx.compose.ui.unit.dp

/**
 * The design-token layer (audit P0-1).
 *
 * Before this existed, four screens invented four outer paddings (20/16/12/14dp)
 * and cards used nine different interiors. Nothing lined up because there was
 * nothing to line up to — which is most of what the "hot mess" verdict was
 * actually reacting to.
 *
 * The rule: **no bare `dp` literal in a `padding()`/`spacedBy()` call anywhere in
 * `ui/`.** If a value isn't here, it doesn't belong on screen. Everything is a
 * multiple of 4dp so text baselines and card edges land on a shared grid.
 */
object Space {
    /** 4dp — hairline gaps inside a single control (label → value). */
    val xs = 4.dp

    /** 8dp — related items inside one card; the default chip gap. */
    val sm = 8.dp

    /** 12dp — icon → text in a row. */
    val md = 12.dp

    /** 16dp — the workhorse. Card interiors, screen insets, list gaps. */
    val lg = 16.dp

    /** 24dp — between unrelated groups on the same screen. */
    val xl = 24.dp

    /** 32dp — hero breathing room; the gap above a bottom-anchored action. */
    val xxl = 32.dp

    /**
     * THE horizontal screen inset. Every screen. M3's default and what One UI
     * uses, so app content lines up with the system's own surfaces.
     */
    val screen = 16.dp

    /** THE card interior. One value, everywhere. */
    val card = 16.dp

    /** Gap between sibling cards / list rows. */
    val listGap = 8.dp
}

/**
 * Corner radii on the M3 shape scale. The codebase previously shipped 14/12/7/6/
 * 1.5dp with no rule. One UI leans generous, so cards and sheets sit at the
 * 16/28 end rather than M3's baseline 12.
 */
object Radius {
    /** 8dp — badges, inline pills. */
    val sm = 8.dp

    /** 12dp — small interactive tiles. */
    val md = 12.dp

    /** 16dp — cards, banners, settings rows. The default. */
    val lg = 16.dp

    /** 28dp — sheets and full-width hero surfaces. */
    val xl = 28.dp
}

/**
 * Fixed sizes that recur across screens. Named so a change is one edit rather
 * than a grep — the avatar picker in particular used to exist twice at two
 * different sizes (56/40 in Settings vs 54/38 on Home).
 */
object Sizes {
    /** Minimum touch target. Android accessibility floor — never go below. */
    val touch = 48.dp

    /** Avatar picker cell, and the preview inside it. One size, one component. */
    val avatarCell = 72.dp
    val avatarPreview = 48.dp

    /** Home hero avatar. Was 160dp in a 180dp box — ~22% of the viewport. */
    val hero = 120.dp

    /** Leading icon in a settings/permission row. */
    val rowIcon = 24.dp
}
