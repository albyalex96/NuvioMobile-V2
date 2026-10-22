package com.nuvio.app.features.search

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.hasPendingEnabledManifests
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.settings.normalizeSettingsSearchText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val ADDONS_READY_TIMEOUT_MS = 10_000L
private const val SEARCH_RESULTS_TIMEOUT_MS = 15_000L
private const val SCREEN_HANDOFF_TIMEOUT_MS = 3_000L

/**
 * Searches started from outside the app (Android TV voice search, Google Assistant).
 * The search screen takes the pending query and fills its field with it; [awaitExactMatch]
 * then tells whether a single title matches the query exactly, so it can be opened directly.
 */
object ExternalSearchLauncher {
    private val log = Logger.withTag("ExternalSearch")
    private val _pendingQuery = MutableStateFlow<String?>(null)
    val pendingQuery: StateFlow<String?> = _pendingQuery.asStateFlow()

    fun request(query: String) {
        log.i { "External search requested (${query.length} chars)" }
        _pendingQuery.value = query
    }

    /** Called by the search screen once the query is in its field. */
    fun consume(query: String) {
        _pendingQuery.compareAndSet(query, null)
    }

    /**
     * Waits for the results of [query] and returns the only title whose name is [query].
     * Returns null when nothing or more than one title matches (e.g. a film and a series with
     * the same name, or two remakes): the user then chooses from the results.
     */
    suspend fun awaitExactMatch(query: String): MetaPreview? {
        // The screen applies the query first: searching before it could be undone by the
        // screen clearing its (still empty) field.
        withTimeoutOrNull(SCREEN_HANDOFF_TIMEOUT_MS) { pendingQuery.first { it != query } }
        AddonRepository.initialize()
        val addons = withTimeoutOrNull(ADDONS_READY_TIMEOUT_MS) {
            AddonRepository.uiState.first { !it.addons.hasPendingEnabledManifests() }
        }?.addons ?: AddonRepository.uiState.value.addons
        // Same request the screen makes: the repository reuses it instead of searching twice.
        SearchRepository.search(query = query, addons = addons)
        val state = withTimeoutOrNull(SEARCH_RESULTS_TIMEOUT_MS) {
            SearchRepository.uiState.first { !it.isLoading }
        } ?: SearchRepository.uiState.value.also {
            log.w { "Search results still loading after ${SEARCH_RESULTS_TIMEOUT_MS}ms: matching the partial results" }
        }
        val match = findExactSearchMatch(query, state.sections)
        if (match != null) {
            log.i { "Exact match found (${match.type}:${match.id}): opening details" }
        } else {
            log.i { "No single exact match among ${state.sections.sumOf { it.items.size }} results: showing results" }
        }
        return match
    }
}

internal fun findExactSearchMatch(query: String, sections: List<HomeCatalogSection>): MetaPreview? {
    val target = normalizeSearchMatchText(query)
    if (target.isEmpty()) return null
    val matches = sections
        .flatMap { it.items }
        .filter { normalizeSearchMatchText(it.name) == target }
        .distinctBy { it.stableKey() }
    if (matches.isEmpty()) return null
    if (matches.distinctBy { it.type }.size > 1) return null
    // The same title from several addons has different ids but the same year.
    if (matches.mapNotNull { it.releaseYear() }.distinct().size > 1) return null
    return matches.first()
}

internal fun normalizeSearchMatchText(text: String): String =
    normalizeSettingsSearchText(text)
        .replace('&', ' ')
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .replace(Regex("""\band\b|\be\b"""), " ")
        .trim()
        .replace(Regex("""\s+"""), " ")

private fun MetaPreview.releaseYear(): String? =
    (releaseInfo ?: rawReleaseDate)?.let { Regex("""\d{4}""").find(it)?.value }
