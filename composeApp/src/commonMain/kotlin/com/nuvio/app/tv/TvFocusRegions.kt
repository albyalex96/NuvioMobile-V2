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
import com.nuvio.app.core.ui.PlatformBackHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** An area of the screen that should receive the D-pad focus when its content changes (TV only). */
class TvFocusRegion internal constructor() {
    val requester = FocusRequester()
    var hasFocus by mutableStateOf(false)
        internal set

    /** Higher wins when the primary region is chosen (ties: most recently registered). */
    internal var priority = 0
    internal var lastKey: Any? = NoKey

    internal object NoKey
}

/** A region whose state (e.g. [TvFocusRegion.hasFocus]) the caller needs: pass it to [tvFocusRegion]. */
@Composable
fun rememberTvFocusRegion(): TvFocusRegion = remember { TvFocusRegion() }

/**
 * Focus request handled by the TV app.
 * [region] null means "the primary region": the active region with the highest priority
 * (most recently registered among equals).
 * Without [force] the request is ignored while something already holds the focus.
 */
class TvFocusRequest(
    val region: TvFocusRegion?,
    val force: Boolean,
)

object TvFocusRegions {
    private val activeRegions = mutableStateListOf<TvFocusRegion>()
    private val requestFlow = MutableSharedFlow<TvFocusRequest>(extraBufferCapacity = 8)

    /** Active regions in registration order. */
    val regions: List<TvFocusRegion>
        get() = activeRegions

    /** Region receiving null (primary) requests. */
    val primary: TvFocusRegion?
        get() = activeRegions.lastOrNull { region -> region.priority == activeRegions.maxOf { it.priority } }

    /** Navigation chrome of the TV shell (sidebar): target of [requestChrome], never primary. */
    var chrome: TvFocusRegion? = null

    /** Moves the focus to the navigation chrome (e.g. Back from the first level of a page). */
    fun requestChrome() {
        chrome?.let { request(it, force = true) }
    }

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
 * tab…) the focus moves to the first element of the area. With [focusOnActivation] the area also
 * takes the focus when its screen becomes visible; otherwise only real key changes move it.
 * No-op without a [TvShell].
 */
@Composable
fun Modifier.tvFocusRegion(
    key: Any?,
    region: TvFocusRegion = rememberTvFocusRegion(),
    priority: Int = 0,
    focusOnActivation: Boolean = true,
): Modifier {
    if (LocalTvShell.current == null) return this
    val active = LocalScreenActive.current
    region.priority = priority
    DisposableEffect(region, active) {
        if (active) TvFocusRegions.register(region)
        onDispose { TvFocusRegions.unregister(region) }
    }
    LaunchedEffect(region, key, active) {
        if (!active) return@LaunchedEffect
        val keyChanged = region.lastKey !== TvFocusRegion.NoKey && region.lastKey != key
        region.lastKey = key
        if (focusOnActivation || keyChanged) TvFocusRegions.request(region, force = true)
    }
    return this
        .onFocusChanged { state -> region.hasFocus = state.hasFocus }
        .focusRequester(region.requester)
        .focusGroup()
}

/**
 * Marks the navigation chrome of the TV shell (sidebar) as the target of
 * [TvFocusRegions.requestChrome]. It never takes the focus on its own. No-op without a [TvShell].
 */
@Composable
fun Modifier.tvChromeRegion(): Modifier {
    if (LocalTvShell.current == null) return this
    val region = rememberTvFocusRegion()
    DisposableEffect(region) {
        TvFocusRegions.chrome = region
        onDispose { if (TvFocusRegions.chrome === region) TvFocusRegions.chrome = null }
    }
    return this
        .onFocusChanged { state -> region.hasFocus = state.hasFocus }
        .focusRequester(region.requester)
        .focusGroup()
}

/**
 * Entering this focus group (D-pad from outside, or a region request) lands on [target] instead
 * of the first element, e.g. the selected item of a menu. Must precede the group it applies to.
 * No-op without a [TvShell].
 */
@Composable
fun Modifier.tvEnterOn(target: FocusRequester): Modifier {
    if (LocalTvShell.current == null) return this
    return focusProperties {
        onEnter = { runCatching { target.requestFocus() } }
    }
}

/**
 * Back on a two-level TV page (menu on the left, content on the right): from the content root it
 * moves the focus back to the menu, from the menu to the navigation sidebar. Deeper content pages
 * keep their own Back (parent page). No-op without a [TvShell].
 */
@Composable
internal fun TvMenuBackHandler(menu: TvFocusRegion, content: TvFocusRegion, contentAtRoot: Boolean) {
    if (LocalTvShell.current == null) return
    val active = LocalScreenActive.current
    PlatformBackHandler(
        enabled = active && (menu.hasFocus || (content.hasFocus && contentAtRoot)),
        onBack = {
            if (menu.hasFocus) {
                TvFocusRegions.requestChrome()
            } else {
                TvFocusRegions.request(menu, force = true)
            }
        },
    )
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
