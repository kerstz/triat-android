package com.ivor.ivormusic.data

import java.time.LocalDate


/**
 * Result ordering for video mode search, mirroring YouTube's own "Sort by"
 * filter. [code] is the sort value inside the InnerTube `sp` search params
 * (the same protobuf the youtube.com filter sheet sends); non-relevance
 * orders route the search through a direct /search call because NewPipe's
 * YouTube search has no sort support. Verified July 2026 against the live
 * /search endpoint.
 */
enum class VideoSearchSort(val label: String, val code: Int) {
    RELEVANCE("Relevance", 0),
    UPLOAD_DATE("Newest", 2),
    VIEW_COUNT("Most viewed", 3),
    RATING("Top rated", 1)
}
