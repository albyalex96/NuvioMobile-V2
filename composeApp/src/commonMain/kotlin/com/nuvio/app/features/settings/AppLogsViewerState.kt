package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.nuvio.app.core.logging.AppLogEntry
import com.nuvio.app.core.logging.AppLogFilter
import com.nuvio.app.core.logging.AppLogLevel
import com.nuvio.app.core.logging.AppLogStore
import com.nuvio.app.core.logging.logCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** What the viewer shows: the filtered entries (newest first) out of [totalCount] in the source list. */
internal data class AppLogsViewResult(
    val entries: List<AppLogEntry> = emptyList(),
    val filteredInOrder: List<AppLogEntry> = emptyList(),
    val totalCount: Int = 0,
    val categories: List<Pair<String, Int>> = emptyList(),
)

/**
 * State of the "App logs" page. The filters live in memory only (never synced or persisted).
 * With [live] off the source list is frozen, so the entries don't move while reading.
 */
@Stable
internal class AppLogsViewerState {
    var filter by mutableStateOf(AppLogFilter())
    var live by mutableStateOf(true)
    var result by mutableStateOf(AppLogsViewResult())
        internal set
    var expandedIds by mutableStateOf(emptySet<Long>())
        private set

    /** Bumped on "clear", so a paused viewer drops its frozen list too. */
    internal var clearGeneration by mutableIntStateOf(0)
        private set

    fun toggleLevel(level: AppLogLevel) {
        val levels = filter.levels
        filter = filter.copy(levels = if (level in levels) levels - level else levels + level)
    }

    fun toggleExpanded(id: Long) {
        expandedIds = if (id in expandedIds) expandedIds - id else expandedIds + id
    }

    fun clear() {
        AppLogStore.clear()
        expandedIds = emptySet()
        clearGeneration++
    }
}

private data class AppLogsViewerInputs(
    val filter: AppLogFilter,
    val live: Boolean,
    val clearGeneration: Int,
)

@Composable
internal fun rememberAppLogsViewerState(): AppLogsViewerState {
    val state = remember { AppLogsViewerState() }
    LaunchedEffect(state) {
        var frozen: List<AppLogEntry>? = null
        var seenClearGeneration = state.clearGeneration
        val inputs = snapshotFlow { AppLogsViewerInputs(state.filter, state.live, state.clearGeneration) }
        combine(AppLogStore.entries, inputs) { entries, input ->
            if (input.clearGeneration != seenClearGeneration) {
                seenClearGeneration = input.clearGeneration
                frozen = if (input.live) null else emptyList()
            }
            val source = if (input.live) {
                frozen = null
                entries
            } else {
                frozen ?: entries.also { frozen = it }
            }
            val filtered = source.filter(input.filter::matches)
            AppLogsViewResult(
                entries = filtered.asReversed(),
                filteredInOrder = filtered,
                totalCount = source.size,
                categories = source.logCategories(),
            )
        }
            .flowOn(Dispatchers.Default)
            .collect { state.result = it }
    }
    return state
}
