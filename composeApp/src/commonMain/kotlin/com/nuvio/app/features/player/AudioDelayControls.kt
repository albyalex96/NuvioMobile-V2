package com.nuvio.app.features.player

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_audio_delay
import nuvio.composeapp.generated.resources.compose_player_audio_delay_decrease
import nuvio.composeapp.generated.resources.compose_player_audio_delay_from_stream
import nuvio.composeapp.generated.resources.compose_player_audio_delay_hint
import nuvio.composeapp.generated.resources.compose_player_audio_delay_increase
import nuvio.composeapp.generated.resources.compose_player_audio_delay_saved_for_device
import nuvio.composeapp.generated.resources.compose_player_audio_delay_session_only
import nuvio.composeapp.generated.resources.compose_player_reset
import org.jetbrains.compose.resources.stringResource

private const val AUDIO_DELAY_HOLD_START_MS = 400L
private const val AUDIO_DELAY_HOLD_REPEAT_MS = 70L

/** Step for the n-th repeat while − / + is held: fine at first, then faster. */
internal fun audioDelayRepeatStepMs(repeat: Int): Int = when {
    repeat < 10 -> AUDIO_DELAY_STEP_MS
    repeat < 25 -> 100
    else -> 250
}

/**
 * Audio delay section of the Audio panel. [audioDelayMs] is the total delay applied to the
 * player; [streamDelayMs] is the part declared by the stream; [savedForDeviceLabel] names the
 * audio output the value is saved for (null = not saved).
 */
@Composable
internal fun AudioDelayControls(
    audioDelayMs: Int,
    streamDelayMs: Int,
    savedForDeviceLabel: String?,
    onAudioDelayChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentDelayMs = audioDelayMs.coerceIn(AUDIO_DELAY_MIN_MS, AUDIO_DELAY_MAX_MS)
    val latestDelayMs by rememberUpdatedState(currentDelayMs)
    val latestOnChanged by rememberUpdatedState(onAudioDelayChanged)
    val secondaryColor = Color.White.copy(alpha = 0.72f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.compose_player_audio_delay),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AudioDelayStepButton(
                icon = Icons.Rounded.Remove,
                contentDescription = stringResource(Res.string.compose_player_audio_delay_decrease),
                enabled = currentDelayMs > AUDIO_DELAY_MIN_MS,
                onStep = { stepMs -> latestOnChanged(latestDelayMs - stepMs) },
            )
            Box(
                modifier = Modifier
                    .widthIn(min = 96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatAudioDelay(currentDelayMs),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            AudioDelayStepButton(
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(Res.string.compose_player_audio_delay_increase),
                enabled = currentDelayMs < AUDIO_DELAY_MAX_MS,
                onStep = { stepMs -> latestOnChanged(latestDelayMs + stepMs) },
            )
            AudioDelayResetAction(
                label = stringResource(Res.string.compose_player_reset),
                // With a stream delay, "reset" goes back to what the stream declared.
                enabled = currentDelayMs != streamDelayMs,
                onClick = { onAudioDelayChanged(streamDelayMs) },
            )
        }
        val statusText = when {
            streamDelayMs != 0 -> stringResource(
                Res.string.compose_player_audio_delay_from_stream,
                formatAudioDelay(streamDelayMs),
            )
            savedForDeviceLabel != null -> stringResource(
                Res.string.compose_player_audio_delay_saved_for_device,
                savedForDeviceLabel,
            )
            else -> stringResource(Res.string.compose_player_audio_delay_session_only)
        }
        Text(
            text = statusText,
            color = secondaryColor,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(Res.string.compose_player_audio_delay_hint),
            color = Color.White.copy(alpha = 0.56f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * − / + button that repeats while held, both with touch and with the remote's OK key
 * (key repeats arrive as further KeyDown events). Not built on `clickable`, which only fires
 * once on release.
 */
@Composable
private fun AudioDelayStepButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onStep: (Int) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val latestOnStep by rememberUpdatedState(onStep)
    val latestEnabled by rememberUpdatedState(enabled)
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    var keyRepeatCount by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else tokens.opacity.disabled)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                onClick {
                    if (latestEnabled) latestOnStep(AUDIO_DELAY_STEP_MS)
                    latestEnabled
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) {
                    return@onPreviewKeyEvent false
                }
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (latestEnabled) latestOnStep(audioDelayRepeatStepMs(keyRepeatCount))
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
                    if (latestEnabled) latestOnStep(AUDIO_DELAY_STEP_MS)
                    val repeatJob = scope.launch {
                        delay(AUDIO_DELAY_HOLD_START_MS)
                        var repeat = 1
                        while (isActive && latestEnabled) {
                            latestOnStep(audioDelayRepeatStepMs(repeat))
                            repeat++
                            delay(AUDIO_DELAY_HOLD_REPEAT_MS)
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

@Composable
private fun AudioDelayResetAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio

    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else tokens.opacity.disabled)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            // Stays focusable when dimmed: on TV a control that turns disabled under the focus
            // would leave the D-pad with nothing focused.
            .clickable(onClick = { if (enabled) onClick() })
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
