package com.wisprfox.android.settings

/**
 * Selectable avatar shown as the floating button and the home hero.
 *  - [FOX]        watercolor fox (PNG asset pack, state-mapped).
 *  - [CLIPPY]     our own black stylized paperclip (drawn in Compose).
 *  - [ORU_GUJIA]  the two-cat raster pack, same assets as the desktop sibling
 *                 (`oru_gujia_*` in drawable-nodpi), state-mapped (P-3).
 *  - [SIRI]       an Apple-like animated orb, drawn in Compose (P-3).
 *
 * SettingsStore parses this with a valueOf-with-fallback, so a saved value from
 * an older build that predates a new entry safely falls back to the default.
 */
enum class Avatar { FOX, CLIPPY, ORU_GUJIA, SIRI }
