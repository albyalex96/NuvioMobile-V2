package com.nuvio.app.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val log = Logger.withTag("TvLongPress")

/** OK held this long triggers the long click (same as the TV app's own cards). */
private const val TV_SHARED_LONG_PRESS_MS = 400L

private val ConfirmKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Remote keys for shared clickable cards with a long click (library, search…): OK held for
 * [TV_SHARED_LONG_PRESS_MS] or Menu runs [onLongClick], a short OK runs [onClick] on release.
 * Apply it before the clickable modifier. No-op without a [TvShell].
 */
@Composable
fun Modifier.tvLongPressKeys(onClick: (() -> Unit)?, onLongClick: (() -> Unit)?): Modifier {
    if (LocalTvShell.current == null || onLongClick == null) return this
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var longPressFired by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    return this
        .onFocusChanged { state ->
            if (!state.hasFocus) {
                longPressJob?.cancel()
                pressed = false
            }
        }
        .onPreviewKeyEvent { event ->
            when {
                event.key == Key.Menu -> {
                    if (event.type == KeyEventType.KeyDown) {
                        log.d { "Menu key: card options" }
                        currentOnLongClick()
                    }
                    true
                }
                event.key in ConfirmKeys && event.type == KeyEventType.KeyDown -> {
                    // Repeated key-downs of a held OK are swallowed.
                    if (!pressed) {
                        pressed = true
                        longPressFired = false
                        longPressJob?.cancel()
                        longPressJob = scope.launch {
                            delay(TV_SHARED_LONG_PRESS_MS)
                            longPressFired = true
                            log.d { "OK held: card options" }
                            currentOnLongClick()
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
                    if (shortPress) currentOnClick?.invoke()
                    true
                }
                else -> false
            }
        }
}
