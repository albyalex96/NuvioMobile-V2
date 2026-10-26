package com.nuvio.app.features.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.tv.LocalTvShell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private val log = Logger.withTag("SettingsSearchHighlight")

/** Delay before the page starts looking for the row, so the page transition can settle. */
private const val HighlightStartDelayMillis = 220L

/** Upper bound for the scroll that looks for a row not composed yet. */
private const val HighlightSearchTimeoutMillis = 4_000L

private const val HighlightPulseCount = 2
private const val HighlightPulseAlpha = 0.24f

/**
 * Row the settings search asked to show: after the tap on a result, the destination page scrolls to the
 * row whose title is [title] and plays a short highlight on it.
 */
internal class SettingsSearchHighlightRequest(
    val id: Long,
    val page: SettingsPage,
    val title: String,
) {
    internal val normalizedTitle: String = normalizeSettingsSearchText(title.trim())

    /** Set by the first matching row, so only one row reacts and the page stops scrolling. */
    internal var found: Boolean = false
}

internal object SettingsSearchHighlight {
    private val _request = MutableStateFlow<SettingsSearchHighlightRequest?>(null)
    val request: StateFlow<SettingsSearchHighlightRequest?> = _request.asStateFlow()
    private var nextId = 0L

    fun request(page: SettingsPage, title: String) {
        if (title.isBlank()) return
        log.d { "Highlight requested: page=${page.name} title=$title" }
        _request.value = SettingsSearchHighlightRequest(id = ++nextId, page = page, title = title)
    }

    fun consume(request: SettingsSearchHighlightRequest) {
        if (_request.value?.id == request.id) _request.value = null
    }

    fun clear() {
        _request.value = null
    }
}

/** Page shown by the enclosing settings list; rows outside a [SettingsSearchHighlightHost] never highlight. */
private val LocalSettingsSearchHighlightPage = compositionLocalOf<SettingsPage?> { null }

/**
 * Wraps a settings page list: while a highlight request targets [page], the list scrolls down until the
 * requested row is composed (the row then brings itself into view). If the row is never found, the
 * request is dropped and the list goes back to the top.
 */
@Composable
internal fun SettingsSearchHighlightHost(
    page: SettingsPage,
    listState: LazyListState,
    content: @Composable () -> Unit,
) {
    val request by SettingsSearchHighlight.request.collectAsState()
    val active = request?.takeIf { it.page == page }
    LaunchedEffect(active?.id) {
        val current = active ?: return@LaunchedEffect
        delay(HighlightStartDelayMillis)
        var elapsed = HighlightStartDelayMillis
        while (!current.found && listState.canScrollForward && elapsed < HighlightSearchTimeoutMillis) {
            val step = listState.layoutInfo.viewportSize.height * 0.8f
            if (step <= 0f) break
            try {
                listState.animateScrollBy(
                    value = step,
                    animationSpec = tween(durationMillis = NuvioTokens.Motion.normalMillis, easing = LinearOutSlowInEasing),
                )
            } catch (error: CancellationException) {
                // The matched row interrupts this scroll with its own bring-into-view.
                ensureActive()
            }
            elapsed += NuvioTokens.Motion.normalMillis.toLong()
        }
        if (!current.found) {
            log.w { "Row not found on page=${page.name} for title=${current.title}" }
            SettingsSearchHighlight.consume(current)
            listState.animateScrollToItem(0)
        }
    }
    CompositionLocalProvider(LocalSettingsSearchHighlightPage provides page, content = content)
}

/**
 * Lets the settings search focus this row: when a highlight request on the current page matches [title]
 * the row is brought into view (with some margin, so sticky headers and bottom bars don't cover it),
 * focused on TV and briefly pulsed with the accent color. Apply it before `clickable` so focus lands on
 * the row's own focus target.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.settingsSearchHighlight(title: String): Modifier = composed {
    val page = LocalSettingsSearchHighlightPage.current ?: return@composed this
    val request by SettingsSearchHighlight.request.collectAsState()
    val matching = request?.takeIf {
        it.page == page && it.normalizedTitle == normalizeSettingsSearchText(title.trim())
    }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val focusRequester = remember { FocusRequester() }
    val pulse = remember { Animatable(0f) }
    val size = remember { arrayOf(IntSize.Zero) }
    val marginPx = with(LocalDensity.current) { 112.dp.toPx() }
    val isTv = LocalTvShell.current != null
    val accent = MaterialTheme.nuvio.colors.accent

    LaunchedEffect(matching?.id) {
        val current = matching ?: return@LaunchedEffect
        if (current.found) return@LaunchedEffect
        current.found = true
        log.i { "Highlighting row: page=${page.name} title=${current.title}" }
        try {
            val rowSize = size[0]
            bringIntoViewRequester.bringIntoView(
                if (rowSize == IntSize.Zero) {
                    null
                } else {
                    Rect(0f, -marginPx, rowSize.width.toFloat(), rowSize.height + marginPx)
                },
            )
            if (isTv) runCatching { focusRequester.requestFocus() }
            delay(NuvioTokens.Motion.fastMillis.toLong())
            repeat(HighlightPulseCount) {
                pulse.animateTo(1f, tween(durationMillis = NuvioTokens.Motion.normalMillis, easing = FastOutSlowInEasing))
                pulse.animateTo(0f, tween(durationMillis = NuvioTokens.Motion.slowMillis, easing = FastOutSlowInEasing))
            }
        } finally {
            withContext(NonCancellable) { pulse.snapTo(0f) }
            SettingsSearchHighlight.consume(current)
        }
    }

    this
        .onSizeChanged { size[0] = it }
        .bringIntoViewRequester(bringIntoViewRequester)
        .drawBehind {
            val alpha = pulse.value
            if (alpha > 0f) drawRect(color = accent.copy(alpha = HighlightPulseAlpha * alpha))
        }
        .focusRequester(focusRequester)
}
