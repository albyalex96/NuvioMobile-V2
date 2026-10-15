package com.nuvio.app.features.streams.parser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Vector icons that Material Icons does not provide. */
internal object StreamParserIcons {
    /** Horseshoe magnet with separate pole tips (24×24 grid, pointing up). */
    val Magnet: ImageVector by lazy {
        ImageVector.Builder(
            name = "StreamParserMagnet",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Pole tips.
                moveTo(4f, 3f)
                horizontalLineTo(9f)
                verticalLineTo(6f)
                horizontalLineTo(4f)
                close()
                moveTo(15f, 3f)
                horizontalLineTo(20f)
                verticalLineTo(6f)
                horizontalLineTo(15f)
                close()
                // U-shaped body.
                moveTo(4f, 7.5f)
                horizontalLineTo(9f)
                verticalLineTo(12f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 15f, y1 = 12f)
                verticalLineTo(7.5f)
                horizontalLineTo(20f)
                verticalLineTo(12f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 4f, y1 = 12f)
                close()
            }
        }.build()
    }
}
