package com.ivor.ivormusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The glyph that leads a hub row, on Settings and on the Library alike.
 *
 * One treatment for both, because they are the same kind of row seen on two
 * screens: a soft plate of the tint at 12% with the glyph itself in that tint.
 * A solid fill here would make a list of destinations shout as loudly as the
 * play button, which is the one thing in this app allowed to.
 */
@Composable
fun RowIcon(
    icon: ImageVector,
    shape: Shape,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(shape)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
    }
}
