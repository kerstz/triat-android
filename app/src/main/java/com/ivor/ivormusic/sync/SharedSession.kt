package com.ivor.ivormusic.sync

import com.ivor.ivormusic.data.YouTubeAuthUtils

/** The largest cookie string accepted from the desktop: 64 KiB of characters. */
internal const val MAX_SHARED_SESSION_CHARS = 64 * 1024

/**
 * What the phone does with the YouTube session a desktop sends, once the user
 * agreed to receive it. [NONE]: nothing came, or it is the one already applied.
 */
internal enum class SessionOutcome { NONE, REJECTED, ACCEPTED }

/**
 * Why the cookie string a desktop shares must not become this phone's session,
 * or null when it may. Pure, so it is tested on the JVM. The consent to receive
 * a session lets the desktop switch the phone's Google account; this gate keeps
 * a malformed or truncated string from doing even that:
 *
 * - [PROBLEM_TOO_LARGE]: over [MAX_SHARED_SESSION_CHARS];
 * - [PROBLEM_CONTROL_CHARACTERS]: a cookie string is one header line, so any
 *   control character (newline included) is refused;
 * - [PROBLEM_INCOMPLETE]: `SAPISID` or `SID` is missing, the same check the
 *   sign-in dialog and the paste sheet make.
 *
 * The result is a short code and never contains a cookie value, so it can be logged.
 */
internal fun sharedSessionProblem(cookies: String): String? {
    if (cookies.length > MAX_SHARED_SESSION_CHARS) return PROBLEM_TOO_LARGE
    if (cookies.any { it.isISOControl() }) return PROBLEM_CONTROL_CHARACTERS
    if (YouTubeAuthUtils.missingRequiredCookies(cookies).isNotEmpty()) return PROBLEM_INCOMPLETE
    return null
}

internal const val PROBLEM_TOO_LARGE = "too_large"
internal const val PROBLEM_CONTROL_CHARACTERS = "control_characters"
internal const val PROBLEM_INCOMPLETE = "incomplete"
