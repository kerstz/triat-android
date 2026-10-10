package com.ivor.ivormusic.data

/**
 * YouTube's own dismissal tokens for one feed item, when it carried any.
 *
 * These only exist on a signed-in InnerTube response - there is no account to
 * record a preference against otherwise - so every field is null for RSS,
 * NewPipe and signed-out items, and telling the account is skipped rather than
 * failed. Tokens are single-use and tied to the response that carried them,
 * which is why they ride along on the item instead of being fetched later.
 *
 * The undo tokens are pre-baked by YouTube into the same response, so taking
 * back a dismissal costs no extra round trip either. Verified August 2026.
 */
data class DismissalTokens(
    /** Hides this one video from the account's recommendations. */
    val notInterested: String? = null,
    val notInterestedUndo: String? = null,
    /** Stops the account being recommended this video's channel at all. */
    val blockChannel: String? = null,
    val blockChannelUndo: String? = null
)

/**
 * Represents a YouTube video item for Video Mode.
 * This is distinct from Song as it contains video-specific metadata.
 */
data class VideoItem(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String? = null,
    val channelIconUrl: String? = null,
    val thumbnailUrl: String?,
    val duration: Long, // Duration in seconds
    val viewCount: String, // Formatted view count like "1.2M views"
    val uploadedDate: String? = null, // e.g., "2 days ago"
    val isLive: Boolean = false,
    val description: String? = null,
    val subscriberCount: String? = null,
    /**
     * YouTube's dismissal tokens for this item, when the response carried
     * them. Null everywhere except signed-in InnerTube feeds.
     */
    val dismissal: DismissalTokens? = null
) {
    /**
     * High-resolution thumbnail URL.
     */
    val highResThumbnailUrl: String?
        get() = thumbnailUrl?.let { url ->
            when {
                url.contains("ytimg.com") || url.contains("youtube.com") -> {
                    url.replace("mqdefault", "maxresdefault")
                       .replace("hqdefault", "maxresdefault")
                       .replace("sddefault", "maxresdefault")
                }
                else -> url
            }
        }

    /**
     * Formatted duration string (e.g., "3:45" or "1:23:45").
     */
    val formattedDuration: String
        get() {
            if (isLive) return "LIVE"
            if (duration <= 0) return ""
            val hours = duration / 3600
            val minutes = (duration % 3600) / 60
            val seconds = duration % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }

    companion object {
        /**
         * Formats view count to human-readable format.
         */
        fun formatViewCount(count: Long?): String {
            if (count == null || count < 0) return ""
            return when {
                count >= 1_000_000_000 -> String.format("%.1fB views", count / 1_000_000_000.0)
                count >= 1_000_000 -> String.format("%.1fM views", count / 1_000_000.0)
                count >= 1_000 -> String.format("%.1fK views", count / 1_000.0)
                else -> "$count views"
            }
        }
    }
}

/**
 * One page of the video home feed. [continuation] is the InnerTube token for
 * the next page (browse continuation), or null when the source can't page
 * that way (taste-based and cold-start feeds page by seed offset instead).
 */
data class VideoFeedPage(
    val videos: List<VideoItem>,
    val continuation: String? = null
)

/**
 * A YouTube playlist shown in the video Library tab. Watch Later ("WL") and
 * Liked videos ("LL") are pinned entries built locally, not parsed.
 */
data class VideoPlaylist(
    val playlistId: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val videoCountText: String? = null, // e.g. "28 videos"
    val subtitle: String? = null // e.g. "Private"
)

