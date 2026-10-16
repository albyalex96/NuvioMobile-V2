package com.nuvio.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val PrimaryActionShape = RoundedCornerShape(40.dp)

/**
 * Main call to action (e.g. "Play", "View details"): a pill with a light gradient tinted by the
 * theme accent, a glossy top highlight, an accent glow and a small press animation.
 * Dark label on light fill, so it stays readable with every theme accent.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrimaryActionButton(
    text: String,
    icon: Painter?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
    iconSize: Dp = 18.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "primary_action_press",
    )
    val contentColor = primaryActionContentColor(enabled)

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .primaryActionSurface(shape = PrimaryActionShape, enabled = enabled)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
                role = Role.Button,
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = textStyle,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Label/icon color on [primaryActionSurface]. */
@Composable
fun primaryActionContentColor(enabled: Boolean = true): Color =
    if (enabled) {
        lerp(Color(0xFF0B0B0F), MaterialTheme.nuvio.colors.accent, 0.12f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

/**
 * Fill of the main call-to-action buttons, shared with the TV app: light gradient tinted by the
 * theme accent, glossy highlight on the upper half, gradient border and an accent glow.
 */
@Composable
fun Modifier.primaryActionSurface(
    shape: Shape,
    enabled: Boolean = true,
    glow: Boolean = true,
): Modifier {
    val accent = MaterialTheme.nuvio.colors.accent
    val disabledFill = MaterialTheme.colorScheme.surfaceVariant
    val fill = if (enabled) {
        Brush.linearGradient(
            colors = listOf(
                Color.White,
                lerp(Color.White, accent, 0.18f),
                lerp(Color.White, accent, 0.42f),
            ),
        )
    } else {
        Brush.linearGradient(listOf(disabledFill, disabledFill))
    }
    return this
        .then(
            if (enabled && glow) {
                Modifier.shadow(
                    elevation = 14.dp,
                    shape = shape,
                    ambientColor = accent.copy(alpha = 0.55f),
                    spotColor = accent.copy(alpha = 0.75f),
                )
            } else {
                Modifier
            },
        )
        .clip(shape)
        .background(fill)
        .background(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (enabled) 0.55f else 0f),
                0.5f to Color.White.copy(alpha = 0f),
            ),
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (enabled) 0.9f else 0.1f),
                    accent.copy(alpha = if (enabled) 0.35f else 0f),
                ),
            ),
            shape = shape,
        )
}
