package com.nuvio.app.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.nuvio.app.core.ui.LocalScreenActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** An area of the screen that should receive the D-pad focus when its content changes (TV only). */
class TvFocusRegion internal constructor() {
    val requester = FocusRequester()
    var hasFocus by mutableStateOf(false)
        internal set
}

/**
 * Focus request handled by the TV app.
 * [region] null means "the primary region": the most recently registered active region.
 * Without [force] the request is ignored while something already holds the focus.
 */
class TvFocusRequest(
    val region: TvFocusRegion?,
    val force: Boolean,
)

object TvFocusRegions {
    private val activeRegions = mutableStateListOf<TvFocusRegion>()
    private val requestFlow = MutableSharedFlow<TvFocusRequest>(extraBufferCapacity = 8)

    /** Active regions in registration order: the last one is the primary region. */
    val regions: List<TvFocusRegion>
        get() = activeRegions

    val requests: SharedFlow<TvFocusRequest> = requestFlow.asSharedFlow()

    fun request(region: TvFocusRegion? = null, force: Boolean = false) {
        requestFlow.tryEmit(TvFocusRequest(region, force))
    }

    internal fun register(region: TvFocusRegion) {
        activeRegions.remove(region)
        activeRegions += region
    }

    internal fun unregister(region: TvFocusRegion) {
        activeRegions.remove(region)
    }
}

/**
 * Excludes an on-screen control from D-pad focus on TV (e.g. header back buttons: the remote
 * has a Back key). Must precede the clickable/focusable it applies to. No-op without a [TvShell].
 */
@Composable
fun Modifier.tvNotFocusable(): Modifier =
    if (LocalTvShell.current == null) this else focusProperties { canFocus = false }

/**
 * Marks a content area for the TV focus coordinator: every time [key] changes (page, category,
 * tab…) the focus moves to the first element of the area. No-op without a [TvShell].
 */
@Composable
fun Modifier.tvFocusRegion(key: Any?): Modifier {
    if (LocalTvShell.current == null) return this
    val active = LocalScreenActive.current
    val region = remember { TvFocusRegion() }
    DisposableEffect(region, active) {
        if (active) TvFocusRegions.register(region)
        onDispose { TvFocusRegions.unregister(region) }
    }
    LaunchedEffect(region, key, active) {
        if (active) TvFocusRegions.request(region, force = true)
    }
    return this
        .onFocusChanged { state -> region.hasFocus = state.hasFocus }
        .focusRequester(region.requester)
        .focusGroup()
}

/**
 * Focus behavior of a root tab pane. Phone/iOS keep the original `canFocus = active`.
 * On TV, visited tabs stay composed (hidden and unplaced) to keep their state: their elements
 * must never take the D-pad focus, otherwise it "disappears" into an invisible tab. The visible
 * tab is a [tvFocusRegion] and receives the focus when it is opened. The pane must stay a plain
 * focus group: a `canFocus = true` above it would make the whole (invisible) pane focusable.
 */
@Composable
internal fun Modifier.tvTabPaneFocus(tab: Any, active: Boolean): Modifier {
    if (LocalTvShell.current == null) return this.focusProperties { canFocus = active }
    return this
        .focusProperties {
            onEnter = { if (!active) cancelFocusChange() }
        }
        .tvFocusRegion(key = tab)
}
