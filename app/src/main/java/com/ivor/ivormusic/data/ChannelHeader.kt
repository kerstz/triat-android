package com.ivor.ivormusic.data

/**
 * Who a channel is, for the music artist page.
 *
 * Shapes verified against live InnerTube responses, signed out, August 2026 -
 * see [YouTubeRepository.getChannelHeader] for the endpoint and the renderer
 * notes. Koda draws no channel page of its own, so this is identity only.
 */

/**
 * Identity and display metadata for a channel, from `pageHeaderViewModel`
 * plus `metadata.channelMetadataRenderer` of the same response.
 *
 * The header is the half that drifts - `c4TabbedHeaderRenderer` was replaced
 * wholesale by `pageHeaderViewModel` - so everything optional here is genuinely
 * allowed to be null rather than being a parse failure. [name] and [channelId]
 * are the only two a page cannot be drawn without.
 */
data class ChannelHeader(
    val channelId: String,
    val name: String,
    /** "@handle", from the metadata row or the vanity URL. */
    val handle: String? = null,
    val avatarUrl: String? = null,
    /**
     * Banner art. Genuinely absent on many channels (verified on live
     * responses), so the UI must have something to draw in its place rather
     * than treating this as a loading state that never resolves.
     */
    val bannerUrl: String? = null,
    val subscriberCountText: String? = null,
    val videoCountText: String? = null,
    /** The truncated blurb YouTube shows under the name, not the full About text. */
    val descriptionPreview: String? = null,
    /** Verified tick, from a `CHECK_CIRCLE_FILLED` attachment run on the title. */
    val isVerified: Boolean = false,
    /** The one link YouTube promotes in the header, e.g. "lttstore.com". */
    val attributionText: String? = null,
    val attributionUrl: String? = null,
    /**
     * Continuation token for the About panel. YouTube does not put the full
     * about text in the channel response - it is a separate browse behind this
     * token - so the About tab is fetched on demand and only once.
     */
    val aboutToken: String? = null,
    /**
     * Whether the signed-in account follows this channel, when the response
     * said so.
     *
     * **Null means "the response did not say", not "no".** Signed out there is
     * no account state to report, and a shape change would silently turn a
     * subscribed channel into an unsubscribed-looking one, so the caller treats
     * null as a reason to go and ask rather than as an answer.
     */
    val accountSubscribed: Boolean? = null
) {
    val shareUrl: String
        get() = handle?.takeIf { it.startsWith("@") }
            ?.let { "https://www.youtube.com/$it" }
            ?: "https://www.youtube.com/channel/$channelId"
}
