package com.ivor.ivormusic.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R

/** An optional single primary action shown under a [StateView] message. */
class StateAction(val label: String, val onClick: () -> Unit)

/**
 * The shared empty / error / offline / loading surface. One primary action at
 * most. Centered in the space it is given; [contentPadding] lets a caller that
 * sits under the floating nav bar and mini player keep clear of them.
 */
object StateView {

    @Composable
    fun Empty(
        title: String,
        modifier: Modifier = Modifier,
        body: String? = null,
        icon: ImageVector = Icons.Rounded.Inbox,
        action: StateAction? = null,
        contentPadding: PaddingValues = PaddingValues(0.dp)
    ) = Message(modifier, contentPadding, icon, title, body, action)

    @Composable
    fun Error(
        onRetry: () -> Unit,
        modifier: Modifier = Modifier,
        title: String = stringResource(R.string.state_error_title),
        body: String? = null,
        contentPadding: PaddingValues = PaddingValues(0.dp)
    ) = Message(
        modifier, contentPadding, Icons.Rounded.ErrorOutline, title, body,
        StateAction(stringResource(R.string.action_retry), onRetry)
    )

    @Composable
    fun Offline(
        modifier: Modifier = Modifier,
        body: String = stringResource(R.string.state_offline_body),
        action: StateAction? = null,
        contentPadding: PaddingValues = PaddingValues(0.dp)
    ) = Message(modifier, contentPadding, Icons.Rounded.WifiOff, null, body, action)

    /** Three dots pulsing in sequence over 1.2s; still when reduced motion is on. */
    @Composable
    fun Loading(
        modifier: Modifier = Modifier,
        contentPadding: PaddingValues = PaddingValues(0.dp)
    ) {
        val label = stringResource(R.string.state_loading)
        val reduced = rememberStateViewReducedMotion()
        val transition = rememberInfiniteTransition(label = "stateViewDots")
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(contentPadding)
                .padding(32.dp)
                .semantics {
                    contentDescription = label
                    liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { i ->
                    val pulse = if (reduced) {
                        0.6f
                    } else {
                        transition.animateFloat(
                            initialValue = 0.25f,
                            targetValue = 0.25f,
                            animationSpec = infiniteRepeatable(
                                animation = keyframes {
                                    durationMillis = 1200
                                    0.25f at i * 300 using LinearEasing
                                    1f at i * 300 + 300 using LinearEasing
                                    0.25f at i * 300 + 600 using LinearEasing
                                },
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "dot$i"
                        ).value
                    }
                    Box(
                        Modifier
                            .size(12.dp)
                            .alpha(pulse)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }

    @Composable
    private fun Message(
        modifier: Modifier,
        contentPadding: PaddingValues,
        icon: ImageVector,
        title: String?,
        body: String?,
        action: StateAction?
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(contentPadding)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (title != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
            }
            if (body != null) {
                Spacer(Modifier.height(if (title != null) 6.dp else 16.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (action != null) {
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(onClick = action.onClick) { Text(action.label) }
            }
        }
    }
}

/**
 * True when the user turned animations off (animator duration scale 0).
 * Private stand-in until ui/theme/TriatMotion.kt provides rememberReducedMotion().
 */
@Composable
private fun rememberStateViewReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
