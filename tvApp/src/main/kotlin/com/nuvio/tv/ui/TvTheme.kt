package com.nuvio.tv.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.launch

/**
 * Applied inside the Nuvio theme for the whole TV app. Shared phone screens still shown on TV
 * (search, library, settings, streams…) use the default indication / Material ripple, which is
 * barely visible from the couch: this makes the focused element clearly highlighted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvThemeOverlay(content: @Composable () -> Unit) {
    val focusColor = MaterialTheme.nuvio.colors.focusRing
    val indication = remember(focusColor) { TvFocusIndication(focusColor) }
    val rippleConfiguration = remember(focusColor) {
        RippleConfiguration(
            color = focusColor,
            rippleAlpha = RippleAlpha(
                draggedAlpha = 0.16f,
                focusedAlpha = 0.32f,
                hoveredAlpha = 0.08f,
                pressedAlpha = 0.24f,
            ),
        )
    }
    CompositionLocalProvider(
        LocalIndication provides indication,
        LocalRippleConfiguration provides rippleConfiguration,
    ) {
        content()
    }
}

private class TvFocusIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        TvFocusIndicationNode(interactionSource, color)

    override fun equals(other: Any?): Boolean = other is TvFocusIndication && other.color == color

    override fun hashCode(): Int = color.hashCode()
}

private class TvFocusIndicationNode(
    private val interactionSource: InteractionSource,
    private val color: Color,
) : Modifier.Node(), DrawModifierNode {
    private var focused = false
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            var focusCount = 0
            var pressCount = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focusCount++
                    is FocusInteraction.Unfocus -> focusCount--
                    is PressInteraction.Press -> pressCount++
                    is PressInteraction.Release -> pressCount--
                    is PressInteraction.Cancel -> pressCount--
                }
                val nextFocused = focusCount > 0
                val nextPressed = pressCount > 0
                if (nextFocused != focused || nextPressed != pressed) {
                    focused = nextFocused
                    pressed = nextPressed
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (focused) {
            val radius = CornerRadius(10.dp.toPx())
            drawRoundRect(color = color.copy(alpha = 0.16f), cornerRadius = radius)
            val stroke = 2.5.dp.toPx()
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                cornerRadius = radius,
                style = Stroke(width = stroke),
            )
        }
        if (pressed) {
            drawRect(color = Color.White.copy(alpha = 0.12f))
        }
    }
}
