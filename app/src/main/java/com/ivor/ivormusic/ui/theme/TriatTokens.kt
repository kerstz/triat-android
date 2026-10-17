package com.ivor.ivormusic.ui.theme

import androidx.compose.ui.graphics.Color

// Source: design/tokens.json in the Triat desktop repository (tokens version 1).
// Regenerate from the desktop checkout with:
//   python3 -c "import sys; sys.path.insert(0, 'tools'); import gen_tokens as g; print(g.render_kotlin(g.load_tokens()), end='')"
// and paste the output here unchanged. Veils are translucent white over ground;
// TriatColorScheme flattens them for Material roles.
// Genere par tools/gen_tokens.py depuis design/tokens.json : ne pas editer.
object TriatTokens {
    val ground = Color(0xFF000000)
    val groundSolid = Color(0xFF050505)
    val groundGlass = Color(0xE0000000)
    val veil1 = Color(0x0AFFFFFF)
    val veil2 = Color(0x12FFFFFF)
    val veil3 = Color(0x1CFFFFFF)
    val veil4 = Color(0x29FFFFFF)
    val hair = Color(0x14FFFFFF)
    val bar = Color(0xB8080808)
    val scrim = Color(0x8C000000)
    val scrim2 = Color(0x9E000000)
    val shadow = Color(0x80000000)
    val inkMax = Color(0xFFFFFFFF)
    val ink = Color(0xFFF2F2F2)
    val ink2 = Color(0xFFB3B3B3)
    val ink3 = Color(0xFF8C8C8C)
    val ink4 = Color(0xFF6B6B6B)
    val dotOff = Color(0xFF2A2A2A)
    val disabled = Color(0xFF5A5A5A)
    val signal = Color(0xFFD71921)
    val signalInk = Color(0xFFFF4D55)
    val selectBg = Color(0xFFFFFFFF)
    val selectFg = Color(0xFF000000)
    val danger = Color(0xFFFF6B6B)
    val dangerSoft = Color(0x29FF6B6B)
    val ok = Color(0xFF4FD18B)
    val focus = Color(0xFFFFFFFF)

    const val s1Dp = 4
    const val s2Dp = 8
    const val s3Dp = 12
    const val s4Dp = 16
    const val s5Dp = 24
    const val s6Dp = 32
    const val s7Dp = 48
    const val s8Dp = 64
    const val rPanelDp = 8
    const val rArtSDp = 6
    const val rArtLDp = 12
    const val rSheetDp = 28
    const val rPillDp = 999
    const val displayXlSp = 44
    const val displayLSp = 32
    const val displayMSp = 22
    const val titleLSp = 22
    const val titleMSp = 18
    const val titleSSp = 16
    const val bodySp = 15
    const val bodySSp = 14
    const val metaSp = 12
    const val monoSp = 12
    const val monoSSp = 11
    const val tFastMs = 120
    const val tBaseMs = 200
    const val tPageMs = 280
    const val tArtMs = 360
}
