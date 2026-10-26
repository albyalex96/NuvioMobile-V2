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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Duration of focus scale/border animations (NuvioTV `focusTween`). */
const val TV_FOCUS_ANIMATION_MS = 180

/** OK held this long triggers the long click (the system key repeat starts later, ~500+ ms). */
const val TV_LONG_PRESS_MS = 400L

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
    // Cards that keep their size on focus (NuvioTV home rows) skip the scale animation and its layer.
    val scales = focusedScale != 1f
    val scale = if (scales) {
        animateFloatAsState(
            targetValue = if (focused) focusedScale else 1f,
            animationSpec = tween(TV_FOCUS_ANIMATION_MS),
            label = "tvFocusScale",
        )
    } else {
        null
    }
    val ringColor = MaterialTheme.nuvio.colors.focusRing
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentEnabled by rememberUpdatedState(enabled)
    val scope = rememberCoroutineScope()
    // OK press tracked here (not by the clickable) when a long click exists: the press must start
    // on this element, and the long click fires on a timer instead of waiting for key repeats.
    var pressed by remember { mutableStateOf(false) }
    var longPressFired by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }

    return this
        .onFocusChanged { state ->
            if (!state.hasFocus) {
                longPressJob?.cancel()
                pressed = false
            }
            onFocusChange?.invoke(state.hasFocus)
        }
        .then(
            if (scale != null) {
                Modifier.graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
            } else {
                Modifier
            },
        )
        // Cards that keep their size on focus (NuvioTV home rows) only get the ring.
        .then(if (focused && focusedScale > 1f) Modifier.shadow(elevation = 18.dp, shape = shape, clip = false) else Modifier)
        .then(if (focused && borderWidth > 0.dp) Modifier.border(width = borderWidth, color = ringColor, shape = shape) else Modifier)
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
                        pressed = true
                        longPressFired = false
                        longPressJob?.cancel()
                        longPressJob = scope.launch {
                            delay(TV_LONG_PRESS_MS)
                            longPressFired = true
                            longClick()
                        }
                    }
                    true
                }
                event.key in ConfirmKeys && event.type == KeyEventType.KeyUp -> {
                    val shortPress = pressed && !longPressFired
                    longPressJob?.cancel()
                    longPressJob = null
                    pressed = false
                    longPressFired = false
                    if (shortPress && currentEnabled) currentOnClick()
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
