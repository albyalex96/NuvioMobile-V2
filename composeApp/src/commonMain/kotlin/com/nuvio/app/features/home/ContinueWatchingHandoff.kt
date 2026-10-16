package com.nuvio.app.features.home

import androidx.compose.runtime.mutableStateMapOf
import co.touchlab.kermit.Logger
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps a continue-watching card in place while its series moves on to the next episode (e.g.
 * after "mark as watched"): the old card stays at its position until the row has a new card for
 * the same series, so the row does not flicker while the next-up item is being resolved.
 */
object ContinueWatchingHandoff {
    private const val HOLD_TIMEOUT_MS = 6_000L
    private val log = Logger.withTag("ContinueWatchingHandoff")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private class Hold(val item: ContinueWatchingItem, val index: Int, val token: Any)

    private val holds = mutableStateMapOf<String, Hold>()

    /** Last row passed to [applyTo] (plain field, not state): gives the position of a held card. */
    private var lastItems: List<ContinueWatchingItem> = emptyList()

    /** Holds [item] at its current position until its replacement appears or a timeout. */
    fun hold(item: ContinueWatchingItem) {
        val seriesId = item.parentMetaId.ifBlank { return }
        val index = lastItems.indexOfFirst { it.parentMetaId == seriesId }.coerceAtLeast(0)
        val token = Any()
        holds[seriesId] = Hold(item, index, token)
        log.d { "Holding card of $seriesId at $index" }
        scope.launch {
            delay(HOLD_TIMEOUT_MS)
            if (holds[seriesId]?.token === token) {
                holds.remove(seriesId)
                log.d { "Hold of $seriesId expired" }
            }
        }
    }

    /** Ends the hold of [item]'s series (no next episode is coming). */
    fun release(item: ContinueWatchingItem) {
        if (holds.remove(item.parentMetaId) != null) log.d { "Released card of ${item.parentMetaId}" }
    }

    /**
     * Inserts the held cards whose series has no card in [items]. Reads snapshot state: the caller
     * recomposes when a hold starts or ends.
     */
    fun applyTo(items: List<ContinueWatchingItem>): List<ContinueWatchingItem> {
        lastItems = items
        if (holds.isEmpty()) return items
        val presentSeries = items.mapTo(hashSetOf()) { it.parentMetaId }
        val missing = holds.entries
            .filter { (seriesId, _) -> seriesId !in presentSeries }
            .sortedBy { (_, hold) -> hold.index }
        if (missing.isEmpty()) return items
        val result = items.toMutableList()
        lastItems = result
        missing.forEach { (_, hold) -> result.add(hold.index.coerceAtMost(result.size), hold.item) }
        return result
    }
}
