package com.ivor.ivormusic.data

/**
 * The musician behind a YouTube track, as opposed to whoever uploaded it.
 *
 * **A YouTube video's "artist" is its channel.** [verified on device, October
 * 2026] A history built from ordinary YouTube playback holds names like "nn",
 * "convar HUN" and "Music EDM 2022" - uploaders, reuploaders and compilation
 * channels. Those are what the taste profile ranked and what the artist graph
 * was handed, and none of them has a YouTube Music artist page: the browses
 * came back with zero songs and no "fans might also like" shelf, so the graph
 * had nothing to walk and the feed quietly fell back to searches.
 *
 * The title is where the real name is, because the convention is almost
 * universal on music uploads: "Daft Punk - One More Time". So the left side of
 * the first dash is taken when there is one, and the channel name is kept when
 * there is not.
 *
 * **Deliberately conservative.** It only splits on a dash with space around
 * it, which is the separator the convention uses - "Jay-Z" and "Blink-182"
 * survive untouched - and it refuses a result that is implausibly long or
 * empty. A wrong guess here costs a bad seed, so the rule is "obvious cases
 * only, uploader otherwise" rather than a parser that tries to be clever about
 * "feat.", brackets and the rest.
 *
 * Used for seeding and for the per-artist diversity cap, never for display:
 * what a row shows is what the source said, and rewriting that would be the
 * app disagreeing with itself about who made a track.
 */
internal fun musicArtistOf(title: String?, uploader: String?): String {
    val channel = uploader?.trim().orEmpty()
    val name = title?.trim().orEmpty()
    if (name.isEmpty()) return channel

    val index = name.indexOf(TITLE_SEPARATOR)
    if (index <= 0) return channel

    val candidate = name.take(index).trim().trim(*TRAILING_NOISE)
    if (candidate.isEmpty() || candidate.length > MAX_NAME_LENGTH) return channel
    // A title that leads with its own channel name says nothing new.
    if (candidate.equals(channel, ignoreCase = true)) return channel
    return candidate
}

/** [musicArtistOf] folded for use as a map key. */
internal fun artistKey(title: String?, uploader: String?): String =
    musicArtistOf(title, uploader).lowercase()

/**
 * Space-dash-space, not a bare dash: the convention puts spaces around the
 * separator, and a bare dash is part of plenty of real names.
 */
private const val TITLE_SEPARATOR = " - "

/**
 * Longer than this and the "artist" is a sentence - a tagline, or the whole
 * first clause of a title that happened to contain a dash.
 */
private const val MAX_NAME_LENGTH = 40

private val TRAILING_NOISE = charArrayOf(' ', '|', '/', '\\', '•', '·', '"', '\'')
