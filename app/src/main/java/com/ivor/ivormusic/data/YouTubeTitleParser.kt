package com.ivor.ivormusic.data

/**
 * Utility for parsing raw YouTube video titles into clean (Title, Artist) pairs.
 */
object YouTubeTitleParser {

    /**
     * What a bracketed group may say to count as video packaging rather than part
     * of the song. Matched against the WHOLE content of the group, so
     * "(I Can't Get No)" or "(Live At Wembley)" never qualify. English, French,
     * Spanish and Portuguese spellings, since uploaders label in their own
     * language.
     */
    private const val NOISE_WORDS =
        "(?:official\\s+|officiel(?:le)?\\s+|oficial\\s+)?" +
            "(?:(?:hd|hq|4k|full\\s*hd|1080p|720p)\\s+)?" +
            "(?:(?:music|lyric|lyrics|audio|visual)\\s+)?" +
            "(?:video|vid[eé]o|videoclip|clip|visuali[sz]er|mv|m/v|audio|lyrics?|letras?|paroles|official|officiel(?:le)?|oficial)" +
            "(?:\\s+(?:officiel(?:le)?|oficial|hd|hq|4k|video|clip))?"

    /** Tags that are only ever packaging, bare. */
    private const val BARE_TAGS = "hd|hq|4k|1080p|720p|full\\s*hd|m/v|mv|with\\s+lyrics|lyrics\\s+on\\s+screen"

    /** A whole bracketed group made of noise: "(Official Video)", "[Lyrics]", "[4K]". */
    private val NOISE_GROUP = Regex(
        "(?i)\\s*[(\\[]\\s*(?:$NOISE_WORDS|$BARE_TAGS)\\s*[)\\]]"
    )

    /**
     * A trailing "| Official Video" / "- Official Audio" / "// Lyrics". Only the
     * unambiguous phrases may follow a bare separator: a lone "Video", "Audio" or
     * "HD" after a dash is more likely a real title ("Artist - Video") than
     * packaging, so those only count inside brackets.
     */
    private val NOISE_TRAILER = Regex(
        "(?i)\\s*(?:[|/\\\\\\u2013\\u2014-]{1,2})\\s*" +
            "(?:(?:official|officiel(?:le)?|oficial)\\s+(?:hd\\s+|4k\\s+)?(?:(?:music|lyric|lyrics|audio|visual)\\s+)?" +
            "(?:video|vid[eé]o|videoclip|clip|visuali[sz]er|audio|lyrics?|mv)|" +
            "(?:music|lyric|lyrics)\\s+video|lyrics|lyric|letra|paroles)\\s*$"
    )

    /**
     * A "// LETRAS4K" style channel trailer: after a spaced double slash, a short
     * tail with no lowercase letter at all (a tag, not words of a title).
     */
    private val SHOUTED_TRAILER = Regex("\\s+//\\s+[^a-z/]{2,30}$")

    private val DANGLING_SEPARATORS = Regex("[\\s|/\\\\\\u2013\\u2014:-]+$")
    private val MULTI_SPACE = Regex("\\s{2,}")

    /** Strips video packaging from a title, leaving the song's own name. Display only. */
    fun cleanTitle(rawTitle: String): String {
        var clean = rawTitle.trim()
        clean = NOISE_GROUP.replace(clean, "")
        clean = SHOUTED_TRAILER.replace(clean, "")
        clean = NOISE_TRAILER.replace(clean, "")
        // Removing a group can expose another trailer ("Song | Official Video [HD]").
        clean = NOISE_GROUP.replace(clean, "")
        clean = DANGLING_SEPARATORS.replace(clean, "")
        clean = MULTI_SPACE.replace(clean, " ").trim()
        return clean.removeSurrounding("\"", "\"").removeSurrounding("'", "'").trim()
    }

    /**
     * Cleans a raw video title and attempts to split artist and song title.
     * @param rawTitle Raw video title (e.g. "Daft Punk - One More Time (Official Video)")
     * @param rawAuthor Channel/Uploader name fallback (e.g. "Daft Punk" or "DaftPunkVEVO")
     * @return Pair of (Clean Title, Clean Artist)
     */
    fun parseTitleAndArtist(rawTitle: String, rawAuthor: String? = null): Pair<String, String> {
        val clean = cleanTitle(rawTitle)

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
