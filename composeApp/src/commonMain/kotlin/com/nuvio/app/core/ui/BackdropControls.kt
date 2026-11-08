package com.nuvio.app.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** True while the content is drawn over an artwork backdrop (cinematic or dominant-color details). */
internal val LocalOnBackdrop = staticCompositionLocalOf { false }

/**
 * Fill of a details button or chip: over an artwork backdrop it becomes a faint see-through tint so
 * the artwork shows through, elsewhere it keeps [default].
 */
@Composable
internal fun backdropControlColor(default: Color): Color =
    if (LocalOnBackdrop.current) {
        MaterialTheme.nuvio.colors.textPrimary.copy(alpha = NuvioTokens.Opacity.hover)
    } else {
        default
    }
