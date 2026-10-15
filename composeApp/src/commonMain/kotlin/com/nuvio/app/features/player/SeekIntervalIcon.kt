package com.nuvio.app.features.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * Replay/Forward icon with a variable number of seconds, drawn on the same 24-unit grid
 * as the Material `Replay10`/`Forward10` icons (circular arrow, number in the middle).
 */
@Composable
internal fun SeekIntervalIcon(
    seconds: Int,
    forward: Boolean,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        val unit = size.minDimension / 24f
        fun x(value: Float) = (if (forward) 24f - value else value) * unit

        val radius = 7f * unit
        val center = Offset(12f * unit, 13f * unit)
        drawArc(
            color = tint,
            // Gap at the top-left (backward) or top-right (forward), where the arrow head sits.
            startAngle = if (forward) 0f else -90f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = 2f * unit),
        )
        val arrowHead = Path().apply {
            moveTo(x(12.5f), 1.5f * unit)
            lineTo(x(7.5f), 6f * unit)
            lineTo(x(12.5f), 10.5f * unit)
            close()
        }
        drawPath(arrowHead, color = tint)

        val label = textMeasurer.measure(
            text = seconds.toString(),
            style = TextStyle(
                color = tint,
                fontSize = (7f * unit).toSp(),
                fontWeight = FontWeight.Bold,
            ),
        )
        drawText(
            textLayoutResult = label,
            topLeft = Offset(
                x = center.x - label.size.width / 2f,
                y = center.y - label.size.height / 2f,
            ),
        )
    }
}
