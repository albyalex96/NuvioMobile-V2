package com.nuvio.app.features.player

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val HOLD_REPEAT_START_MS = 400L
private const val HOLD_REPEAT_INTERVAL_MS = 70L

/**
 * − / + button that repeats while held, both with touch and with the remote's OK key
 * (key repeats arrive as further KeyDown events). Not built on `clickable`, which only fires
 * once on release. [stepForRepeat] gives the step of the n-th repeat (0 = the first press),
 * so the value can change faster the longer the button is held.
 */
@Composable
internal fun HoldRepeatStepButton(
    icon: ImageVector,
    contentDescription: String?,
    enabled: Boolean,
    stepForRepeat: (Int) -> Int,
    onStep: (Int) -> Unit,
    size: Dp = 44.dp,
) {
    val tokens = MaterialTheme.nuvio
    val latestOnStep by rememberUpdatedState(onStep)
    val latestStepForRepeat by rememberUpdatedState(stepForRepeat)
    val latestEnabled by rememberUpdatedState(enabled)
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    var keyRepeatCount by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .size(size)
            .alpha(if (enabled) 1f else tokens.opacity.disabled)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .semantics {
                role = Role.Button
                if (contentDescription != null) this.contentDescription = contentDescription
                onClick {
                    if (latestEnabled) latestOnStep(latestStepForRepeat(0))
                    latestEnabled
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) {
                    return@onPreviewKeyEvent false
                }
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (latestEnabled) latestOnStep(latestStepForRepeat(keyRepeatCount))
                        keyRepeatCount++
                        true
                    }
                    KeyEventType.KeyUp -> {
                        keyRepeatCount = 0
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    if (latestEnabled) latestOnStep(latestStepForRepeat(0))
                    val repeatJob = scope.launch {
                        delay(HOLD_REPEAT_START_MS)
                        var repeat = 1
                        while (isActive && latestEnabled) {
                            latestOnStep(latestStepForRepeat(repeat))
                            repeat++
                            delay(HOLD_REPEAT_INTERVAL_MS)
                        }
                    }
                    val up = waitForUpOrCancellation()
                    up?.consume()
                    repeatJob.cancel()
                    interactionSource.tryEmit(
                        if (up != null) PressInteraction.Release(press) else PressInteraction.Cancel(press),
                    )
                }
            }
            .indication(interactionSource, LocalIndication.current)
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}
