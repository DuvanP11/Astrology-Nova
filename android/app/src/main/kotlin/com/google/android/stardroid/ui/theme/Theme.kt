/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.google.android.stardroid.render.api.Rgba

/**
 * The map chrome is always dark (it floats over the night sky); night mode swaps to a
 * red-shifted scheme so the UI matches `RenderState.nightMode` and preserves dark adaptation.
 * One DataStore preference drives both worlds (layers-and-app.md).
 */
@Composable
fun SkyMapTheme(
    nightMode: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (nightMode) NightColors else DayColors,
        content = content,
    )
}

/**
 * Multiply tint for photographs in night mode (v1's red `PorterDuff.MULTIPLY` filter on
 * gallery thumbnails and expanded images) — theme colors only restyle the chrome; bitmaps
 * must be red-shifted explicitly to preserve dark adaptation.
 */
val NightPhotoTint = Color(0xFFB03030)

/**
 * The two-tier status palette (AGENTS.md): day colors carry meaning by hue; the night set is
 * red-shifted with brightness mirroring the day-mode meaning (brighter = better). Values are
 * v1's `status_*` / `night_status_*` resources.
 */
data class StatusColors(
    val good: Color,
    val ok: Color,
    val warning: Color,
    val bad: Color,
    val absent: Color,
)

fun statusColors(nightMode: Boolean): StatusColors =
    if (nightMode) NightStatusColors else DayStatusColors

/**
 * `ok` and `warning` diverge from v1 (D73), which separated them from a gold accent that this
 * fork no longer uses. They are kept as they are rather than reverted to v1's yellow and
 * orange: the constraint that shaped them — status must read as status and not as the brand —
 * is satisfied by both sets under Nova Cyan, and this set is the one the rest of the app's
 * screenshots, help text and translations were written against.
 *
 * The live constraint here is instead the accent itself: cyan sits at hue 186°, far from every
 * status hue below, so nothing in this set collides with it.
 */
private val DayStatusColors =
    StatusColors(
        good = Color(0xFF8BC34A),
        ok = Color(0xFFDCE775),
        warning = Color(0xFFFF7043),
        bad = Color(0xFFE91E63),
        absent = Color(0xFFA0A0A0),
    )

private val NightStatusColors =
    StatusColors(
        good = Color(0xFFDD6666),
        ok = Color(0xFFAA4444),
        warning = Color(0xFF883333),
        bad = Color(0xFF662222),
        absent = Color(0xFF552222),
    )

/**
 * The long-form document palette for [StyledHtml]'s renderers (help, EULA, What's New,
 * calibration): v1's `help.css` expressed as roles rather than CSS classes. Kept here with
 * [StatusColors] so the raw hues live in one place — no `Color(0xFF…)` literals in `ui/`.
 */
data class DocumentColors(
    /** `h1`–`h3` accents, indexed by heading level - 1. */
    val headings: List<Color>,
    val calloutBackground: Color,
    val calloutAccent: Color,
    /**
     * Red-shift applied to the help screen's symbol-key glyphs, or null in day mode — there the
     * legend must match what the map actually draws, so it tints from the renderer's own
     * `SkyColors` rather than from a chrome color that merely happens to agree.
     */
    val symbolTintOverride: Color?,
)

fun documentColors(nightMode: Boolean): DocumentColors =
    if (nightMode) NightDocumentColors else DayDocumentColors

/** Bridges a renderer [Rgba] to a Compose [Color] so chrome can reuse the map's own palette. */
fun Rgba.toComposeColor(): Color = Color(red = r, green = g, blue = b, alpha = a)

/**
 * The onboarding tour's spotlight (D46/D61). [neon] is *not* derived from the map's markers:
 * it is picked for contrast against the chrome and to stay distinct from the accent the
 * spotlight points at, so it tracks the chrome palette rather than the renderer's. Under the
 * Nova palette that means violet — cyan is now the accent, and a cyan spotlight ringing a
 * cyan control says nothing.
 */
data class TourColors(
    val neon: Color,
    /** The label chip's fill, v1's `neon_text_bg`. */
    val labelFill: Color,
)

fun tourColors(nightMode: Boolean): TourColors = if (nightMode) NightTourColors else DayTourColors

private val DayTourColors =
    TourColors(
        neon = Color(0xFF9B7BFF),
        labelFill = Color(0xDD0B1020),
    )

private val NightTourColors =
    TourColors(
        neon = Color(0xFFFF5252),
        labelFill = Color(0xDD0B1020),
    )

/**
 * Headings walk the brand's three accents in order of weight: cyan for h1, violet for h2,
 * pink for h3. Depth reads by hue rather than by size alone, which is what keeps a long help
 * page navigable on a phone.
 *
 * The callout deliberately does *not* reuse [StatusColors.warning]. Those hues are load-bearing
 * for live state — "this sensor is degraded right now" — and a static prose note is not that.
 * Violet is the secondary accent and reads as "worth reading", leaving the warm end of the
 * palette to mean what it means everywhere else.
 */
private val DayDocumentColors =
    DocumentColors(
        headings = listOf(Color(0xFF5AF2FF), Color(0xFF9B7BFF), Color(0xFFFB7EC0)),
        calloutBackground = Color(0xFF16224A),
        calloutAccent = Color(0xFF9B7BFF),
        symbolTintOverride = null,
    )

/** The night palette's brightest red — `h1` accent and the night symbol-key tint. */
private val NightAccentBright = Color(0xFFE06060)

/**
 * Night variants: red-shifted per the status-color rules, heading brightness stepping down with
 * depth so the hierarchy survives the single-hue palette.
 *
 * The callout carries far less background lift than its day counterpart. On the near-black night
 * surface a tonal panel is both hard to see and hostile to dark adaptation, so the accent bar
 * does the work and the fill only just separates from the page.
 */
private val NightDocumentColors =
    DocumentColors(
        headings = listOf(NightAccentBright, Color(0xFFC04848), Color(0xFFA03A3A)),
        calloutBackground = Color(0xFF200000),
        calloutAccent = Color(0xFFC04848),
        // The map's markers are red-shifted by the renderer's night transform; the legend
        // follows with the brightest chrome red so it still reads as the same element.
        symbolTintOverride = NightAccentBright,
    )

/**
 * Astrology Nova's scheme: Nova Cyan on the near-black navy the assistant's own interface
 * uses, with Nova Violet as the secondary. The five hues (#0B1020 ground, #5AF2FF cyan,
 * #9B7BFF violet, #E6ECFF starlight, #9AA3C7 dim) are lifted straight from Nova rather than
 * re-derived, so the chrome, the launcher icon and the deep-sky view's web chrome are all
 * the same palette.
 *
 * **This trades away something the upstream palette had.** Sky Map picked amber deliberately:
 * of the bright hues it is the least hostile to dark adaptation, so its day scheme degraded
 * gently into the red night scheme. Cyan is the opposite — short wavelengths bleach night
 * vision fastest, and this scheme is the worst of the two to hold up to your face at a dark
 * site. [NightColors] is therefore not a nicety here but the mode that matters outdoors, and
 * it is left red and untouched.
 *
 * The `surfaceContainer*` ramp is set explicitly: dialogs, bottom sheets, cards and scrolled
 * top bars resolve their containers from those roles, and left unset they stay the baseline's
 * purple-tinted grey even once the core roles are branded.
 */
private val DayColors =
    darkColorScheme(
        primary = Color(0xFF5AF2FF),
        onPrimary = Color(0xFF002F38),
        primaryContainer = Color(0xFF00464F),
        onPrimaryContainer = Color(0xFFA7F4FF),
        inversePrimary = Color(0xFF006874),
        secondary = Color(0xFF9B7BFF),
        onSecondary = Color(0xFF1D0B57),
        secondaryContainer = Color(0xFF2E2168),
        onSecondaryContainer = Color(0xFFCFBEFF),
        tertiary = Color(0xFFFB7EC0),
        onTertiary = Color(0xFF4A0A31),
        // The time-travel player's "clock is sweeping" surface (TimeTravelUi); the baseline's
        // mauve container would break the palette, and reusing the violet secondary here
        // would make time travel look like an ordinary secondary action.
        tertiaryContainer = Color(0xFF4E1338),
        onTertiaryContainer = Color(0xFFFFB0DA),
        background = Color(0xFF070B18),
        onBackground = Color(0xFFE6ECFF),
        surface = Color(0xFF0B1020),
        onSurface = Color(0xFFE6ECFF),
        surfaceVariant = Color(0xFF1F2748),
        onSurfaceVariant = Color(0xFF9AA3C7),
        surfaceDim = Color(0xFF070B18),
        surfaceBright = Color(0xFF232C4C),
        surfaceContainerLowest = Color(0xFF04060F),
        surfaceContainerLow = Color(0xFF0B1020),
        surfaceContainer = Color(0xFF141C3C),
        surfaceContainerHigh = Color(0xFF1F2748),
        surfaceContainerHighest = Color(0xFF283258),
        // Borders and dividers rather than text, so this sits below the AA text threshold by
        // design; it doubles as the layer rail's "off" tint, always beside an icon shape.
        outline = Color(0xFF5A6A90),
        outlineVariant = Color(0xFF2A3355),
        error = Color(0xFFF87171),
        onError = Color(0xFF3F0709),
        errorContainer = Color(0xFF6E1F20),
        onErrorContainer = Color(0xFFFFDAD6),
        // Snackbars draw on the inverse roles; keep them Starlight-on-navy.
        inverseSurface = Color(0xFFE6ECFF),
        inverseOnSurface = Color(0xFF0B1020),
    )

private val NightColors =
    darkColorScheme(
        primary = Color(0xFFC03030),
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF400000),
        onPrimaryContainer = Color(0xFFE06060),
        secondary = Color(0xFF904040),
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF2A0000),
        onSecondaryContainer = Color(0xFFC05050),
        // The time-travel player's "clock is sweeping" surface; without an override the
        // baseline purple container would break the red palette.
        tertiary = Color(0xFFB05050),
        onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF4A0E0E),
        onTertiaryContainer = Color(0xFFE06868),
        // Snackbars draw on the inverse roles; the baseline's near-white inverseSurface
        // would wreck dark adaptation, so keep them red-on-dark too.
        inverseSurface = Color(0xFF380808),
        inverseOnSurface = Color(0xFFD05050),
        inversePrimary = Color(0xFFE06060),
        background = Color.Black,
        onBackground = Color(0xFFB03030),
        surface = Color(0xFF150000),
        onSurface = Color(0xFFB03030),
        surfaceVariant = Color(0xFF200000),
        onSurfaceVariant = Color(0xFFA03030),
        outline = Color(0xFF702020),
    )
