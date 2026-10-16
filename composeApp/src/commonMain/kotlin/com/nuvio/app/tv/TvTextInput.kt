package com.nuvio.app.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred

private val log = Logger.withTag("TvTextInput")

private val ConfirmKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * TV text fields: focusing a field with the D-pad only highlights it; the keyboard opens when OK
 * is pressed. Every text field asks the platform for an input session when it gains focus: the
 * interceptor holds that request until [openKeyboard].
 */
object TvTextInputGate {
    /** A focused text field is waiting for OK before opening the keyboard. */
    var waiting by mutableStateOf(false)
        private set

    /** A text field holds an input session (keyboard shown, or hidden with Back). */
    var editing by mutableStateOf(false)
        private set

    private var pending: CompletableDeferred<Unit>? = null

    @OptIn(ExperimentalComposeUiApi::class)
    internal val interceptor = PlatformTextInputInterceptor { request, nextHandler ->
        val gate = CompletableDeferred<Unit>()
        pending = gate
        waiting = true
        try {
            gate.await()
        } finally {
            if (pending === gate) {
                pending = null
                waiting = false
            }
        }
        log.d { "Keyboard opened" }
        editing = true
        try {
            nextHandler.startInputMethod(request)
        } finally {
            editing = false
        }
    }

    /** Releases the held input session: the keyboard appears. False when no field is waiting. */
    fun openKeyboard(): Boolean {
        val gate = pending ?: return false
        gate.complete(Unit)
        return true
    }
}

/** Applies [TvTextInputGate] to [content] (TV shell only). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvTextInputHost(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(interceptor = TvTextInputGate.interceptor, content = content)
}

/**
 * Remote keys for the gated text fields of a window (the app root and each dialog): OK opens the
 * keyboard of the focused field (again, if it was closed with Back); while the field only shows
 * the focus, left/right move the focus instead of the cursor. No-op without a [TvShell].
 */
@Composable
fun Modifier.tvTextInputKeys(): Modifier {
    if (LocalTvShell.current == null) return this
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            return@onPreviewKeyEvent TvTextInputGate.waiting && event.key in ConfirmKeys
        }
        when {
            TvTextInputGate.waiting && event.key in ConfirmKeys -> TvTextInputGate.openKeyboard()
            TvTextInputGate.waiting && event.key == Key.DirectionLeft -> {
                focusManager.moveFocus(FocusDirection.Left)
                true
            }
            TvTextInputGate.waiting && event.key == Key.DirectionRight -> {
                focusManager.moveFocus(FocusDirection.Right)
                true
            }
            // Keys reach the app only while the keyboard is hidden: OK shows it again.
            TvTextInputGate.editing && event.key in ConfirmKeys && keyboard != null -> {
                keyboard.show()
                true
            }
            else -> false
        }
    }
}
