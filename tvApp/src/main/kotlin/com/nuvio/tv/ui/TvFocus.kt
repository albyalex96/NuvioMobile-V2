package com.nuvio.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio

/** Duration of focus scale/border animations (NuvioTV `focusTween`). */
const val TV_FOCUS_ANIMATION_MS = 180

private val ConfirmKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Focusable + clickable surface for D-pad navigation: scales up and draws a focus ring when
 * focused. Holding OK (or pressing Menu) triggers [onLongClick].
 */
@Composable
fun Modifier.tvFocusable(
    shape: Shape,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    focusedScale: Float = 1.06f,
    borderWidth: Dp = 3.dp,
    enabled: Boolean = true,
    onFocusChange: ((Boolean) -> Unit)? = null,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = tween(TV_FOCUS_ANIMATION_MS),
        label = "tvFocusScale",
    )
    val ringColor = MaterialTheme.nuvio.colors.focusRing
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    var longPressFired by remember { mutableStateOf(false) }

    return this
        .onFocusChanged { state -> onFocusChange?.invoke(state.hasFocus) }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(if (focused) Modifier.shadow(elevation = 18.dp, shape = shape, clip = false) else Modifier)
        .border(
            width = if (focused) borderWidth else 0.dp,
            color = if (focused) ringColor else Color.Transparent,
            shape = shape,
        )
        .clip(shape)
        .onPreviewKeyEvent { event ->
            val longClick = currentOnLongClick ?: return@onPreviewKeyEvent false
            when {
                event.key == Key.Menu && event.type == KeyEventType.KeyDown -> {
                    longClick()
                    true
                }
                event.key in ConfirmKeys && event.type == KeyEventType.KeyDown -> {
                    if (event.nativeKeyEvent.repeatCount == 0) {
                        longPressFired = false
                        false
                    } else {
                        if (!longPressFired) {
                            longPressFired = true
                            longClick()
                        }
                        true
                    }
                }
                event.key in ConfirmKeys && event.type == KeyEventType.KeyUp && longPressFired -> {
                    longPressFired = false
                    true
                }
                else -> false
            }
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}
