package com.ivor.ivormusic.data

/**
 * Utility for parsing raw YouTube video titles into clean (Title, Artist) pairs.
 */
object YouTubeTitleParser {

    private val VIDEO_NOISE_PATTERNS = listOf(
        Regex("(?i)\\[\\s*official\\s*(music\\s*)?video\\s*\\]"),
        Regex("(?i)\\(\\s*official\\s*(music\\s*)?video\\s*\\)"),
        Regex("(?i)\\(\\s*official\\s*audio\\s*\\)"),
        Regex("(?i)\\[\\s*official\\s*audio\\s*\\]"),
        Regex("(?i)\\(\\s*clip\\s*officiel\\s*\\)"),
        Regex("(?i)\\[\\s*clip\\s*officiel\\s*\\]"),
        Regex("(?i)\\(\\s*lyric\\s*video\\s*\\)"),
        Regex("(?i)\\[\\s*lyric\\s*video\\s*\\]"),
        Regex("(?i)\\(\\s*lyrics\\s*\\)"),
        Regex("(?i)\\[\\s*lyrics\\s*\\]"),
        Regex("(?i)\\(\\s*visualizer\\s*\\)"),
        Regex("(?i)\\[\\s*visualizer\\s*\\]"),
        Regex("(?i)\\[\\s*4k\\s*\\]"),
        Regex("(?i)\\(\\s*4k\\s*\\)"),
        Regex("(?i)\\[\\s*hd\\s*\\]"),
        Regex("(?i)\\(\\s*hd\\s*\\)"),
        Regex("(?i)\\[\\s*mv\\s*\\]"),
        Regex("(?i)\\(\\s*mv\\s*\\)"),
        Regex("(?i)\\(\\s*audio\\s*\\)"),
        Regex("(?i)\\[\\s*audio\\s*\\]"),
        Regex("(?i)\\(\\s*official\\s*\\)"),
        Regex("(?i)\\[\\s*official\\s*\\]")
    )

    /**
     * Cleans a raw video title and attempts to split artist and song title.
     * @param rawTitle Raw video title (e.g. "Daft Punk - One More Time (Official Video)")
     * @param rawAuthor Channel/Uploader name fallback (e.g. "Daft Punk" or "DaftPunkVEVO")
     * @return Pair of (Clean Title, Clean Artist)
     */
    fun parseTitleAndArtist(rawTitle: String, rawAuthor: String? = null): Pair<String, String> {
        var clean = rawTitle.trim()

        // Strip common noise brackets/parentheses
        for (pattern in VIDEO_NOISE_PATTERNS) {
            clean = pattern.replace(clean, "").trim()
        }

        // Remove trailing punctuation / quotes
        clean = clean.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'").trim()

        // Normalize author if provided (strip VEVO, - Topic, Official)
        val cleanAuthor = rawAuthor?.let { normalizeChannelName(it) }?.takeIf { !isUnknownArtist(it) } ?: UNKNOWN_ARTIST

        // Preserve full original title (with video noise stripped)
        val finalTitle = if (clean.isNotBlank()) clean else rawTitle
        return Pair(finalTitle, cleanAuthor)
    }

    /**
     * Cleans channel/author names like "ArtistVEVO" -> "Artist" or "Artist - Topic" -> "Artist".
     */
    fun normalizeChannelName(rawName: String): String {
        var name = rawName.trim()
        if (name.endsWith("VEVO", ignoreCase = true) && name.length > 4) {
            name = name.substring(0, name.length - 4).trim()
        }
        if (name.endsWith(" - Topic", ignoreCase = true)) {
            name = name.substring(0, name.length - 8).trim()
        }
        if (name.endsWith(" Official", ignoreCase = true)) {
            name = name.substring(0, name.length - 9).trim()
        }
        return if (name.isNotBlank()) name else UNKNOWN_ARTIST
    }
}
