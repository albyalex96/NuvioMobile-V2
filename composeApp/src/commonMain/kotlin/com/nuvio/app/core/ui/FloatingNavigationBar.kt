package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.staticCompositionLocalOf
import com.nuvio.app.core.ui.glass.NuvioBackdrop
import org.jetbrains.compose.resources.DrawableResource

internal class FloatingNavigationItem(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val drawable: DrawableResource? = null,
    val content: (@Composable (onClick: () -> Unit) -> Unit)? = null,
)

internal expect val floatingNavigationGlowSupported: Boolean

/**
 * Source of the floating bar's liquid glass: the screen content behind the bar records itself into
 * it (only while the glass is active), the bar refracts it. Null keeps the classic Haze surface.
 */
internal val LocalNavBarGlassBackdrop = staticCompositionLocalOf<NuvioBackdrop?> { null }

@Composable
internal expect fun FloatingNavigationBar(
    items: List<FloatingNavigationItem>,
    modifier: Modifier = Modifier,
    scrollState: NuvioNavBarScrollState? = null,
    hazeState: HazeState? = null,
    contentPadding: PaddingValues = floatingNavigationBarPadding(),
    compactSize: Boolean = false,
    glowEnabled: Boolean = true,
)

@Composable
internal fun floatingNavigationBarPadding(): PaddingValues = PaddingValues(
    bottom = nuvioBottomNavigationBarInsets().asPaddingValues().calculateBottomPadding() +
        nuvioBottomNavigationExtraVerticalPadding + 8.dp,
)
