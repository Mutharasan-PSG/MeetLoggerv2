package com.meetloggerv2.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meetloggerv2.core.theme.GradientEnd
import com.meetloggerv2.core.theme.GradientStart

/**
 * The app's standard icon badge: a brand-gradient-filled rounded square holding a
 * white glyph. Used everywhere an icon needs a container so the whole app shares
 * one consistent, branded icon language (empty-state heroes, intro features,
 * dialog/section/list icons, etc.).
 *
 * @param size overall badge size; [iconSize] defaults to half of it.
 * @param cornerRadius corner radius of the badge; pass a large value (or use a
 *   circular [shape]) for round badges.
 * @param shadowElevation optional lift; useful for focal/hero badges.
 */
@Composable
fun GradientIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    cornerRadius: Dp = 14.dp,
    iconSize: Dp = size / 2,
    contentDescription: String? = null,
    shadowElevation: Dp = 0.dp,
    shape: Shape = RoundedCornerShape(cornerRadius)
) {
    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = Color.Transparent,
        shadowElevation = shadowElevation
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(listOf(GradientStart, GradientEnd))
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
