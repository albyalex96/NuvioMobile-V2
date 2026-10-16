package com.nuvio.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import co.touchlab.kermit.Logger
import com.nuvio.app.tv.TvFocusRegion
import com.nuvio.app.tv.TvFocusRegions
import com.nuvio.app.tv.TvFocusRequest
import com.nuvio.app.tv.TvPlayerRemoteRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private val log = Logger.withTag("TvFocus")

/** Frames left to the new page to compose and lay out before moving the focus into it. */
private const val SETTLE_FRAMES = 3

/** Interval of the "nothing is focused" check. */
private const val IDLE_FOCUS_CHECK_MS = 500L

/** Attempts (one per frame) while lazy content attaches its first focusable item. */
private const val MAX_FOCUS_ATTEMPTS = 20

/**
 * Keeps the D-pad focus on the content the user just opened:
 * - a [TvFocusRegion] whose key changed (tab, settings page/category) receives the focus;
 * - when the focused element disappears (navigation to a new page, back), the focus moves to the
 *   primary region (highest priority, then last registered) or, if none, to the first element of the screen.
 * Screens that place the focus themselves (home, details) win: non-forced requests are dropped
 * once something holds the focus.
 */
@Composable
fun TvFocusCoordinator(content: @Composable () -> Unit) {
    val rootRequester = remember { FocusRequester() }
    val rootFocus = remember { RootFocusState() }

    LaunchedEffect(Unit) {
        TvFocusRegions.requests.collectLatest { request ->
            if (TvPlayerRemoteRegistry.active != null) return@collectLatest
            rootFocus.placing = true
            try {
                repeat(SETTLE_FRAMES) { withFrameNanos { } }
                moveFocus(request, rootRequester, rootFocus)
            } finally {
                rootFocus.placing = false
            }
        }
    }
    // Screens that never place the focus (profile picker, dialogs-free pages…): when nothing is
    // focused the remote would do nothing visible, so the first element of the screen gets it.
    LaunchedEffect(Unit) {
        while (true) {
            delay(IDLE_FOCUS_CHECK_MS)
            if (!rootFocus.hasFocus && !rootFocus.placing && TvPlayerRemoteRegistry.active == null) {
                TvFocusRegions.request(region = null, force = false)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { state ->
                val hadFocus = rootFocus.hasFocus
                rootFocus.hasFocus = state.hasFocus
                if (hadFocus && !state.hasFocus) {
                    TvFocusRegions.request(region = null, force = false)
                }
            }
            .focusRequester(rootRequester)
            .focusGroup(),
    ) {
        content()
    }
}

private class RootFocusState {
    var hasFocus = false
    var placing = false
}

private suspend fun moveFocus(
    request: TvFocusRequest,
    rootRequester: FocusRequester,
    rootFocus: RootFocusState,
) {
    if (!request.force && rootFocus.hasFocus) return
    repeat(MAX_FOCUS_ATTEMPTS) {
        val region = request.region
            ?.takeIf { it in TvFocusRegions.regions || it == TvFocusRegions.chrome }
            ?: TvFocusRegions.primary
        if (region != null) {
            if (region.hasFocus) return
            runCatching { region.requester.requestFocus(FocusDirection.Enter) }
            withFrameNanos { }
            if (region.hasFocus) {
                log.d { "Focus moved into content region (forced=${request.force})" }
                return
            }
        } else {
            if (rootFocus.hasFocus) return
            runCatching { rootRequester.requestFocus(FocusDirection.Enter) }
            withFrameNanos { }
            if (rootFocus.hasFocus) {
                log.d { "Focus moved to the first element of the screen" }
                return
            }
        }
    }
    // Idle checks retry on their own: only explicit navigations are worth a warning.
    if (request.force) log.w { "Could not place the focus after a navigation change" }
}
