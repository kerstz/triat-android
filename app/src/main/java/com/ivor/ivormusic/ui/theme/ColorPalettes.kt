package com.ivor.ivormusic.ui.theme

import com.ivor.ivormusic.R
import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Curated color palettes for the app theme.
 *
 * Each palette retains its reference swatches. [buildPaletteColorScheme] uses
 * the primary seed and the selected [PaletteStyle] to generate coordinated
 * accents, text and tinted surfaces through Material's HCT color utilities.
 * AMOLED is applied afterwards by the shared theme resolver.
 *
 * The special id [DYNAMIC_PALETTE_ID] is not seed-based: it means "use the
 * system wallpaper dynamic color" (Android 12+), the app's former default;
 * the default is now [TRIAT_PALETTE_ID].
 */
data class AppPalette(
    val id: String,
    @StringRes val nameRes: Int,
    val category: String,
    val seedPrimary: Color,
    val seedSecondary: Color,
    val seedTertiary: Color
)

/** Sentinel palette id meaning wallpaper-based dynamic color (Android 12+). */
const val DYNAMIC_PALETTE_ID = "dynamic"

/**
 * All seed palettes, grouped by [AppPalette.category]. Diverse on purpose:
 * bright/vivid, soft/pastel, warm/earthy, deep/moody and jewel/mono families so
 * there is a genuinely distinct pick for every taste.
 */
val APP_PALETTES: List<AppPalette> = listOf(
    // Triat (default): hand-built from TriatTokens, see [triatDarkColorScheme].
    // The seeds only feed generated playlist covers; the scheme ignores them
    // and ignores PaletteStyle.
    AppPalette(TRIAT_PALETTE_ID, R.string.palette_name_triat, "Triat", TriatTokens.signal, TriatTokens.ink3, TriatTokens.ink),
    // Triat: the Nothing desktop it comes from. Red is the one accent
    // (the active window border on that desktop), the rest stays grey.
    AppPalette("nothing", R.string.palette_name_nothing, "Triat", Color(0xFFD71921), Color(0xFF8A8A8A), Color(0xFFF2F2F2)),

    // Vibrant
    AppPalette("electric", R.string.palette_name_electric, "Vibrant", Color(0xFF2F6BFF), Color(0xFF00C2FF), Color(0xFF7A5CFF)),
    AppPalette("magenta", R.string.palette_name_magenta, "Vibrant", Color(0xFFFF2D95), Color(0xFFFF5CA8), Color(0xFFB14BFF)),
    AppPalette("citrus", R.string.palette_name_citrus, "Vibrant", Color(0xFFFF7A00), Color(0xFFFFB400), Color(0xFFB6D400)),
    AppPalette("neon", R.string.palette_name_neon, "Vibrant", Color(0xFF00E676), Color(0xFF00E5C0), Color(0xFF3AD1FF)),

    // Pastel
    AppPalette("lavender", R.string.palette_name_lavender, "Pastel", Color(0xFFB388FF), Color(0xFFCBA6FF), Color(0xFFFF9EE6)),
    AppPalette("mint", R.string.palette_name_mint, "Pastel", Color(0xFF66E0C0), Color(0xFF7FE3D0), Color(0xFF66D9E8)),
    AppPalette("peach", R.string.palette_name_peach, "Pastel", Color(0xFFFFAB91), Color(0xFFFF8A80), Color(0xFFFFB2C0)),
    AppPalette("sky", R.string.palette_name_sky, "Pastel", Color(0xFF7EC8FF), Color(0xFF90A8FF), Color(0xFFA6E3FF)),

    // Aesthetic — muted, faded-film, low-saturation tones
    AppPalette("vintagefilm", R.string.palette_name_vintagefilm, "Aesthetic", Color(0xFFC6A15B), Color(0xFF9CAF88), Color(0xFFB08968)),
    AppPalette("dustyrose", R.string.palette_name_dustyrose, "Aesthetic", Color(0xFFC58B96), Color(0xFFA99ABA), Color(0xFFCBA6A0)),
    AppPalette("sagesand", R.string.palette_name_sagesand, "Aesthetic", Color(0xFF9CAF88), Color(0xFFD8C3A5), Color(0xFFCB9273)),
    AppPalette("fadeddenim", R.string.palette_name_fadeddenim, "Aesthetic", Color(0xFF7E9AAE), Color(0xFF94A7B2), Color(0xFF6E9B94)),
    AppPalette("oatlatte", R.string.palette_name_oatlatte, "Aesthetic", Color(0xFFC2A386), Color(0xFFD6C6B0), Color(0xFFA07E64)),
    AppPalette("sunbleached", R.string.palette_name_sunbleached, "Aesthetic", Color(0xFFD79A87), Color(0xFF9CC0B3), Color(0xFFD9C4A3)),

    // Earthy
    AppPalette("terracotta", R.string.palette_name_terracotta, "Earthy", Color(0xFFE2725B), Color(0xFFC67B5C), Color(0xFFD9A066)),
    AppPalette("forest", R.string.palette_name_forest, "Earthy", Color(0xFF4C9A5B), Color(0xFF7A8B3C), Color(0xFFA6B04A)),
    AppPalette("mocha", R.string.palette_name_mocha, "Earthy", Color(0xFFA9745B), Color(0xFFC89B6E), Color(0xFF9E8B7D)),
    AppPalette("autumn", R.string.palette_name_autumn, "Earthy", Color(0xFFC1440E), Color(0xFFE08E0B), Color(0xFFD4A017)),

    // Moody
    AppPalette("crimson", R.string.palette_name_crimson, "Moody", Color(0xFFE0114B), Color(0xFFFF3D57), Color(0xFF9C1B3E)),
    AppPalette("indigo", R.string.palette_name_indigo, "Moody", Color(0xFF3D3DE0), Color(0xFF5C6BC0), Color(0xFF7E57C2)),
    AppPalette("deepteal", R.string.palette_name_deepteal, "Moody", Color(0xFF009688), Color(0xFF00BCD4), Color(0xFF4DB6AC)),
    AppPalette("plum", R.string.palette_name_plum, "Moody", Color(0xFF8E24AA), Color(0xFFAB47BC), Color(0xFFE040FB)),

    // Jewel & Mono
    AppPalette("emerald", R.string.palette_name_emerald, "Jewel & Mono", Color(0xFF10B981), Color(0xFF34D399), Color(0xFF6EE7B7)),
    AppPalette("ocean", R.string.palette_name_ocean, "Jewel & Mono", Color(0xFF0077B6), Color(0xFF0096C7), Color(0xFF3A67C4)),
    AppPalette("rosegold", R.string.palette_name_rosegold, "Jewel & Mono", Color(0xFFE18C8C), Color(0xFFE0B0A0), Color(0xFFD4AF37)),
    AppPalette("graphite", R.string.palette_name_graphite, "Jewel & Mono", Color(0xFF64748B), Color(0xFF94A3B8), Color(0xFF475569)),
    // HCT generation gives neutral seeds the same tone targets. Keep the
    // Black/White identity through their dedicated container roles below.
    AppPalette("black", R.string.palette_name_black, "Jewel & Mono", Color(0xFF000000), Color(0xFF000000), Color(0xFF000000)),
    AppPalette("white", R.string.palette_name_white, "Jewel & Mono", Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF))
)

/** Ordered list of the distinct categories, for grouping in the picker UI. */
val PALETTE_CATEGORIES: List<String> = APP_PALETTES.map { it.category }.distinct()

fun findPalette(id: String?): AppPalette? = APP_PALETTES.firstOrNull { it.id == id }

/** Accent overrides preserving the distinct Black and White presets. */
private data class PaletteRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color
)

/**
 * Preserve Black/White identity in their large container fills. HCT monochrome
 * generation alone would give both seeds the same roles. Accent ink stays
 * mode-adaptive; each container's on-color follows its fill, not the app mode.
 */
private fun monochromeRoles(dark: Boolean, leanDark: Boolean): PaletteRoles {
    val ink = if (dark) Color(0xFFF2F2F2) else Color(0xFF141414)
    val onInk = if (dark) Color(0xFF141414) else Color(0xFFF2F2F2)
    val containers = if (leanDark) {
        // Never pure black in dark mode - the app's own dark surfaces sit
        // near black too, and a container identical to its background is an
        // invisible one.
        if (dark) listOf(Color(0xFF2B2B2B), Color(0xFF363636), Color(0xFF414141))
        else listOf(Color(0xFF1E1E1E), Color(0xFF292929), Color(0xFF343434))
    } else {
        if (dark) listOf(Color(0xFFE8E8E8), Color(0xFFD8D8D8), Color(0xFFC8C8C8))
        else listOf(Color(0xFFECECEC), Color(0xFFE2E2E2), Color(0xFFD6D6D6))
    }
    val (primaryContainer, secondaryContainer, tertiaryContainer) = containers
    return PaletteRoles(
        primary = ink,
        onPrimary = onInk,
        primaryContainer = primaryContainer,
        onPrimaryContainer = contrastInk(primaryContainer),
        secondary = ink,
        onSecondary = onInk,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = contrastInk(secondaryContainer),
        tertiary = ink,
        onTertiary = onInk,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = contrastInk(tertiaryContainer)
    )
}

/**
 * Full HCT scheme, including the tinted neutral surfaces used by every screen.
 * Exception: [TRIAT_PALETTE_ID] in dark mode is built by hand from the tokens
 * and ignores [style]; in light mode it reuses the "nothing" HCT scheme.
 */
fun buildPaletteColorScheme(
    palette: AppPalette,
    dark: Boolean,
    style: PaletteStyle = PaletteStyle.TONAL_SPOT,
): ColorScheme {
    if (palette.id == TRIAT_PALETTE_ID) {
        // Tokens define no complete light set yet (roadmap V2): light mode is
        // the HCT scheme of "nothing" (same red identity), style ignored.
        return if (dark) triatDarkColorScheme()
        else buildPaletteColorScheme(findPalette("nothing")!!, dark = false, style = PaletteStyle.TONAL_SPOT)
    }
    val monochrome = palette.id == "black" || palette.id == "white"
    val scheme = buildSeedColorScheme(
        palette.seedPrimary,
        dark,
        if (monochrome) PaletteStyle.MONOCHROME else style,
    )
    if (!monochrome) return scheme

    // Black and White retain their distinct container fills in every style.
    val r = monochromeRoles(dark, leanDark = palette.id == "black")
    return scheme.copy(
        primary = r.primary, onPrimary = r.onPrimary,
        primaryContainer = r.primaryContainer, onPrimaryContainer = r.onPrimaryContainer,
        secondary = r.secondary, onSecondary = r.onSecondary,
        secondaryContainer = r.secondaryContainer, onSecondaryContainer = r.onSecondaryContainer,
        tertiary = r.tertiary, onTertiary = r.onTertiary,
        tertiaryContainer = r.tertiaryContainer, onTertiaryContainer = r.onTertiaryContainer,
        surfaceTint = r.primary,
    )
}

/**
 * A legible ink color (near-black or white) to lay over a flat [background],
 * chosen by perceived luminance.
 */
fun contrastInk(background: Color): Color =
    if (background.luminance() > 0.48f) Color(0xFF0E0E0E) else Color(0xFFFDFDFD)
