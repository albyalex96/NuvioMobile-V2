package com.nuvio.tv.ui

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import co.touchlab.kermit.Logger
import com.nuvio.app.tv.TvPlayerRemoteRegistry

private val log = Logger.withTag("TvDpad")

/** Minimum time between two repeats of a held arrow key (NuvioTV `dpadRepeatThrottle`). */
private const val HORIZONTAL_REPEAT_GATE_MS = 80L
private const val VERTICAL_REPEAT_GATE_MS = 112L

/**
 * Drops the repeats of a held D-pad arrow that arrive faster than the list can follow (the remote
 * repeats every ~50 ms): each move recomposes and scrolls, and on TV sticks a backlog of moves
 * makes the focus stutter and keep moving after the key is released. The first press and the
 * repeats past the gate go through untouched. The player handles its own keys (seek on hold).
 */
@Composable
fun Modifier.tvDpadRepeatThrottle(): Modifier {
    val lastRepeat = remember { LongArray(1) }
    val dropped = remember { IntArray(1) }
    return onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        if (native.action != KeyEvent.ACTION_DOWN || TvPlayerRemoteRegistry.active != null) return@onPreviewKeyEvent false
        val vertical = when (native.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> true
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> false
            else -> return@onPreviewKeyEvent false
        }
        if (native.repeatCount == 0) {
            if (dropped[0] > 0) log.d { "Held D-pad key: ${dropped[0]} fast repeats skipped" }
            dropped[0] = 0
            lastRepeat[0] = 0L
            return@onPreviewKeyEvent false
        }
        val now = SystemClock.uptimeMillis()
        val gate = if (vertical) VERTICAL_REPEAT_GATE_MS else HORIZONTAL_REPEAT_GATE_MS
        if (now - lastRepeat[0] < gate) {
            dropped[0]++
            return@onPreviewKeyEvent true
        }
        lastRepeat[0] = now
        false
    }
}
