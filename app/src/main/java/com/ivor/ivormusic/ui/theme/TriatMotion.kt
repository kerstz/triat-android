package com.ivor.ivormusic.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Triat motion tokens as Compose specs (durations from [TriatTokens]) and the
 * single reduced-motion switch. Calm and short: no bounce except for direct
 * gestures ([springTouch]).
 */
object TriatMotion {
    val easeOut = FastOutSlowInEasing
    val easePage = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val easeInOut = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** Hover, press. */
    fun <T> fast(): TweenSpec<T> = tween(TriatTokens.tFastMs, easing = easeOut)

    /** State change, toasts. */
    fun <T> base(): TweenSpec<T> = tween(TriatTokens.tBaseMs, easing = easeOut)

    /** Page, opening the player. */
    fun <T> page(): TweenSpec<T> = tween(TriatTokens.tPageMs, easing = easePage)

    /** Crossfade between covers. */
    fun <T> art(): TweenSpec<T> = tween(TriatTokens.tArtMs, easing = easeInOut)

    /** Default spring: no bounce. */
    fun <T> springCalm() = spring<T>(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)

    /** Direct-manipulation gestures only (swipe to skip, dragging the player). */
    fun <T> springTouch() = spring<T>(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium)

    /**
     * Whether the user turned animations off (system animator scale 0). Plain
     * read, for non-composable callers and observers.
     */
    fun isReducedMotion(resolver: android.content.ContentResolver): Boolean =
        runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
}

/**
 * The one reduced-motion answer in the app. Reads
 * `Settings.Global.ANIMATOR_DURATION_SCALE == 0` and follows changes made
 * while the screen is shown.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember(context) { mutableStateOf(TriatMotion.isReducedMotion(context.contentResolver)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = TriatMotion.isReducedMotion(context.contentResolver)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer
        )
        reduced = TriatMotion.isReducedMotion(context.contentResolver)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}
