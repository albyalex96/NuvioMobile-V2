package com.nuvio.app.core.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

private val GlassSurfaceColor = Color(0xFF1C1C1E)

/** Desktop glass bar: blurred backdrop with the frosted fill and light edge (no runtime shader). */
@Composable
internal fun GlassBarSurface(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    glowStrength: Float = 1f,
    /** Resolution of the blurred backdrop: below 1 the page is blurred at a lower resolution (cheaper). */
    inputScale: HazeInputScale = HazeInputScale.Default,
) {
    Box(
        modifier
            .then(if (hazeState != null) Modifier.barBackdrop(hazeState, inputScale) else Modifier)
            .drawWithCache {
                val fill = GlassSurfaceColor.copy(alpha = if (hazeState != null) 0.55f else 0.82f)
                val edge = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.27f), Color.White.copy(alpha = 0.02f)),
                )
                val width = 0.75.dp.toPx()
                onDrawBehind {
                    drawRect(fill)
                    drawRoundRect(
                        brush = edge,
                        topLeft = Offset(width / 2, width / 2),
                        size = Size(size.width - width, size.height - width),
                        cornerRadius = CornerRadius((size.height - width) / 2),
                        style = Stroke(width),
                        alpha = glowStrength,
                    )
                }
            },
    )
}

private fun Modifier.barBackdrop(hazeState: HazeState, inputScale: HazeInputScale): Modifier = hazeEffect(state = hazeState) {
    blurRadius = 24.dp
    this.inputScale = inputScale
    backgroundColor = GlassSurfaceColor
    tints = listOf(HazeTint(Color.Transparent))
    noiseFactor = 0f
}
