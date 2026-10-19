package com.nuvio.app.tv

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Where NuvioTV keeps the focused element while scrolling (`scrollViewportTarget`). */
private const val ViewportTarget = 0.42f

/**
 * NuvioTV `smoothScrollSpec`: moving the focus scrolls so that the focused element sits a bit
 * above the middle of the list, instead of stopping at the edge with its neighbours cut off.
 */
@OptIn(ExperimentalFoundationApi::class)
private val TvCenteredBringIntoViewSpec = object : BringIntoViewSpec {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = spring(dampingRatio = 0.95f, stiffness = 180f)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (containerSize <= 0f || size <= 0f) return 0f
        // Taller than the list: show its top (centering would hide the beginning).
        if (size >= containerSize) return offset
        val distance = offset + size / 2f - containerSize * ViewportTarget
        // Already visible and almost in place: do not nudge the list for a few pixels.
        if (offset >= 0f && offset + size <= containerSize && distance > 0f && distance < 12f) return 0f
        return distance
    }
}

/** On the TV app, scrollables inside [content] keep the focused element near the center. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvCenteredScrolling(content: @Composable () -> Unit) {
    if (LocalTvShell.current == null) {
        content()
        return
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides TvCenteredBringIntoViewSpec, content = content)
}

/**
 * Fades the content out over [edge] at the top and bottom of a scrolling list, so the rows that
 * leave the viewport dissolve instead of being cut by a hard line. No-op when [enabled] is false.
 */
fun Modifier.tvFadingEdges(enabled: Boolean, edge: Dp = 28.dp): Modifier {
    if (!enabled) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = edge.toPx().coerceAtMost(size.height / 2f)
            if (fade <= 0f) return@drawWithContent
            drawRect(
                brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = fade),
                blendMode = BlendMode.DstIn,
            )
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black,
                    1f to Color.Transparent,
                    startY = size.height - fade,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}
