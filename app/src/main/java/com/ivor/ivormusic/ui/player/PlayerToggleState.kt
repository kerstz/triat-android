package com.ivor.ivormusic.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.media3.common.Player
import com.ivor.ivormusic.R

/**
 * What a screen reader says after a shuffle or repeat control's name. Every
 * player style draws these as a tile or chip whose on/off look is colour only,
 * so without a state description TalkBack reads "Shuffle, button" whatever the
 * mode. Repeat has three modes, so its text names the mode rather than on/off.
 */
@Composable
internal fun shuffleStateText(enabled: Boolean): String =
    stringResource(if (enabled) R.string.a11y_on else R.string.a11y_off)

@Composable
internal fun repeatStateText(repeatMode: Int): String = stringResource(
    when (repeatMode) {
        Player.REPEAT_MODE_ONE -> R.string.a11y_repeat_one
        Player.REPEAT_MODE_ALL -> R.string.a11y_repeat_all
        else -> R.string.a11y_off
    }
)

/** Exposes [state] to TalkBack; a null state leaves the semantics untouched. */
internal fun Modifier.toggleStateText(state: String?): Modifier =
    if (state == null) this else this.semantics { stateDescription = state }
